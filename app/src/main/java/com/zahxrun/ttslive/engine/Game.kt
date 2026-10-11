package com.zahxrun.ttslive.engine

/**
 * Kontrak game, setara GAME-INTERFACE.md versi Node.
 * Paket `engine` murni Kotlin (tanpa import Android) supaya gampang dites di JVM biasa.
 */
sealed interface GameState

data class AnswerResult(val ok: Boolean, val msg: String, val points: Int = 0)

sealed interface Parsed {
    /** [across]: true = mendatar, false = menurun, null = tidak disebut (dipakai TTS). */
    data class Answer(val number: Int? = null, val text: String? = null, val across: Boolean? = null) : Parsed
    data object Skip : Parsed
}

interface Game {
    val id: String
    val title: String

    fun buildState(): GameState
    fun handleAnswer(answer: Parsed.Answer, player: String, emoji: String): AnswerResult
    fun isComplete(): Boolean

    /** Ronde berikutnya (setara onComplete() di versi Node). */
    fun nextRound()
    fun reset()
    fun parseComment(text: String): Parsed?
}
