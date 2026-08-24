package com.helucryptic.android.crypto

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest

/**
 * HELU-INV1 room-invite codec, wire-compatible with desktop invites.py.
 *
 * Canonical format: `HELU-INV1:<base64url(compact JSON)>` with fields
 *   v=1, r=room_id, u=signaling_url, p=password?, k=psk?, c=creator_ed25519_pub?,
 *   m=1 (ephemeral?), h=sha256(canonical JSON sans h)[:16] hex.
 *
 * The checksum is computed over the payload serialised EXACTLY like Python's
 * `json.dumps(payload, sort_keys=True, separators=(",", ":"))` - so the
 * canonical serialiser below sorts keys and emits no whitespace. All field
 * values are ASCII (base64 / URLs / room codes), so no unicode escaping is
 * needed for parity.
 *
 * decode() also accepts the legacy Android-only `HELU-INV1:ROOM:PSK:URL`
 * colon format so previously shared invites keep working.
 */
object InviteCodec {

    const val PREFIX = "HELU-INV1:"
    private val ROOM_RE = Regex("^ROOM-[A-Z0-9]{4}$")
    private val URL_RE  = Regex("^(ws|wss|http|https)://", RegexOption.IGNORE_CASE)

    data class Invite(
        val roomId: String,
        val signalingUrl: String,
        val password: String? = null,
        val psk: String? = null,
        val creatorEd25519Pub: String? = null,
        val ephemeral: Boolean = false,
    )

    fun encode(invite: Invite): String {
        require(ROOM_RE.matches(invite.roomId)) { "Invalid room id (expected ROOM-XXXX)" }
        require(URL_RE.containsMatchIn(invite.signalingUrl)) { "Invalid signaling URL" }
        invite.psk?.let {
            require(runCatching { Base64.decode(it, Base64.NO_WRAP).size == 32 }.getOrDefault(false)) {
                "PSK must be base64 of 32 bytes"
            }
        }
        val fields = sortedMapOf<String, Any>(
            "v" to 1,
            "r" to invite.roomId,
            "u" to invite.signalingUrl,
        )
        invite.password?.takeIf { it.isNotEmpty() }?.let { fields["p"] = it }
        invite.psk?.takeIf { it.isNotEmpty() }?.let { fields["k"] = it }
        invite.creatorEd25519Pub?.takeIf { it.isNotEmpty() }?.let { fields["c"] = it }
        if (invite.ephemeral) fields["m"] = 1

        fields["h"] = checksum(fields)
        val raw = canonicalJson(fields).toByteArray(Charsets.UTF_8)
        // Keep '=' padding: the desktop decoder (python base64.urlsafe_b64decode)
        // rejects unpadded input with "Incorrect padding".
        return PREFIX + Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /** Parse + validate. Throws IllegalArgumentException on any problem. */
    fun decode(code: String): Invite {
        val trimmed = (code).trim()
        require(trimmed.startsWith(PREFIX)) { "Not a helucryptic invite code" }
        val body = trimmed.removePrefix(PREFIX)

        // Legacy Android colon format: ROOM:PSK:URL (URL may itself contain ':').
        if (!body.startsWith("ey") || runCatching { JSONObject(decodeB64Url(body)) }.isFailure) {
            val parts = body.split(":")
            if (parts.size >= 3 && URL_RE.containsMatchIn(parts.subList(2, parts.size).joinToString(":"))) {
                val room = parts[0]
                val psk  = parts[1]
                val url  = parts.subList(2, parts.size).joinToString(":")
                require(room.isNotBlank() && psk.isNotBlank()) { "Malformed invite code" }
                return Invite(roomId = room, signalingUrl = url, psk = psk)
            }
        }

        val obj = runCatching { JSONObject(decodeB64Url(body)) }
            .getOrElse { throw IllegalArgumentException("Malformed invite code") }

        val supplied = obj.optString("h").takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Invite code is corrupted (no checksum)")
        val fields = sortedMapOf<String, Any>()
        for (key in obj.keys()) {
            if (key != "h") fields[key] = obj.get(key)
        }
        if (checksum(fields) != supplied) {
            throw IllegalArgumentException("Invite code is corrupted or was tampered with")
        }

        val room = obj.optString("r")
        val url  = obj.optString("u")
        require(ROOM_RE.matches(room)) { "Invite code has an invalid room id" }
        require(URL_RE.containsMatchIn(url)) { "Invite code has an invalid signaling URL" }
        val psk = obj.optString("k").takeIf { it.isNotEmpty() }
        psk?.let {
            require(runCatching { Base64.decode(it, Base64.NO_WRAP).size == 32 }.getOrDefault(false)) {
                "Invite code has an invalid PSK"
            }
        }
        return Invite(
            roomId            = room,
            signalingUrl      = url,
            password          = obj.optString("p").takeIf { it.isNotEmpty() },
            psk               = psk,
            creatorEd25519Pub = obj.optString("c").takeIf { it.isNotEmpty() },
            ephemeral         = obj.optInt("m", 0) == 1,
        )
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun decodeB64Url(s: String): String =
        String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)

    private fun checksum(fields: Map<String, Any>): String {
        val canonical = canonicalJson(fields.toSortedMap())
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    /**
     * Serialise like Python `json.dumps(d, sort_keys=True, separators=(",",":"))`.
     * NOTE: org.json's JSONObject.toString() escapes '/' as '\/' and does not
     * sort keys, which would break checksum parity - hence this manual emitter.
     */
    private fun canonicalJson(fields: Map<String, Any>): String =
        fields.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
            val value = when (v) {
                is Int, is Long -> v.toString()
                else            -> "\"${escapeJson(v.toString())}\""
            }
            "\"${escapeJson(k)}\":$value"
        }

    private fun escapeJson(s: String): String = buildString {
        for (ch in s) {
            when {
                ch == '"'      -> append("\\\"")
                ch == '\\'     -> append("\\\\")
                ch < ' '       -> append("\\u%04x".format(ch.code))
                ch.code > 126  -> append("\\u%04x".format(ch.code))  // ensure_ascii parity
                else           -> append(ch)
            }
        }
    }
}
