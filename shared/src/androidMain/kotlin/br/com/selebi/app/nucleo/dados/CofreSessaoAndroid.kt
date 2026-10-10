package br.com.selebi.app.nucleo.dados

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.cofre by preferencesDataStore(name = "cofre")

/**
 * O [CofreSessao] do Android: token e perfil cifrados.
 *
 * O `EncryptedSharedPreferences` foi descontinuado pelo Google em 2025. A
 * recomendação atual é DataStore para guardar e Tink (AES-256-GCM) para
 * cifrar, com a chave-mestra presa ao Keystore do aparelho — ela não sai
 * dele, nem em backup.
 *
 * Qualquer falha ao decifrar (chave perdida numa atualização do sistema,
 * aparelho de fabricante com Keystore problemático) vira "não há sessão" e o
 * técnico entra de novo. Nunca vira o app fechando sozinho.
 */
class CofreSessaoAndroid(
    private val contexto: Context,
    private val json: Json,
) : CofreSessao {
    private val chave = stringPreferencesKey("sessao")
    private val dadosAssociados = "selebi-sessao".encodeToByteArray()

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(contexto, "selebi_chaves", "selebi_chaves_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://selebi_chave_mestra")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override suspend fun ler(): SessaoSalva? = withContext(Dispatchers.IO) {
        runCatching {
            val cifrado = contexto.cofre.data.first()[chave] ?: return@runCatching null
            val claro = aead.decrypt(Base64.decode(cifrado, Base64.NO_WRAP), dadosAssociados)
            json.decodeFromString(SessaoSalva.serializer(), claro.decodeToString())
        }.getOrElse {
            Log.w("CofreSessao", "Sessão guardada não abriu; segue sem sessão.", it)
            null
        }
    }

    override suspend fun salvar(sessao: SessaoSalva) = withContext(Dispatchers.IO) {
        runCatching {
            val claro = json.encodeToString(SessaoSalva.serializer(), sessao).encodeToByteArray()
            val cifrado = Base64.encodeToString(aead.encrypt(claro, dadosAssociados), Base64.NO_WRAP)
            contexto.cofre.edit { it[chave] = cifrado }
        }.onFailure {
            // Sem cofre, a sessão vale só enquanto o app estiver aberto.
            Log.w("CofreSessao", "Não deu para guardar a sessão.", it)
        }
        Unit
    }

    override suspend fun apagar() = withContext(Dispatchers.IO) {
        contexto.cofre.edit { it.remove(chave) }
        Unit
    }
}
