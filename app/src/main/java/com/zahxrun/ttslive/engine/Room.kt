package com.zahxrun.ttslive.engine

import kotlin.random.Random

data class LeaderboardEntry(val player: String, val score: Int, val emoji: String)
data class ChatLine(val player: String, val text: String)
data class SummaryEntry(val player: String, val points: Int, val emoji: String)
data class RoundSummary(
    val top: List<SummaryEntry>,
    val total: List<LeaderboardEntry>,
    val durationMs: Long,
)

data class RoomSnapshot(
    val gameTitle: String,
    val game: GameState,
    val leaderboard: List<LeaderboardEntry>,
    val chat: List<ChatLine>,
    /** Non-null selama jeda papan hasil ronde (jawaban masuk diabaikan). */
    val summary: RoundSummary?,
)

data class ChatOutcome(
    val result: AnswerResult? = null,
    val roundCompleted: Boolean = false,
)

/**
 * Port ringkas dari server/services/room.js untuk satu pengguna:
 * skor, avatar emoji, alur komentar -> parse -> validasi -> skor, dan jeda akhir ronde.
 * Semua pemanggilan harus dari satu thread (main thread di aplikasi).
 */
class Room(
    private val game: Game,
    private val random: Random = Random.Default,
    private val summaryMs: Long = SUMMARY_MS,
) {
    private val total = LinkedHashMap<String, Int>()
    private val roundScores = LinkedHashMap<String, Int>()
    private val avatars = LinkedHashMap<String, String>()
    private val chat = ArrayDeque<ChatLine>()

    var awaitingAdvance = false
        private set

    fun handleChat(player: String, text: String): ChatOutcome {
        val name = player.trim().ifEmpty { "Anonim" }
        val msg = text.take(MAX_COMMENT_LEN)
        if (msg.isBlank()) return ChatOutcome()

        val emoji = avatarFor(name)
        chat.addLast(ChatLine(name, msg))
        while (chat.size > CHAT_KEEP) chat.removeFirst()

        // Selama jeda papan hasil, komentar tetap muncul tapi tidak dinilai.
        if (awaitingAdvance) return ChatOutcome()

        val parsed = game.parseComment(msg)
        if (parsed !is Parsed.Answer) return ChatOutcome() // Skip: belum ada voting di fase 1

        val result = game.handleAnswer(parsed, name, emoji)
        if (!result.ok) return ChatOutcome(result)

        total[name] = (total[name] ?: 0) + result.points
        roundScores[name] = (roundScores[name] ?: 0) + result.points

        var completed = false
        if (game.isComplete()) {
            awaitingAdvance = true
            completed = true
        }
        return ChatOutcome(result, completed)
    }

    /** Dipanggil setelah jeda summaryMs selesai. */
    fun advance() {
        game.nextRound()
        roundScores.clear()
        awaitingAdvance = false
    }

    fun summaryDurationMs(): Long = summaryMs

    fun resetAll() {
        game.reset()
        total.clear()
        roundScores.clear()
        avatars.clear()
        chat.clear()
        awaitingAdvance = false
    }

    fun exportScores(): Map<String, Int> = LinkedHashMap(total)

    fun importScores(scores: Map<String, Int>) {
        total.clear()
        total.putAll(scores)
    }

    fun snapshot(): RoomSnapshot = RoomSnapshot(
        gameTitle = game.title,
        game = game.buildState(),
        leaderboard = leaderboard(LEADERBOARD_SIZE),
        chat = chat.toList(),
        summary = if (awaitingAdvance) buildSummary() else null,
    )

    private fun leaderboard(limit: Int): List<LeaderboardEntry> =
        total.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { LeaderboardEntry(it.key, it.value, avatars[it.key] ?: "") }

    private fun buildSummary(): RoundSummary = RoundSummary(
        top = roundScores.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(3)
            .map { SummaryEntry(it.key, it.value, avatars[it.key] ?: "") },
        total = leaderboard(5),
        durationMs = summaryMs,
    )

    private fun avatarFor(name: String): String {
        avatars[name]?.let { return it }
        val used = avatars.values.toSet()
        val free = AVATAR_POOL.filter { it !in used }
        val pick = (if (free.isNotEmpty()) free else AVATAR_POOL).random(random)
        avatars[name] = pick
        return pick
    }

    companion object {
        const val SUMMARY_MS = 7000L
        const val MAX_COMMENT_LEN = 120
        const val CHAT_KEEP = 4
        const val LEADERBOARD_SIZE = 10

        val AVATAR_POOL = listOf(
            "😎", "🤠", "🥳", "😺", "🐶", "🐼", "🦊", "🐯", "🦁", "🐸",
            "🐵", "🐧", "🦄", "🐙", "🦉", "🐢", "🐳", "🦖", "👻", "🤖",
            "👽", "🎃", "🍕", "🍔", "🍩", "🍉", "⚽", "🎸", "🚀", "🌈",
        )
    }
}
