package com.zahxrun.ttslive.engine

import android.content.Context
import org.json.JSONArray
import kotlin.random.Random

data class TtsWord(
    val number: Int,
    val across: Boolean,
    val length: Int,
    val clue: String,
    val solved: Boolean,
    /** null sampai terjawab, supaya jawaban nggak bocor ke layar. */
    val answer: String?,
    val solvedBy: String,
    val solvedByEmoji: String,
)

data class TtsState(
    val rows: Int,
    val cols: Int,
    /** Hanya sel yang dipakai kata (sel kosong tidak ada di daftar). */
    val cells: List<SambungKataCell>,
    val words: List<TtsWord>,
    val foundCount: Int,
    val totalCount: Int,
) : GameState

/**
 * Port dari server/games/tts/index.js + comment-parser.js. Teka-teki silang dengan clue:
 * penonton menjawab "3 jakarta", "jakarta nomor 3", "3 menurun jakarta", atau langsung "jakarta".
 * Poin = jumlah sel yang baru terbuka (sel persilangan nggak dihitung dobel).
 *
 * Tata letak grid dihitung lebih dulu dengan generator Node (word-placement.js) dari bank soal TTS dan
 * disimpan di assets/questions/tts.json, jadi di sini nggak perlu menempatkan kata lagi.
 */
class TtsGame(
    context: Context,
    private val random: Random = Random.Default,
) : Game {

    override val id = "tts"
    override val title = "TTS"

    private class W(
        val number: Int,
        val across: Boolean,
        val row: Int,
        val col: Int,
        val answer: String,
        val clue: String,
    ) {
        var solved = false
        var solvedBy = ""
        var solvedByEmoji = ""

        fun cellAt(i: Int): Pair<Int, Int> =
            if (across) Pair(row, col + i) else Pair(row + i, col)
    }

    private class Puzzle(val rows: Int, val cols: Int, val words: List<W>)

    private val bank: List<Puzzle> = loadBank(context)
    private val usedIndices = mutableSetOf<Int>()

    private var puzzle: Puzzle = bank.first()

    init { generateRound() }

    private fun loadBank(context: Context): List<Puzzle> {
        val json = context.assets.open("questions/tts.json").bufferedReader().use { it.readText() }
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
                    clue = w.optString("k", ""),
                )
            }
            Puzzle(o.getInt("rows"), o.getInt("cols"), words)
        }
        check(list.isNotEmpty()) { "Bank tts.json kosong" }
        return list
    }

    /** Sama seperti Node: huruf dan angka saja. */
    private fun normalize(s: String): String = s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    private fun generateRound() {
        var pool = bank.indices.filter { it !in usedIndices }
        if (pool.isEmpty()) {
            usedIndices.clear()
            pool = bank.indices.toList()
        }
        val idx = pool.random(random)
        usedIndices.add(idx)
        val p = bank[idx]
        // Bank dipakai berulang, jadi status jawaban dibersihkan tiap kali puzzle dimuat.
        p.words.forEach {
            it.solved = false
            it.solvedBy = ""
            it.solvedByEmoji = ""
        }
        puzzle = p
    }

    /** Kunci (baris * jumlahKolom + kolom) semua sel yang sudah terbuka. */
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
        return TtsState(
            rows = puzzle.rows,
            cols = puzzle.cols,
            cells = cells,
            words = puzzle.words.map { w ->
                TtsWord(
                    number = w.number,
                    across = w.across,
                    length = w.answer.length,
                    clue = w.clue,
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
        val number = answer.number

        // Tanpa nomor, komentar bisa berisi kalimat ("ada jakarta"), jadi tiap kata ikut dicoba.
        val tries = if (number == null) {
            (listOf(raw) + raw.split(WHITESPACE)).map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
        } else {
            listOf(normalize(raw)).filter { it.isNotEmpty() }
        }
        if (tries.isEmpty()) return AnswerResult(false, "Jawaban kosong")

        var candidates = if (number != null) puzzle.words.filter { it.number == number } else puzzle.words
        if (number != null && candidates.isEmpty()) return AnswerResult(false, "Nomor tidak ditemukan")

        val across = answer.across
        if (across != null) {
            val filtered = candidates.filter { it.across == across }
            if (filtered.isNotEmpty()) candidates = filtered
        }

        val hit = tries.firstNotNullOfOrNull { t -> candidates.firstOrNull { !it.solved && it.answer == t } }
        if (hit == null) {
            return if (candidates.all { it.solved }) AnswerResult(false, "Sudah terisi") else AnswerResult(false, "Salah")
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
        usedIndices.clear()
        generateRound()
    }

    /**
     * Parser komentar (port comment-parser.js, tanpa AI):
     * "3 jakarta", "3. jakarta", "nomor 3 jakarta", "jakarta nomor 3", atau "jakarta" saja.
     * Kata arah (mendatar/menurun/...) dibuang dari teks dan dicatat sebagai petunjuk arah.
     */
    override fun parseComment(text: String): Parsed? {
        val across: Boolean? = when {
            ACROSS_RE.containsMatchIn(text) -> true
            DOWN_RE.containsMatchIn(text) -> false
            else -> null
        }
        val clean = DIRECTION_WORDS_RE.replace(text, " ").replace(WHITESPACE, " ").trim()
        if (clean.isEmpty()) return null
        val n = clean.lowercase()

        FORMAT_NUMBER_FIRST.matchEntire(n)?.let { m ->
            return Parsed.Answer(number = m.groupValues[1].toIntOrNull(), text = m.groupValues[2].trim(), across = across)
        }
        FORMAT_NOMOR_FIRST.find(n)?.let { m ->
            return Parsed.Answer(number = m.groupValues[1].toIntOrNull(), text = m.groupValues[2].trim(), across = across)
        }
        FORMAT_NOMOR_LAST.matchEntire(n)?.let { m ->
            return Parsed.Answer(number = m.groupValues[2].toIntOrNull(), text = m.groupValues[1].trim(), across = across)
        }
        // Jawaban langsung tanpa nomor; ditolak kalau cuma angka (nggak jelas nomor atau jawaban).
        if (!ONLY_DIGITS.matches(n) && n.length >= 2) {
            return Parsed.Answer(number = null, text = n, across = across)
        }
        return null
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val ACROSS_RE = Regex("\\b(mendatar|horizontal|kesamping|across)\\b", RegexOption.IGNORE_CASE)
        private val DOWN_RE = Regex("\\b(menurun|vertikal|kebawah|down)\\b", RegexOption.IGNORE_CASE)
        private val DIRECTION_WORDS_RE =
            Regex("\\b(mendatar|menurun|horizontal|vertikal|kesamping|kebawah|across|down)\\b", RegexOption.IGNORE_CASE)
        private val FORMAT_NUMBER_FIRST = Regex("^(\\d+)[.\\s]+(.+)$")
        private val FORMAT_NOMOR_FIRST = Regex("(?:nomor|no|nomer|number)\\s*(\\d+)\\s+(.+)$")
        private val FORMAT_NOMOR_LAST = Regex("^(.+?)\\s+(?:nomor|no|nomer|number)\\s*(\\d+)$")
        private val ONLY_DIGITS = Regex("^\\d+$")
    }
}
