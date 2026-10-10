package br.com.selebi.app.nucleo.rede

import kotlinx.serialization.json.Json

/** O leitor de JSON do app inteiro: campo novo no servidor não quebra nada. */
object ModuloRede {
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }
}
