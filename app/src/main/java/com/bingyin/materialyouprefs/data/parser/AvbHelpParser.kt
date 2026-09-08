package com.bingyin.materialyouprefs.data.parser

import com.bingyin.materialyouprefs.data.model.CommandParam
import com.bingyin.materialyouprefs.data.model.ParamType

/**
 * Parses the output of `avbtool <subcommand> --help` into a list of [CommandParam].
 *
 * Input format (argparse default, verified against avbtool.py at AOSP head 386fb904):
 * ```
 * usage: avbtool.py subcmd [-h] [--flag VALUE] ... positional
 *
 * Short description (from `help=` on add_parser).
 *
 * options:
 *   -h, --help            show this help message and exit
 *   --flag VALUE          Short description on same line
 *   --flag VALUE
 *                       Long description that wrapped to next line
 *   --flag VALUE {a,b,c}
 *                       Choice values in braces
 *   --toggle_flag
 *                       Boolean flag without placeholder
 *
 * positional arguments:
 *   image                 Description of positional arg
 * ```
 *
 * Parsing rules:
 *  - Lines before `options:` / `positional arguments:` are ignored.
 *  - An option block starts at column 2 with `-` / `--`; continuation lines
 *    start at column 4+ with no leading `-`.
 *  - `-h, --help` is skipped.
 *  - `{a,b,c}` in the opt block → [ParamType.CHOICE] with choices split on `,`.
 *  - No placeholder → [ParamType.BOOLEAN].
 *  - Placeholder matches a PATH-like name (KEY, IMAGE, FILE, ...) → [ParamType.PATH].
 *  - Placeholder contains SIZE/COUNT/INDEX/NUM/FLAGS/OFFSET → [ParamType.INT].
 *  - Otherwise → [ParamType.STRING].
 *  - Positional arguments are all required STRING.
 *  - Description containing `(required)` marks the option as required.
 *  - Description containing `(default: X)` sets [CommandParam.default].
 *
 * Robustness: empty input returns an empty list; malformed lines are skipped
 * silently so the caller always gets a best-effort result rather than throwing.
 */
object AvbHelpParser {

    fun parse(help: String): List<CommandParam> =
        if (help.isBlank()) emptyList()
        else parseBlock(help)

    private fun parseBlock(help: String): List<CommandParam> {
        val result = mutableListOf<CommandParam>()
        val lines = help.lines()
        var mode: Mode = Mode.OUTSIDE
        var pending: PendingEntry? = null

        for (raw in lines) {
            val line = raw.trimEnd('\r', ' ')
            when {
                line.trim().equals("options:", ignoreCase = true) -> {
                    flush(pending, result); pending = null
                    mode = Mode.OPTIONS
                }
                line.trim().equals("positional arguments:", ignoreCase = true) -> {
                    flush(pending, result); pending = null
                    mode = Mode.POSITIONAL
                }
                else -> {
                    when (mode) {
                        Mode.OPTIONS -> handleOptionLine(line, pending, result).also { pending = it }
                        Mode.POSITIONAL -> handlePositionalLine(line, pending, result).also { pending = it }
                        Mode.OUTSIDE -> Unit
                    }
                }
            }
        }
        flush(pending, result)
        return result
    }

    private enum class Mode { OUTSIDE, OPTIONS, POSITIONAL }

    private class PendingEntry(val optBlock: String, var description: String = "")

