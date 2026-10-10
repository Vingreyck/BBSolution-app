package br.com.selebi.app.nucleo.rede

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

/**
 * O servidor respondeu com erro. `corpo` é o JSON `{ erro, mensagem, campos }`
 * que as rotas do Selebi devolvem.
 */
class ErroHttp(val status: Int, val corpo: String?) : Exception("HTTP $status")

/** Transforma exceção de rede em [Resultado], para nenhuma tela precisar de try/catch. */
class ExecutorApi(private val json: Json) {

    suspend fun <T> executar(bloco: suspend () -> T): Resultado<T> = try {
        Resultado.Ok(bloco())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ErroHttp) {
        Resultado.Falha(erroHttp(e))
    } catch (e: Exception) {
        Resultado.Falha(classificar(e))
    }

    private fun erroHttp(e: ErroHttp): ErroApp {
        val dto = e.corpo?.let { runCatching { json.decodeFromString(ErroDto.serializer(), it) }.getOrNull() }
        return ErroApp.Servidor(
            status = e.status,
            codigo = dto?.erro ?: "http_${e.status}",
            mensagem = dto?.mensagem ?: "O servidor respondeu com erro (${e.status}).",
            campos = dto?.campos.orEmpty(),
        )
    }

    private fun classificar(e: Exception): ErroApp = when {
        e is HttpRequestTimeoutException || e is SocketTimeoutException || e is ConnectTimeoutException ->
            ErroApp.TempoEsgotado
        else -> falhaDeRede(e) ?: if (e is IOException) ErroApp.SemConexao else ErroApp.Inesperado()
    }
}

/**
 * O jeito de cada sistema dizer "sem sinal" ou "demorou demais": o Android
 * fala por exceções do Java; o iPhone, por códigos de erro do `NSURLSession`.
 * Nulo quando a exceção não é de rede.
 */
internal expect fun falhaDeRede(e: Throwable): ErroApp?
