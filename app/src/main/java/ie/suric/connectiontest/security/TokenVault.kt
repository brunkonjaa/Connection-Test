package ie.suric.connectiontest.security

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import ie.suric.connectiontest.auth.Tokens
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the complete OAuth record with a non-exportable Android Keystore AES key. */
class TokenVault(context: Context) {
    private val prefs = context.getSharedPreferences("oauth_vault", Context.MODE_PRIVATE)
    private val alias = "ask_buster_oauth_v1"

    fun save(tokens: Tokens) {
        val record = JSONObject().apply {
            put("client_id", tokens.clientId); put("access_token", tokens.accessToken)
            put("refresh_token", tokens.refreshToken); put("id_token", tokens.idToken)
            put("subject", tokens.subject); put("email", tokens.email ?: JSONObject.NULL)
            put("scopes", tokens.scopes.joinToString(" ")); put("expires_at", tokens.expiresAtEpochSeconds)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(record)
        val packed = cipher.iv + encrypted
        check(prefs.edit().putString("record", Base64.encodeToString(packed, Base64.NO_WRAP)).commit())
    }

    fun load(): Tokens? = runCatching {
        val packed = Base64.decode(prefs.getString("record", null) ?: return null, Base64.NO_WRAP)
        require(packed.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        val j = JSONObject(String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8))
        Tokens(j.getString("client_id"), j.getString("access_token"), j.getString("refresh_token"),
            j.getString("id_token"), j.getString("subject"), j.optString("email").takeIf { it.isNotBlank() },
            j.getString("scopes").split(' ').filter(String::isNotBlank).toSet(), j.getLong("expires_at"))
    }.getOrNull()

    fun clear() { prefs.edit().clear().commit() }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}
