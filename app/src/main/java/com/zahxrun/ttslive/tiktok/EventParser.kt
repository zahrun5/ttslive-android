package com.zahxrun.ttslive.tiktok

import org.json.JSONArray
import org.json.JSONObject

data class ChatEvent(val player: String, val text: String, val profileUrl: String?)

/**
 * Mengambil komentar dari frame JSON EulerStream.
 *
 * Bentuk persis tiap pesan belum dikunci di sini, jadi parser ini menelusuri seluruh pohon JSON
 * dan menganggap objek apa pun yang punya `comment` (teks) + `user` sebagai komentar chat.
 * Ini sengaja longgar supaya tahan terhadap perbedaan pembungkus ("messages", "data", dll).
 */
object EventParser {

    fun extractChats(raw: String): List<ChatEvent> {
        val out = ArrayList<ChatEvent>()
        val root: Any = try {
            val t = raw.trim()
            if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
        } catch (e: Exception) {
            return out
        }
        walk(root, out, 0)
        return out
    }

    private fun walk(node: Any?, out: MutableList<ChatEvent>, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JSONObject -> {
                val chat = asChat(node)
                if (chat != null) {
                    out.add(chat)
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), out, depth + 1)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), out, depth + 1)
        }
    }

    private fun asChat(o: JSONObject): ChatEvent? {
        val comment = o.optString("comment", "")
        if (comment.isEmpty()) return null
        val user = o.optJSONObject("user")
        if (user == null && !o.has("uniqueId") && !o.has("nickname")) return null

        val name = firstNonBlank(
            user?.optString("nickname", ""),
            user?.optString("uniqueId", ""),
            o.optString("nickname", ""),
            o.optString("uniqueId", ""),
        ) ?: "TikTokUser"

        return ChatEvent(name, comment, profileUrlOf(user, o))
    }

    private fun profileUrlOf(user: JSONObject?, o: JSONObject): String? {
        user?.optString("profilePictureUrl", "")?.takeIf { it.isNotBlank() }?.let { return it }
        o.optString("profilePictureUrl", "").takeIf { it.isNotBlank() }?.let { return it }
        val urls = user?.optJSONArray("profilePictureUrls") ?: user?.optJSONObject("userDetails")?.optJSONArray("profilePictureUrls")
        if (urls != null && urls.length() > 0) return urls.optString(0, "").takeIf { it.isNotBlank() }
        return null
    }

    private fun firstNonBlank(vararg s: String?): String? = s.firstOrNull { !it.isNullOrBlank() }

    private const val MAX_DEPTH = 8
}
