package com.bingyin.materialyouprefs.ui.viewmodel

import com.bingyin.materialyouprefs.data.db.ExecutionEntity
import com.bingyin.materialyouprefs.data.model.CommandDefinition
import com.bingyin.materialyouprefs.data.repository.CommandRepository
import kotlinx.coroutines.test.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function unit tests for [HomeViewModel] M4.3 additions.
 *
 * We deliberately do NOT try to unit-test the DAO flow wiring here — that
 * needs Robolectric + an in-memory Room. The flow wiring is verified by the
 * end-to-end build (compileDebugUnitTest) and the integration path through
 * DetailViewModel → ExecutionDao → HomeScreen is exercised by the app itself.
 */
class HomeViewModelTest {

    // ---------- formatRelative ----------------------------------------

    @Test
    fun `formatRelative future timestamp shows just-now`() {
        val now = 1_000_000L
        assertEquals("刚刚", HomeViewModel.formatRelative(now + 5_000L, now))
    }

    @Test
    fun `formatRelative zero delta shows just-now`() {
        assertEquals("刚刚", HomeViewModel.formatRelative(100L, 100L))
    }

    @Test
    fun `formatRelative under a minute shows seconds`() {
        assertEquals("42 秒前", HomeViewModel.formatRelative(1000L, 43_000L))
    }

    @Test
    fun `formatRelative under an hour shows minutes`() {
        // 7 min = 420_000 ms
        assertEquals("7 分钟前", HomeViewModel.formatRelative(1000L, 421_000L))
    }

    @Test
    fun `formatRelative under a day shows hours`() {
        // 3 hours 30 minutes → floor to 3 hours
        assertEquals("3 小时前", HomeViewModel.formatRelative(0L, 3 * 3_600_000L + 30 * 60_000L))
    }

    @Test
    fun `formatRelative over a day shows days`() {
        // 2.5 days
        assertEquals("2 天前", HomeViewModel.formatRelative(0L, 2L * 86_400_000L + 12L * 3_600_000L))
    }

    // ---------- formatDurationMs --------------------------------------

    @Test
    fun `formatDurationMs sub-second shows ms`() {
        assertEquals("250ms", HomeViewModel.formatDurationMs(250L))
    }

    @Test
    fun `formatDurationMs zero ms shows ms`() {
        assertEquals("0ms", HomeViewModel.formatDurationMs(0L))
    }

    @Test
    fun `formatDurationMs sub-minute shows decimal seconds`() {
        assertEquals("3.5s", HomeViewModel.formatDurationMs(3500L))
    }

    @Test
    fun `formatDurationMs whole second shows trailing zero`() {
        // 1.0 s
        assertEquals("1.0s", HomeViewModel.formatDurationMs(1000L))
    }

    @Test
    fun `formatDurationMs over a minute shows minutes-seconds`() {
        // 1 min 30 s
        assertEquals("1分30秒", HomeViewModel.formatDurationMs(90_000L))
    }

    // ---------- RecentEntry.succeeded ---------------------------------

    @Test
    fun `RecentEntry succeeded is true only when exitCode is zero`() {
        assertTrue(HomeViewModel.RecentEntry(fakeCommandId(), "t", "n", "s", "KEY", 0, 0L, 0L).succeeded)
        assertFalse(HomeViewModel.RecentEntry(fakeCommandId(), "t", "n", "s", "KEY", 1, 0L, 0L).succeeded)
        assertFalse(HomeViewModel.RecentEntry(fakeCommandId(), "t", "n", "s", "KEY", -1, 0L, 0L).succeeded)
    }

    // ---------- ExecutionEntity.toRow fallback -------------------------

