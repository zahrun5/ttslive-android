package com.zahxrun.ttslive.engine

import android.content.Context
import org.json.JSONArray
import kotlin.random.Random

data class SambungKataCell(
    val row: Int,
    val col: Int,
    /** null sampai terbuka, supaya jawaban nggak bocor ke layar. */
    val letter: Char?,
    /** Nomor kata yang dimulai di sel ini (kalau ada). */
    val number: Int?,
)

data class SambungKataWord(
    val number: Int,
    val across: Boolean,
    val length: Int,
    val solved: Boolean,
    val answer: String?,
    val solvedBy: String,
    val solvedByEmoji: String,
)

data class SambungKataState(
    val rows: Int,
    val cols: Int,
    /** Huruf dasar; semua kata di grid disusun dari huruf-huruf ini. */
    val baseLetters: String,
    /** Hanya sel yang dipakai kata (sel kosong tidak ada di daftar). */
    val cells: List<SambungKataCell>,
    val words: List<SambungKataWord>,
    val foundCount: Int,
    val totalCount: Int,
) : GameState

/**
 * Port dari server/games/sambung-kata/index.js. Teka-teki silang tanpa clue: penonton melihat huruf dasar
 * dan nomor kata di grid, lalu mengetik kata yang mereka temukan. Poin = jumlah sel yang baru terbuka.
 *
 * Tata letak grid dihitung lebih dulu dengan generator Node (word-placement.js) dan disimpan di
 * assets/questions/sambung_kata.json, jadi di sini nggak perlu menempatkan kata lagi.
 */
class SambungKataGame(
    context: Context,
    private val random: Random = Random.Default,
) : Game {

    override val id = "sambung-kata"
    override val title = "Sambung Kata"

    private class W(
        val number: Int,
        val across: Boolean,
        val row: Int,
        val col: Int,
        val answer: String,
    ) {
        var solved = false
        var solvedBy = ""
        var solvedByEmoji = ""

        fun cellAt(i: Int): Pair<Int, Int> =
            if (across) Pair(row, col + i) else Pair(row + i, col)
    }

    private class Puzzle(val base: String, val rows: Int, val cols: Int, val words: List<W>)

    private val bank: List<Puzzle> = loadBank(context)
    private val usedBases = mutableSetOf<String>()

    private var puzzle: Puzzle = bank.first()

    init { generateRound() }

    private fun loadBank(context: Context): List<Puzzle> {
        val json = context.assets.open("questions/sambung_kata.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val wa = o.getJSONArray("words")
            val words = (0 until wa.length()).map { j ->
                val w = wa.getJSONObject(j)
                W(
                    number = w.getInt("n"),
                    across = w.getString("d") == "A",
                    row = w.getInt("r"),
                    col = w.getInt("c"),
                    answer = normalize(w.getString("a")),
                )
            }
            Puzzle(o.getString("base"), o.getInt("rows"), o.getInt("cols"), words)
        }
        check(list.isNotEmpty()) { "Bank sambung_kata.json kosong" }
        return list
    }

    private fun normalize(s: String): String = s.uppercase().filter { it in 'A'..'Z' }

    private fun generateRound() {
        var pool = bank.filter { it.base !in usedBases }
        if (pool.isEmpty()) {
            usedBases.clear()
            pool = bank
        }
        val p = pool.random(random)
        usedBases.add(p.base)
        // Bank dipakai berulang, jadi status jawaban dibersihkan tiap kali puzzle dimuat.
        p.words.forEach {
            it.solved = false
            it.solvedBy = ""
            it.solvedByEmoji = ""
        }
        puzzle = p
    }

    /** Kunci "baris,kolom" semua sel yang sudah terbuka. */
    private fun revealedCells(): Set<Int> {
        val out = HashSet<Int>()
        for (w in puzzle.words) {
            if (!w.solved) continue
            for (i in w.answer.indices) {
                val (r, c) = w.cellAt(i)
                out.add(r * puzzle.cols + c)
            }
        }
        return out
    }

    override fun buildState(): GameState {
        val revealed = revealedCells()
        val letters = HashMap<Int, Char>()
        val numbers = HashMap<Int, Int>()
        for (w in puzzle.words) {
            for (i in w.answer.indices) {
                val (r, c) = w.cellAt(i)
                val key = r * puzzle.cols + c
                letters[key] = w.answer[i]
                if (i == 0 && key !in numbers) numbers[key] = w.number
            }
        }
        val cells = letters.keys.sorted().map { key ->
            SambungKataCell(
                row = key / puzzle.cols,
                col = key % puzzle.cols,
                letter = if (key in revealed) letters[key] else null,
                number = numbers[key],
            )
        }
        return SambungKataState(
            rows = puzzle.rows,
            cols = puzzle.cols,
            baseLetters = puzzle.base,
            cells = cells,
            words = puzzle.words.map { w ->
                SambungKataWord(
                    number = w.number,
                    across = w.across,
                    length = w.answer.length,
                    solved = w.solved,
                    answer = if (w.solved) w.answer else null,
                    solvedBy = w.solvedBy,
                    solvedByEmoji = w.solvedByEmoji,
                )
            },
            foundCount = puzzle.words.count { it.solved },
            totalCount = puzzle.words.size,
        )
    }

    override fun handleAnswer(answer: Parsed.Answer, player: String, emoji: String): AnswerResult {
        val raw = answer.text ?: return AnswerResult(false, "Jawaban kosong")
        // Coba seluruh komentar, lalu tiap kata di dalamnya ("ada bunga!" tetap kebaca).
        val candidates = (listOf(raw) + raw.split(WHITESPACE))
            .map { normalize(it) }
            .filter { it.length >= 3 }
            .distinct()
        if (candidates.isEmpty()) return AnswerResult(false, "Terlalu pendek")

        val hit = candidates.firstNotNullOfOrNull { c -> puzzle.words.firstOrNull { !it.solved && it.answer == c } }
        if (hit == null) {
            val already = candidates.any { c -> puzzle.words.any { it.solved && it.answer == c } }
            return AnswerResult(false, if (already) "Sudah terisi" else "Salah")
        }

        val before = revealedCells().size
        hit.solved = true
        hit.solvedBy = player
        hit.solvedByEmoji = emoji
        val newCells = revealedCells().size - before
        return AnswerResult(true, "Benar!", newCells)
    }

    override fun isComplete(): Boolean = puzzle.words.isNotEmpty() && puzzle.words.all { it.solved }

    override fun nextRound() = generateRound()

    override fun reset() {
        usedBases.clear()
        generateRound()
    }

    override fun parseComment(text: String): Parsed? {
        if (normalize(text).length < 3) return null
        return Parsed.Answer(text = text)
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}
