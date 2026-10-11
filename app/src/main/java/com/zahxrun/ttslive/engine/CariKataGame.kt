package com.zahxrun.ttslive.engine

import android.content.Context
import org.json.JSONArray
import kotlin.random.Random

data class CariKataCell(val row: Int, val col: Int)

data class CariKataWord(
    val id: Int,
    val length: Int,
    val found: Boolean,
    /** null sampai ketemu, supaya jawaban nggak bocor ke daftar kata. */
    val word: String?,
    /** Warna ARGB, dipakai untuk menandai kata di grid. */
    val color: Long,
    /** Sel di grid; hanya terisi kalau kata sudah ketemu. */
    val cells: List<CariKataCell>,
    val foundBy: String,
    val foundByEmoji: String,
)

data class CariKataState(
    val theme: String,
    val size: Int,
    val grid: List<List<Char>>,
    val words: List<CariKataWord>,
    val foundCount: Int,
    val totalCount: Int,
) : GameState

/**
 * Port dari server/games/cari-kata/index.js + grid-placement.js.
 * Satu tema per ronde; kata-kata tema ditaruh di grid huruf, penonton ngetik kata yang mereka temukan.
 * Poin = panjang kata. Ronde selesai kalau semua kata ketemu.
 */
class CariKataGame(
    context: Context,
    level: Int = 1,
    private val random: Random = Random.Default,
) : Game {

    override val id = "cari-kata"
    override val title = "Cari Kata"

    private class Cfg(
        val size: Int,
        val minWords: Int,
        val maxWords: Int,
        val minLen: Int,
        val maxLen: Int,
        val diagonals: Boolean,
    )

    private class Theme(val name: String, val words: List<String>)

    private class Placed(
        val id: Int,
        val word: String,
        val row: Int,
        val col: Int,
        val dr: Int,
        val dc: Int,
        val color: Long,
    ) {
        var found = false
        var foundBy = ""
        var foundByEmoji = ""

        fun cells(): List<CariKataCell> =
            (0 until word.length).map { CariKataCell(row + dr * it, col + dc * it) }
    }

    private class Placement(val grid: Array<CharArray>, val words: List<Placed>)

    private val cfg: Cfg = when (level) {
        2 -> Cfg(size = 10, minWords = 8, maxWords = 12, minLen = 3, maxLen = 8, diagonals = true)
        else -> Cfg(size = 8, minWords = 6, maxWords = 10, minLen = 3, maxLen = 5, diagonals = false)
    }

    private val themes: List<Theme> = loadBank(context)
    private val usedThemes = mutableSetOf<String>()

    private var currentTheme = ""
    private var grid: Array<CharArray> = Array(cfg.size) { CharArray(cfg.size) { 'A' } }
    private var placed: List<Placed> = emptyList()

    init { generateRound() }

    private fun loadBank(context: Context): List<Theme> {
        val json = context.assets.open("questions/cari_kata.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val wa = o.getJSONArray("words")
            Theme(o.getString("theme"), (0 until wa.length()).map { wa.getString(it) })
        }
    }

    private fun normalize(s: String): String = s.uppercase().filter { it in 'A'..'Z' }

    private fun wordsFor(t: Theme): List<String> =
        t.words.filter { it.length in cfg.minLen..minOf(cfg.maxLen, cfg.size) }

    private fun pickTheme(): Theme {
        check(themes.isNotEmpty()) { "Bank kata cari_kata.json kosong" }
        val eligible = themes.filter { wordsFor(it).size >= cfg.minWords }.ifEmpty { themes }
        var pool = eligible.filter { it.name !in usedThemes }
        if (pool.isEmpty()) {
            usedThemes.clear()
            pool = eligible
        }
        val t = pool.random(random)
        usedThemes.add(t.name)
        return t
    }

    private fun generateRound() {
        repeat(MAX_ROUND_TRIES) {
            val theme = pickTheme()
            val words = wordsFor(theme).distinct().shuffled(random).take(cfg.maxWords)
            val result = placeWithDrop(words)
            if (result != null) {
                currentTheme = theme.name
                grid = result.grid
                placed = result.words
                return
            }
        }
        // Gagal terus (nyaris mustahil): biarkan ronde lama supaya app nggak crash.
    }

    // ---------- Penempatan kata (port grid-placement.js) ----------

    private fun placeWithDrop(words: List<String>): Placement? {
        val candidates = words.toMutableList()
        while (candidates.size >= MIN_PLACED) {
            val result = placeAll(candidates)
            if (result != null) return result
            candidates.removeAt(random.nextInt(candidates.size))
        }
        return null
    }

    private fun placeAll(words: List<String>): Placement? {
        val size = cfg.size
        repeat(MAX_FULL_ATTEMPTS) {
            // Kata terpanjang ditaruh duluan (paling susah cari slot).
            val order = words.shuffled(random).sortedByDescending { it.length }
            val g = Array(size) { CharArray(size) { EMPTY } }
            val result = ArrayList<Placed>()
            var ok = true
            for (w in order) {
                val pos = tryPlace(g, w, result.size)
                if (pos == null) {
                    ok = false
                    break
                }
                val id = result.size
                result.add(Placed(id, w, pos[0], pos[1], pos[2], pos[3], COLORS[id % COLORS.size]))
            }
            if (ok) {
                fillRandomLetters(g)
                return Placement(g, result)
            }
        }
        return null
    }

    /** Return [row, col, dr, dc] kalau berhasil (huruf langsung ditulis ke grid), null kalau gagal. */
    private fun tryPlace(g: Array<CharArray>, word: String, placedCount: Int): IntArray? {
        val size = cfg.size
        val n = word.length
        if (n > size) return null
        val dirs = if (cfg.diagonals) DIRS_4 else DIRS_2

        for (attempt in 0 until MAX_ATTEMPTS_PER_WORD) {
            val (dr, dc) = dirs[random.nextInt(dirs.size)]
            val row = when (dr) {
                1 -> random.nextInt(size - n + 1)
                -1 -> (n - 1) + random.nextInt(size - n + 1)
                else -> random.nextInt(size)
            }
            val col = if (dc == 1) random.nextInt(size - n + 1) else random.nextInt(size)

            var fits = true
            var crossings = 0
            var newCells = 0
            for (i in 0 until n) {
                val existing = g[row + dr * i][col + dc * i]
                if (existing == EMPTY) {
                    newCells++
                } else if (existing == word[i]) {
                    crossings++
                } else {
                    fits = false
                    break
                }
            }
            if (!fits || newCells == 0) continue
            // Setelah kata pertama, utamakan yang bersilangan; separuh percobaan terakhir dilonggarkan
            // supaya kata nggak gampang gagal muat.
            val needCrossing = placedCount >= 1 && attempt < MAX_ATTEMPTS_PER_WORD / 2
            if (needCrossing && crossings == 0) continue

            for (i in 0 until n) g[row + dr * i][col + dc * i] = word[i]
            return intArrayOf(row, col, dr, dc)
        }
        return null
    }

    private fun fillRandomLetters(g: Array<CharArray>) {
        for (r in g.indices) {
            for (c in g[r].indices) {
                if (g[r][c] == EMPTY) g[r][c] = ALPHABET.random(random)
            }
        }
    }

    // ---------- Kontrak Game ----------

    override fun buildState(): GameState = CariKataState(
        theme = currentTheme,
        size = cfg.size,
        grid = grid.map { it.toList() },
        words = placed.map { p ->
            CariKataWord(
                id = p.id,
                length = p.word.length,
                found = p.found,
                word = if (p.found) p.word else null,
                color = p.color,
                cells = if (p.found) p.cells() else emptyList(),
                foundBy = p.foundBy,
                foundByEmoji = p.foundByEmoji,
            )
        },
        foundCount = placed.count { it.found },
        totalCount = placed.size,
    )

    override fun handleAnswer(answer: Parsed.Answer, player: String, emoji: String): AnswerResult {
        val raw = answer.text ?: return AnswerResult(false, "Jawaban kosong")
        // Coba seluruh komentar, lalu tiap kata di dalamnya ("ada kucing!" tetap kebaca).
        val candidates = (listOf(raw) + raw.split(WHITESPACE))
            .map { normalize(it) }
            .filter { it.length >= 3 }
            .distinct()
        if (candidates.isEmpty()) return AnswerResult(false, "Terlalu pendek")

        val hit = candidates.firstNotNullOfOrNull { c -> placed.firstOrNull { it.word == c && !it.found } }
        if (hit == null) {
            val already = candidates.any { c -> placed.any { it.word == c } }
            return AnswerResult(false, if (already) "Sudah ditemukan" else "Tidak ada di grid")
        }

        hit.found = true
        hit.foundBy = player
        hit.foundByEmoji = emoji
        return AnswerResult(true, "Benar!", hit.word.length)
    }

    override fun isComplete(): Boolean = placed.isNotEmpty() && placed.all { it.found }

    override fun nextRound() = generateRound()

    override fun reset() {
        usedThemes.clear()
        generateRound()
    }

    override fun parseComment(text: String): Parsed? {
        if (normalize(text).length < 3) return null
        return Parsed.Answer(text = text)
    }

    companion object {
        private const val EMPTY = '\u0000'
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val MAX_ROUND_TRIES = 30
        private const val MAX_FULL_ATTEMPTS = 40
        private const val MAX_ATTEMPTS_PER_WORD = 300
        private const val MIN_PLACED = 5
        private val WHITESPACE = Regex("\\s+")

        // (dr, dc): horizontal, vertikal, diagonal turun, diagonal naik
        private val DIRS_2 = listOf(0 to 1, 1 to 0)
        private val DIRS_4 = DIRS_2 + listOf(1 to 1, -1 to 1)

        private val COLORS = listOf(
            0xFF5EEAD4L, 0xFFF5B942L, 0xFFF472B6L, 0xFF8B6EF5L, 0xFF4ADE80L,
            0xFF60A5FAL, 0xFFFB923CL, 0xFFE879F9L, 0xFFFACC15L, 0xFF38BDF8L,
        )
    }
}
