package com.zahxrun.ttslive.engine

import android.content.Context
import org.json.JSONArray
import kotlin.random.Random

data class SusunKataSlot(
    val id: Int,
    val length: Int,
    val scrambled: String,
    val solved: Boolean,
    /** null sampai terjawab, supaya jawaban nggak bocor ke layar. */
    val answer: String?,
    val solvedBy: String,
    val solvedByEmoji: String,
)

data class SusunKataState(
    val slots: List<SusunKataSlot>,
    val foundCount: Int,
    val totalCount: Int,
) : GameState

/**
 * Port dari server/games/susun-kata-acak/index.js + word-bank.js (anagram, 6 slot per ronde).
 * Penonton tinggal ngetik kata yang benar; slot yang cocok kebuka. Ronde selesai kalau semua slot terjawab.
 * Anagram lain yang juga kata valid di bank (mis. KELAPA/KEPALA) ikut diterima.
 */
class SusunKataGame(
    context: Context,
    private val random: Random = Random.Default,
) : Game {

    override val id = "susun-kata-acak"
    override val title = "Susun Kata Acak"

    private class Slot(val id: Int, val answer: String, val scrambled: String) {
        var solved = false
        var shown = answer
        var solvedBy = ""
        var solvedByEmoji = ""
    }

    private val bank: List<String> = loadBank(context)
    private val bankSet: Set<String> = bank.toHashSet()
    private val recent = LinkedHashSet<String>()

    private var slots: List<Slot> = emptyList()

    init { generateRound() }

    private fun loadBank(context: Context): List<String> {
        val json = context.assets.open("questions/susun_kata.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        val words = (0 until arr.length()).map { normalize(arr.getString(it)) }.filter { it.length >= 3 }.distinct()
        check(words.isNotEmpty()) { "Bank kata susun_kata.json kosong" }
        return words
    }

    private fun normalize(s: String): String = s.uppercase().filter { it in 'A'..'Z' }

    /** Kunci anagram: huruf diurutkan. Dua kata beda tapi kuncinya sama = saling anagram. */
    private fun key(w: String): String = w.toCharArray().sorted().joinToString("")

    private fun scramble(word: String): String {
        var s = word
        var attempt = 0
        while (s == word && attempt < MAX_SCRAMBLE_ATTEMPTS) {
            s = word.toList().shuffled(random).joinToString("")
            attempt++
        }
        return s
    }

    private fun generateRound() {
        val picked = ArrayList<Slot>()
        val usedKeys = HashSet<String>() // 1 ronde nggak boleh punya 2 kata yang saling anagram (ambigu)
        for (i in 1..TOTAL_SLOTS) {
            var candidates = bank.filter { it !in recent && key(it) !in usedKeys }
            if (candidates.isEmpty()) candidates = bank.filter { key(it) !in usedKeys }
            if (candidates.isEmpty()) candidates = bank
            val word = candidates.random(random)
            usedKeys.add(key(word))
            recent.add(word)
            if (recent.size > RECENT_LIMIT) recent.remove(recent.first())
            picked.add(Slot(i, word, scramble(word)))
        }
        slots = picked
    }

    override fun buildState(): GameState = SusunKataState(
        slots = slots.map { s ->
            SusunKataSlot(
                id = s.id,
                length = s.answer.length,
                scrambled = s.scrambled,
                solved = s.solved,
                answer = if (s.solved) s.shown else null,
                solvedBy = s.solvedBy,
                solvedByEmoji = s.solvedByEmoji,
            )
        },
        foundCount = slots.count { it.solved },
        totalCount = slots.size,
    )

    override fun handleAnswer(answer: Parsed.Answer, player: String, emoji: String): AnswerResult {
        val raw = answer.text ?: return AnswerResult(false, "Jawaban kosong")
        // Coba seluruh komentar, lalu tiap kata di dalamnya ("jawabannya kucing!" tetap kebaca).
        val candidates = (listOf(raw) + raw.split(WHITESPACE))
            .map { normalize(it) }
            .filter { it.length >= 3 }
            .distinct()
        if (candidates.isEmpty()) return AnswerResult(false, "Terlalu pendek")

        for (c in candidates) {
            val slot = slots.firstOrNull { !it.solved && matches(it, c) } ?: continue
            slot.solved = true
            slot.shown = c
            slot.solvedBy = player
            slot.solvedByEmoji = emoji
            return AnswerResult(true, "Benar!", POINTS_PER_ANSWER)
        }

        val already = candidates.any { c -> slots.any { it.solved && it.answer == c } }
        return AnswerResult(false, if (already) "Sudah terjawab" else "Salah")
    }

    private fun matches(slot: Slot, candidate: String): Boolean {
        if (candidate == slot.answer) return true
        return candidate in bankSet && candidate.length == slot.answer.length && key(candidate) == key(slot.answer)
    }

    override fun isComplete(): Boolean = slots.isNotEmpty() && slots.all { it.solved }

    override fun nextRound() = generateRound()

    override fun reset() = generateRound()

    override fun parseComment(text: String): Parsed? {
        if (normalize(text).length < 3) return null
        return Parsed.Answer(text = text)
    }

    companion object {
        private const val TOTAL_SLOTS = 6
        private const val POINTS_PER_ANSWER = 10
        private const val RECENT_LIMIT = 600
        private const val MAX_SCRAMBLE_ATTEMPTS = 20
        private val WHITESPACE = Regex("\\s+")
    }
}