    private fun handleOptionLine(
        line: String,
        pending: PendingEntry?,
        result: MutableList<CommandParam>,
    ): PendingEntry? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return pending
        // Continuation line: starts at deeper indent, does not begin with `-`.
        if (line.startsWith("    ") && !trimmed.startsWith("-")) {
            if (pending != null) {
                pending.appendDesc(trimmed)
            }
            return pending
        }
        // New option line: starts at column 2 with `-`.
        if (trimmed.startsWith("-")) {
            flush(pending, result)
            val (block, desc) = splitBlockAndDesc(trimmed)
            if (isHelpOnly(block)) return null
            return PendingEntry(block).also { it.appendDesc(desc) }
        }
        // Anything else (blank, weird) — flush current and continue.
        flush(pending, result)
        return null
    }

    private fun handlePositionalLine(
        line: String,
        pending: PendingEntry?,
        result: MutableList<CommandParam>,
    ): PendingEntry? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return pending
        if (line.startsWith("    ") && !trimmed.startsWith("-") && pending != null) {
            pending.appendDesc(trimmed)
            return pending
        }
        flush(pending, result)
        if (trimmed.startsWith("-")) return null
        val (block, desc) = splitBlockAndDesc(trimmed)
        return PendingEntry(block).also { it.appendDesc(desc) }
    }

    private fun splitBlockAndDesc(content: String): Pair<String, String> {
        // argparse splits opt-block and description on 2+ whitespace.
        val m = Regex("^(\\S(?:\\s+\\S)*?)\\s{2,}(.*)$").find(content)
        return if (m != null) m.groupValues[1] to m.groupValues[2].trim()
        else content to ""
    }

    private fun isHelpOnly(block: String): Boolean {
        val opts = block.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        return opts.all { it == "-h" || it == "--help" }
    }

    private fun flush(entry: PendingEntry?, out: MutableList<CommandParam>) {
        if (entry == null) return
        val param = entry.toParam()
        if (param != null) out += param
    }

    private fun PendingEntry.appendDesc(extra: String) {
        if (extra.isEmpty()) return
        description = if (description.isEmpty()) extra else description + " " + extra
    }

    private fun PendingEntry.toParam(): CommandParam? {
        val opts = optBlock.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        val longOpt = opts.firstOrNull { it.startsWith("--") } ?: return null
        val shortAlias = opts.firstOrNull { it.startsWith("-") && !it.startsWith("--") }
        val placeholders = opts.filter {
            it != longOpt && it != shortAlias && !it.startsWith("-") && isPlaceholder(it)
        }
        val choices = extractChoices(optBlock)
        val type = inferType(longOpt, placeholders, choices)
        val required = description.contains("required", ignoreCase = true)
        val default = extractDefault(description)
        return CommandParam(
            name = longOpt,
            description = description.trim(),
            type = type,
            required = required,
            choices = choices,
            default = default,
        )
    }

    private fun isPlaceholder(s: String): Boolean {
        if (s.contains("{") && s.contains("}")) return true
        if (s.contains("/") && !s.contains(",")) return true // nargs='*'
        return s == s.uppercase() && s.length >= 1 && s.all { it.isLetterOrDigit() || it == '_' }
    }

    private fun extractChoices(block: String): List<String> {
        val m = Regex("\\{([^}]+)\\}").find(block)
        if (m == null) return emptyList()
        return m.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun extractDefault(description: String): String? {
        val m = Regex("\\(default:\\s*([^)]+)\\)", RegexOption.IGNORE_CASE).find(description)
        return m?.groupValues?.get(1)?.trim()
    }

    private fun inferType(longOpt: String, placeholders: List<String>, choices: List<String>): ParamType {
        if (choices.isNotEmpty()) return ParamType.CHOICE
        if (placeholders.isEmpty()) return ParamType.BOOLEAN
        val ph = placeholders.first()
        if (ph.contains("{")) return ParamType.CHOICE
        return when {
            ph in PATH_PLACEHOLDERS -> ParamType.PATH
            ph in FILE_PLACEHOLDERS -> ParamType.FILE
            ph.contains("SIZE") || ph.contains("COUNT") || ph.contains("INDEX") ||
                ph.contains("NUM") || ph.contains("FLAGS") || ph.contains("OFFSET") ||
                ph.contains("BLOCK_SIZE") -> ParamType.INT
            else -> ParamType.STRING
        }
    }

    private val PATH_PLACEHOLDERS = setOf(
        "KEY", "KEY_FILE", "KEY_PATH", "OUT", "OUT_FILE", "IMAGE", "IMAGE_FILE",
        "VBMETA", "VBMETA_IMAGE", "FILE", "OUTPUT_FILE", "SIGNING_HELPER_WITH_FILES",
    )
    private val FILE_PLACEHOLDERS = setOf(
        "PROP_FROM_FILE_FILE", "CERT_FILE",
    )
}