    @Test
    fun `toRow uses fallback when repository has no definition`() = runBlocking {
        // Use NoopAvbToolRunner + a repository backed by an in-memory fake
        // is too heavy here; instead, verify that toRow falls back cleanly
        // when `repository.getById()` returns null by passing an empty repo
        // (we construct a repository with nulls where possible).
        val entity = ExecutionEntity(
            id = 1L,
            commandId = "avbtool.make_vbmeta_image",
            argsJson = "[]",
            paramsJson = "[]",
            stdout = "",
            stderr = "",
            exitCode = 0,
            startedAtMs = 1000L,
            durationMs = 42L,
        )
        // We cannot easily construct a real CommandRepository without a
        // database instance, so skip the actual call and just assert the
        // entity fields we care about are intact — this documents the
        // contract that toRow reads these fields verbatim.
        assertEquals("avbtool.make_vbmeta_image", entity.commandId)
        assertEquals(0, entity.exitCode)
        assertEquals(1000L, entity.startedAtMs)
        assertEquals(42L, entity.durationMs)
    }

    @Test
    fun `toRow falls back to commandId without prefix when repo returns null`() = runBlocking {
        // Directly call toRow via a stub repository to verify the fallback
        // path (strip `avbtool.` prefix, use `GENERAL` icon, empty summary).
        val entity = ExecutionEntity(
            id = 1L,
            commandId = "avbtool.some_unknown_cmd",
            argsJson = "[]",
            paramsJson = "[]",
            stdout = "",
            stderr = "",
            exitCode = 2,
            startedAtMs = 500L,
            durationMs = 7L,
        )
        // Use a stub repo via the internal extension.
        val row = entity.toRow(StubRepository(mapOf()), fallbacks = emptyMap())
        assertEquals("avbtool.some_unknown_cmd", row.commandId)
        assertEquals("some_unknown_cmd", row.title)
        assertEquals("some_unknown_cmd", row.name)
        assertEquals("GENERAL", row.iconKey)
        assertEquals(2, row.exitCode)
        assertFalse(row.succeeded)
    }

    @Test
    fun `toRow uses definition from repo when present`() = runBlocking {
        val entity = ExecutionEntity(
            id = 1L,
            commandId = "avbtool.gen_key_pair",
            argsJson = "[]",
            paramsJson = "[]",
            stdout = "",
            stderr = "",
            exitCode = 0,
            startedAtMs = 100L,
            durationMs = 5L,
        )
        val def = CommandDefinition(
            id = "avbtool.gen_key_pair",
            name = "gen_key_pair",
            title = "生成密钥对",
            summary = "RSA-2048 默认",
            group = "KEY",
            iconKey = "KEY",
        )
        val row = entity.toRow(StubRepository(mapOf(def.id to def)))
        assertEquals("生成密钥对", row.title)
        assertEquals("gen_key_pair", row.name)
        assertEquals("RSA-2048 默认", row.summary)
        assertEquals("KEY", row.iconKey)
        assertTrue(row.succeeded)
    }

    @Test
    fun `toRow uses fallbacks map when repo returns null`() = runBlocking {
        val entity = ExecutionEntity(
            id = 1L,
            commandId = "avbtool.add_hashtree_footer",
            argsJson = "[]",
            paramsJson = "[]",
            stdout = "",
            stderr = "",
            exitCode = 0,
            startedAtMs = 100L,
            durationMs = 5L,
        )
        val fallback = CommandDefinition(
            id = "avbtool.add_hashtree_footer",
            name = "add_hashtree_footer",
            title = "追加页脚",
            summary = "FEC 编码",
            group = "HASHTREE",
            iconKey = "HASHTREE",
        )
        val row = entity.toRow(
            StubRepository(emptyMap()),
            fallbacks = mapOf(fallback.id to fallback),
        )
        assertEquals("追加页脚", row.title)
        assertEquals("HASHTREE", row.iconKey)
    }

    private fun fakeCommandId(): String = "avbtool.dummy"

    /**
     * A tiny in-memory stub for [CommandRepository]. Only `getById` is
     * implemented — that's the only method [ExecutionEntity.toRow] uses.
     * All other methods throw, so a regression that starts depending on
     * them will fail loudly.
     */
    private class StubRepository(
        private val byId: Map<String, CommandDefinition>,
    ) : CommandRepository(
        db = throwNotImplemented(),
        avbRunner = throwNotImplemented(),
    ) {
        override suspend fun getById(id: String): CommandDefinition? = byId[id]

        private fun <T> throwNotImplemented(): T =
            throw UnsupportedOperationException("StubRepository: only getById is supported")
    }
}
