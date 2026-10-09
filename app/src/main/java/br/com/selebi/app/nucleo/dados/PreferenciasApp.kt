package br.com.selebi.app.nucleo.dados

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferencias by preferencesDataStore(name = "preferencias")

/**
 * A empresa deste celular.
 *
 * Guardada depois da primeira tela: o técnico digita o código uma vez e daí
 * em diante vê "BB Soluções" no login. O código fica junto porque o cadastro
 * de alguém novo neste mesmo celular precisa dele — e ele não é segredo de
 * ninguém dentro da empresa.
 */
data class EmpresaLembrada(val id: String, val nome: String, val codigo: String)

/** O que não é sigiloso e precisa sobreviver a fechar o app. */
@Singleton
class PreferenciasApp @Inject constructor(@ApplicationContext private val contexto: Context) {

    private object Chaves {
        val empresaId = stringPreferencesKey("empresa_id")
        val empresaNome = stringPreferencesKey("empresa_nome")
        val empresaCodigo = stringPreferencesKey("empresa_codigo")
        val ultimoCpf = stringPreferencesKey("ultimo_cpf")
        val sincronizadoEm = longPreferencesKey("sincronizado_em")
    }

    val empresa: Flow<EmpresaLembrada?> = contexto.preferencias.data.map { p ->
        val id = p[Chaves.empresaId]
        val nome = p[Chaves.empresaNome]
        if (id != null && nome != null) EmpresaLembrada(id, nome, p[Chaves.empresaCodigo].orEmpty()) else null
    }

    suspend fun lembrarEmpresa(empresa: EmpresaLembrada) {
        contexto.preferencias.edit {
            it[Chaves.empresaId] = empresa.id
            it[Chaves.empresaNome] = empresa.nome
            it[Chaves.empresaCodigo] = empresa.codigo
        }
    }

    suspend fun esquecerEmpresa() {
        contexto.preferencias.edit {
            it.remove(Chaves.empresaId)
            it.remove(Chaves.empresaNome)
            it.remove(Chaves.empresaCodigo)
            it.remove(Chaves.ultimoCpf)
        }
    }

    /** Pré-preenche o login: quase todo celular é de uma pessoa só. */
    val ultimoCpf: Flow<String?> = contexto.preferencias.data.map { it[Chaves.ultimoCpf] }

    suspend fun lembrarCpf(cpf: String) {
        contexto.preferencias.edit { it[Chaves.ultimoCpf] = cpf }
    }

    val sincronizadoEm: Flow<Instant?> = contexto.preferencias.data.map { p ->
        p[Chaves.sincronizadoEm]?.let(Instant::ofEpochMilli)
    }

    suspend fun marcarSincronizacao(instante: Instant) {
        contexto.preferencias.edit { it[Chaves.sincronizadoEm] = instante.toEpochMilli() }
    }

    suspend fun esquecerSincronizacao() {
        contexto.preferencias.edit { it.remove(Chaves.sincronizadoEm) }
    }
}
