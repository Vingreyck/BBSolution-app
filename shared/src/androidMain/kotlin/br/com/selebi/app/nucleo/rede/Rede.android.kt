package br.com.selebi.app.nucleo.rede

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.io.IOException
import java.net.SocketTimeoutException

internal actual fun clienteHttp(configurar: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) { configurar() }

internal actual fun falhaDeRede(e: Throwable): ErroApp? = when (e) {
    is SocketTimeoutException -> ErroApp.TempoEsgotado
    is IOException -> ErroApp.SemConexao
    else -> null
}
