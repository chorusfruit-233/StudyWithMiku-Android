package com.shshouse.studywithmiku

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AccountState(val userId: String = "", val username: String = "", val expiresAt: Long = 0,
    val expired: Boolean = false, val validating: Boolean = false, val error: String? = null) {
    val loggedIn get() = userId.isNotBlank() && !expired
}
class TokenVault(context: Context) {
    private val disk = context.getSharedPreferences("study_auth_secure",Context.MODE_PRIVATE)
    private val alias = "study_mikumod_token_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun write(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        disk.edit().putString("iv",Base64.getEncoder().encodeToString(cipher.iv))
            .putString("ciphertext",Base64.getEncoder().encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)))).commit()
    }
    fun read(): String = runCatching {
        val value = disk.getString("ciphertext",null) ?: return@runCatching ""
        val iv = Base64.getDecoder().decode(disk.getString("iv","")!!)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv)) }
        String(cipher.doFinal(Base64.getDecoder().decode(value)),Charsets.UTF_8)
    }.getOrDefault("")
    fun clear() { disk.edit().clear().commit() }
}
class StudyAuth(context: Context,private val api: StudyApi) {
    private val vault = TokenVault(context)
    private var credential = vault.read()
    private val _state = MutableStateFlow(runCatching {
        if (credential.isBlank()) AccountState() else StudyProtocol.tokenClaims(credential,0).let {
            AccountState(it.userId,it.username,it.expiresAt,it.expiresAt <= Instant.now().epochSecond)
        }
    }.getOrElse { credential = ""; AccountState(error = "登录凭据不可用，请重新登录") })
    val state = _state.asStateFlow()
    fun token(): String {
        if (_state.value.expiresAt > 0 && _state.value.expiresAt <= Instant.now().epochSecond) expire()
        return if (_state.value.loggedIn) credential else ""
    }
    suspend fun accept(token: String): Boolean {
        _state.value = _state.value.copy(validating = true,error = null)
        return try {
            val claims = StudyProtocol.tokenClaims(token)
            withContext(Dispatchers.IO) {
                api.request("/api/study/data",token) // Only a server-validated token is persisted.
                vault.write(token)
            }
            credential = token
            _state.value = AccountState(claims.userId,claims.username,claims.expiresAt)
            true
        } catch (e: CancellationException) { _state.value = _state.value.copy(validating = false); throw e }
        catch (e: Exception) {
            _state.value = _state.value.copy(validating = false,error = if (e is StudyApiException) e.message else "无法验证登录，请检查网络或重新登录")
            false
        }
    }
    fun expire() { _state.value = _state.value.copy(expired = true,error = "登录已过期，请重新登录") }
    fun logout() {
        credential = ""; vault.clear(); _state.value = AccountState()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }
}
