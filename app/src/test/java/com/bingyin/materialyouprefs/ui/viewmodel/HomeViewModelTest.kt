package com.bingyin.materialyouprefs.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function unit tests for [HomeViewModel] M4.3 additions.
 *
 * We deliberately do NOT try to unit-test the DAO flow wiring here —
 * that needs Robolectric + an in-memory Room. The flow wiring is
 * verified by the end-to-end build (compileDebugUnitTest) and the
 * integration path through DetailViewModel → ExecutionDao → HomeScreen
 * is exercised by the app itself.
 *
 * The two extracted pure helpers ([formatRelative] and
 * [formatDurationMs]) are trivially testable and cover the user-visible
 * time formatting contract.
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
        assertEquals("3 小时前", HomeViewModel.formatRelative(0L, 3L * 3_600_000L + 30L * 60_000L))
    }

    @Test
    fun `formatRelative over a day shows days`() {
        // 2.5 days
        assertEquals("2 天前", HomeViewModel.formatRelative(0L, 2L * 86_400_000L + 12L * 3_600_000L))
    }

    @Test
    fun `formatRelative uses provided nowMs instead of System clock`() {
        // Deterministic: same timestampMs produces the same string.
        val a = HomeViewModel.formatRelative(0L, 120_000L)
        val b = HomeViewModel.formatRelative(0L, 120_000L)
        assertEquals(a, b)
        assertEquals("2 分钟前", a)
    }

    // ---------- formatDurationMs --------------------------------------

    @Test
    fun `formatDurationMs zero ms shows ms`() {
        assertEquals("0ms", HomeViewModel.formatDurationMs(0L))
    }

    @Test
    fun `formatDurationMs sub-second shows ms`() {
        assertEquals("250ms", HomeViewModel.formatDurationMs(250L))
    }

    @Test
    fun `formatDurationMs just under a second stays in ms`() {
        assertEquals("999ms", HomeViewModel.formatDurationMs(999L))
    }

    @Test
    fun `formatDurationMs whole second shows trailing zero decimal`() {
        assertEquals("1.0s", HomeViewModel.formatDurationMs(1000L))
    }

    @Test
    fun `formatDurationMs sub-minute shows decimal seconds`() {
        assertEquals("3.5s", HomeViewModel.formatDurationMs(3500L))
    }

    @Test
    fun `formatDurationMs 45 seconds shows correctly`() {
        assertEquals("45.0s", HomeViewModel.formatDurationMs(45_000L))
    }

    @Test
    fun `formatDurationMs just over a minute shows minutes-seconds`() {
        // 1 min 5 s
        assertEquals("1分5秒", HomeViewModel.formatDurationMs(65_000L))
    }

    @Test
    fun `formatDurationMs 2 minutes 30 seconds shows correctly`() {
        assertEquals("2分30秒", HomeViewModel.formatDurationMs(150_000L))
    }

    // ---------- RecentEntry.succeeded ---------------------------------

    @Test
    fun `RecentEntry succeeded is true only when exitCode is zero`() {
        assertTrue(HomeViewModel.RecentEntry("id", "t", "n", "s", "KEY", 0, 0L, 0L).succeeded)
        assertFalse(HomeViewModel.RecentEntry("id", "t", "n", "s", "KEY", 1, 0L, 0L).succeeded)
        assertFalse(HomeViewModel.RecentEntry("id", "t", "n", "s", "KEY", -1, 0L, 0L).succeeded)
        assertFalse(HomeViewModel.RecentEntry("id", "t", "n", "s", "KEY", 127, 0L, 0L).succeeded)
    }

    @Test
    fun `RecentEntry equality captures all UI-visible fields`() {
        val a = HomeViewModel.RecentEntry("id", "t", "n", "s", "KEY", 0, 100L, 42L)
        val b = HomeViewModel.RecentEntry("id", "t", "n", "s", "KEY", 0, 100L, 42L)
        val c = HomeViewModel.RecentEntry("id", "t", "n", "s", "KEY", 1, 100L, 42L)
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `RECOMMENDED_IDS contains five canonical avbtool commands`() {
        val ids = HomeViewModel.RECOMMENDED_IDS
        assertEquals(5, ids.size)
        assertTrue(ids.contains("avbtool.gen_key_pair"))
        assertTrue(ids.contains("avbtool.make_vbmeta_image"))
        assertTrue(ids.contains("avbtool.add_hashtree_footer"))
        assertTrue(ids.contains("avbtool.verify_image"))
        assertTrue(ids.contains("avbtool.info_image"))
    }

    @Test
    fun `RECENT_LIMIT is at most five`() {
        // Cap on the execution_history rows surfaced on the Home card.
        assertTrue(HomeViewModel.RECENT_LIMIT in 1..5)
    }
}
