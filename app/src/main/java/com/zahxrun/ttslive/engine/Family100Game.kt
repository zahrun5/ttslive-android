package com.zahxrun.ttslive.engine

import android.content.Context
import org.json.JSONArray

data class Family100State(
    val question: String,
    val board: List<BoardSlot?>,   // 7 slot, null = kosong
    val foundCount: Int,
    val totalAnswers: Int,
) : GameState

data class BoardSlot(
    val text: String,
    val points: Int,
    val foundBy: String,
    val foundByEmoji: String,
)

/**
 * Port dari server/data/rooms/mamangharun/games/family100/index.js
 * Board 7 slot, jawaban 10-15, poin makin kecil sesuai ranking.
 */
class Family100Game(context: Context) : Game {

    override val id = "family100"
    override val title = "Family 100"

    private val BOARD_SLOTS = 7

    private data class Answer(
        val text: String,
        val points: Int,
        var found: Boolean = false,
        var foundBy: String = "",
        var foundByEmoji: String = "",
    )

    private val allQuestions: List<Pair<String, List<String>>> = loadBank(context)
    private val usedIndices = mutableSetOf<Int>()

    private var currentQuestion = ""
    private var answers: List<Answer> = emptyList()
    private var board: Array<Int?> = arrayOfNulls(BOARD_SLOTS)

    init { generateRound() }

    private fun loadBank(context: Context): List<Pair<String, List<String>>> {
        val json = context.assets.open("questions/family100.json").bufferedReader().readText()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val q = obj.getString("question")
            val answersArr = obj.getJSONArray("answers")
            val ans = (0 until answersArr.length()).map { normalize(answersArr.getString(it)) }
            Pair(q, ans)
        }
    }

    private fun normalize(s: String): String =
        s.uppercase().replace('-', ' ').replace(Regex("[^A-Z ]"), "").replace(Regex("\\s+"), " ").trim()

    private fun generateRound() {
        val pool = allQuestions.indices.filter { it !in usedIndices }
            .ifEmpty { usedIndices.clear(); allQuestions.indices.toList() }
        val idx = pool.random()
        usedIndices.add(idx)
        val (q, answerList) = allQuestions[idx]
        currentQuestion = q
        // Poin: ranking 1 = 30, 2 = 27, ..., min 3
        answers = answerList.mapIndexed { i, text ->
            Answer(text = text, points = maxOf(3, 30 - i * 3))
        }
        board = arrayOfNulls(BOARD_SLOTS)
    }

    override fun buildState(): GameState = Family100State(
        question = currentQuestion,
        board = board.map { idx ->
            if (idx == null) null
            else answers[idx].let { BoardSlot(it.text, it.points, it.foundBy, it.foundByEmoji) }
        },
        foundCount = answers.count { it.found },
        totalAnswers = answers.size,
    )

    override fun handleAnswer(answer: Parsed.Answer, player: String, emoji: String): AnswerResult {
        val raw = answer.text ?: return AnswerResult(false, "Jawaban kosong")
        val norm = normalize(raw)
        if (norm.length < 2) return AnswerResult(false, "Terlalu pendek")

        if (boardFilledCount() >= BOARD_SLOTS)
            return AnswerResult(false, "Papan sudah penuh!")

        // Matching: exact, contains, atau answer contains norm
        val match = answers.firstOrNull { a ->
            !a.found && (a.text == norm || (norm.length >= 4 && a.text.contains(norm)) || (a.text.length >= 4 && norm.contains(a.text)))
        } ?: return AnswerResult(false, "Tidak ada di daftar")

        match.found = true
        match.foundBy = player
        match.foundByEmoji = emoji
        insertToBoard()

        return AnswerResult(true, "Benar!", match.points)
    }

    private fun insertToBoard() {
        val found = answers.mapIndexed { i, a -> Pair(i, a) }
            .filter { it.second.found }
            .sortedByDescending { it.second.points }
            .take(BOARD_SLOTS)

        board = arrayOfNulls(BOARD_SLOTS)
        var prevSlot = -1
        for ((rankIdx, pair) in found.withIndex()) {
            val (origIdx, _) = pair
            var target = (origIdx / 2).coerceAtMost(6)
            var slot = target.coerceAtLeast(prevSlot + 1)
            val maxAllowed = 6 - (found.size - 1 - rankIdx)
            slot = slot.coerceAtMost(maxAllowed)
            board[slot] = origIdx
            prevSlot = slot
        }
    }

    private fun boardFilledCount() = board.count { it != null }

    override fun isComplete() = boardFilledCount() >= BOARD_SLOTS

    override fun nextRound() = generateRound()

    override fun reset() {
        usedIndices.clear()
        generateRound()
    }

    override fun parseComment(text: String): Parsed? {
        val cleaned = normalize(text)
        if (cleaned.length < 2) return null
        return Parsed.Answer(text = cleaned)
    }
}
