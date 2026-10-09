package br.com.selebi.app.nucleo.rede

import br.com.selebi.app.nucleo.modelo.Resposta
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * A resposta de um item viaja como JSON solto — `true`, `40`, `"Growatt"`,
 * `["A","B"]` — porque o tipo depende do item. Aqui ela vira [Resposta] e
 * volta. O banco local guarda o mesmo JSON em texto.
 */

private val json = Json

fun JsonElement?.paraResposta(): Resposta? = when (this) {
    null, JsonNull -> null
    is JsonArray -> Resposta.Lista(mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
    is JsonPrimitive -> when {
        isString -> Resposta.Texto(content)
        booleanOrNull != null -> Resposta.Marcado(booleanOrNull!!)
        doubleOrNull != null -> Resposta.Numero(doubleOrNull!!)
        else -> null
    }
    else -> null
}

fun Resposta?.paraJson(): JsonElement = when (this) {
    null -> JsonNull
    is Resposta.Marcado -> JsonPrimitive(sim)
    is Resposta.Numero -> JsonPrimitive(valor)
    is Resposta.Texto -> JsonPrimitive(valor)
    is Resposta.Lista -> JsonArray(valores.map(::JsonPrimitive))
}

/** O JSON guardado no banco local de volta para [Resposta]. */
fun respostaDoTexto(texto: String?): Resposta? =
    texto?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }.paraResposta()

fun Resposta?.paraTexto(): String? = this?.paraJson()?.toString()

private val listaDeTexto = ListSerializer(String.serializer())

fun opcoesParaTexto(opcoes: List<String>): String = json.encodeToString(listaDeTexto, opcoes)

fun opcoesDoTexto(texto: String): List<String> =
    runCatching { json.decodeFromString(listaDeTexto, texto) }.getOrDefault(emptyList())
