package com.zahxrun.ttslive.engine

import kotlin.random.Random

data class QuestionView(
    val index: Int,
    /** Contoh: "12 × 7" (tanpa "= ?"). */
    val expression: String,
    /** null sampai terjawab, supaya jawaban nggak bocor ke layar. */
    val answer: Int?,
    val solved: Boolean,
    val winnerName: String?,
    val winnerEmoji: String?,
)

data class HitungCepatState(
    val roundNumber: Int,
    val questions: List<QuestionView>,
    val solvedCount: Int,
) : GameState

/** Port dari server/games/hitung-cepat/index.js */
class HitungCepatGame(private val random: Random = Random.Default) : Game {

    override val id = "hitung-cepat"
    override val title = "Hitung Cepat"

    private class Q(val expression: String, val answer: Int) {
        var solved = false
        var winnerName: String? = null
        var winnerEmoji: String? = null
    }

    private var questions: List<Q> = emptyList()
    private var roundNumber = 1

    init {
        generateRound()
    }

    private fun generateRound() {
        val list = ArrayList<Q>()
        val usedAnswers = HashSet<Int>()
        while (list.size < QUESTIONS_PER_ROUND) {
            val a: Int
            val b: Int
            val ans: Int
            val sym: String
            when (random.nextInt(3)) {
                0 -> { a = random.nextInt(50) + 1; b = random.nextInt(50) + 1; ans = a + b; sym = "+" }
                1 -> { a = random.nextInt(50) + 20; b = random.nextInt(20) + 1; ans = a - b; sym = "−" }
                else -> { a = random.nextInt(10) + 2; b = random.nextInt(10) + 2; ans = a * b; sym = "×" }
            }
            if (!usedAnswers.add(ans)) continue
            list.add(Q("$a $sym $b", ans))
        }
        questions = list
    }

    override fun buildState(): GameState = HitungCepatState(
        roundNumber = roundNumber,
        questions = questions.mapIndexed { i, q ->
            QuestionView(
                index = i,
                expression = q.expression,
                answer = if (q.solved) q.answer else null,
                solved = q.solved,
                winnerName = q.winnerName,
                winnerEmoji = q.winnerEmoji,
            )
        },
        solvedCount = questions.count { it.solved },
    )

    override fun handleAnswer(answer: Parsed.Answer, player: String, emoji: String): AnswerResult {
        val num = answer.number ?: return AnswerResult(false, "Bukan angka")
        val match = questions.firstOrNull { !it.solved && it.answer == num }
        if (match == null) {
            return if (questions.any { it.solved && it.answer == num }) {
                AnswerResult(false, "Sudah terjawab")
            } else {
                AnswerResult(false, "Salah")
            }
        }
        match.solved = true
        match.winnerName = player
        match.winnerEmoji = emoji
        return AnswerResult(true, "Benar!", POINTS_PER_ANSWER)
    }

    override fun isComplete(): Boolean = questions.isNotEmpty() && questions.all { it.solved }

    override fun nextRound() {
        roundNumber++
        generateRound()
    }

    override fun reset() {
        roundNumber = 1
        generateRound()
    }

    override fun parseComment(text: String): Parsed? {
        val normalized = text.trim().lowercase()
        if (normalized == "skip" || normalized == ".skip") return Parsed.Skip
        val m = NUMBER_RE.find(text) ?: return null
        val n = m.value.toIntOrNull() ?: return null
        return Parsed.Answer(number = n)
    }

    companion object {
        const val QUESTIONS_PER_ROUND = 8
        const val POINTS_PER_ANSWER = 10
        private val NUMBER_RE = Regex("\\b\\d+\\b")
    }
}
