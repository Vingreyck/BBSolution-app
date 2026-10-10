package br.com.selebi.app.nucleo.rede

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorTimedOut

internal actual fun clienteHttp(configurar: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin) { configurar() }

/**
 * O `NSURLSession` diz o que houve por um código: -1001 é "demorou demais";
 * o resto (sem internet, sem DNS, conexão caiu) é falta de sinal para o técnico.
 */
internal actual fun falhaDeRede(e: Throwable): ErroApp? {
    val erro = (e as? DarwinHttpRequestException)?.origin ?: return null
    return if (erro.code == NSURLErrorTimedOut) ErroApp.TempoEsgotado else ErroApp.SemConexao
}
