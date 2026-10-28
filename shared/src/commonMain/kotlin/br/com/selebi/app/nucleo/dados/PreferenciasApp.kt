package br.com.selebi.app.nucleo.dados

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * A empresa deste celular.
 *
 * Guardada depois da primeira tela: o técnico digita o código uma vez e daí
 * em diante vê "BB Soluções" no login. O código fica junto porque o cadastro
 * de alguém novo neste mesmo celular precisa dele — e ele não é segredo de
 * ninguém dentro da empresa.
 */
data class EmpresaLembrada(val id: String, val nome: String, val codigo: String)

/**
 * O que não é sigiloso e precisa sobreviver a fechar o app. O arquivo é de
 * cada sistema (`preferencias.preferences_pb`); a leitura é a mesma.
 */
class PreferenciasApp(private val preferencias: DataStore<Preferences>) {

    private object Chaves {
        val empresaId = stringPreferencesKey("empresa_id")
        val empresaNome = stringPreferencesKey("empresa_nome")
        val empresaCodigo = stringPreferencesKey("empresa_codigo")
        val ultimoCpf = stringPreferencesKey("ultimo_cpf")
        val sincronizadoEm = longPreferencesKey("sincronizado_em")
        val tecnicos = stringPreferencesKey("tecnicos")
    }

    val empresa: Flow<EmpresaLembrada?> = preferencias.data.map { p ->
        val id = p[Chaves.empresaId]
        val nome = p[Chaves.empresaNome]
        if (id != null && nome != null) EmpresaLembrada(id, nome, p[Chaves.empresaCodigo].orEmpty()) else null
    }

    suspend fun lembrarEmpresa(empresa: EmpresaLembrada) {
        preferencias.edit {
            it[Chaves.empresaId] = empresa.id
            it[Chaves.empresaNome] = empresa.nome
            it[Chaves.empresaCodigo] = empresa.codigo
        }
    }

    suspend fun esquecerEmpresa() {
        preferencias.edit {
            it.remove(Chaves.empresaId)
            it.remove(Chaves.empresaNome)
            it.remove(Chaves.empresaCodigo)
            it.remove(Chaves.ultimoCpf)
        }
    }

    /** Pré-preenche o login: quase todo celular é de uma pessoa só. */
    val ultimoCpf: Flow<String?> = preferencias.data.map { it[Chaves.ultimoCpf] }

    suspend fun lembrarCpf(cpf: String) {
        preferencias.edit { it[Chaves.ultimoCpf] = cpf }
    }

    /** A conta foi excluída: o CPF não fica pré-preenchido no login. */
    suspend fun esquecerCpf() {
        preferencias.edit { it.remove(Chaves.ultimoCpf) }
    }

    val sincronizadoEm: Flow<Instant?> = preferencias.data.map { p ->
        p[Chaves.sincronizadoEm]?.let(Instant::fromEpochMilliseconds)
    }

    suspend fun marcarSincronizacao(instante: Instant) {
        preferencias.edit { it[Chaves.sincronizadoEm] = instante.toEpochMilliseconds() }
    }

    /**
     * Os técnicos que podem ser escolhidos para uma visita de volta — guardados
     * para valer sem sinal: a vistoria acaba na casa do cliente, onde o sinal
     * nem sempre chega. Texto simples, `id<TAB>nome` por linha.
     */
    val tecnicos: Flow<List<Pair<String, String>>> = preferencias.data.map { p ->
        p[Chaves.tecnicos].orEmpty().lines().mapNotNull { linha ->
            val partes = linha.split('\t', limit = 2)
            if (partes.size == 2 && partes[0].isNotBlank()) partes[0] to partes[1] else null
        }
    }

    suspend fun guardarTecnicos(lista: List<Pair<String, String>>) {
        preferencias.edit { it[Chaves.tecnicos] = lista.joinToString("\n") { (id, nome) -> id + "\t" + nome.replace('\n', ' ').replace('\t', ' ') } }
    }

    suspend fun esquecerSincronizacao() {
        preferencias.edit {
            it.remove(Chaves.sincronizadoEm)
            it.remove(Chaves.tecnicos)
        }
    }
}
