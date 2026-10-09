package br.com.selebi.app.nucleo.rede

import android.os.Build
import br.com.selebi.app.BuildConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O token da sessão, em memória.
 *
 * A cópia durável fica cifrada no [br.com.selebi.app.nucleo.dados.CofreSessao];
 * esta é a que cada requisição usa, sem ir ao disco.
 */
@Singleton
class PortadorToken @Inject constructor() {
    @Volatile
    var token: String? = null
}

/** Avisa o resto do app que o servidor encerrou a sessão. */
@Singleton
class EventosSessao @Inject constructor() {
    private val _expirou = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val expirou: SharedFlow<Unit> = _expirou.asSharedFlow()

    fun avisarExpirou() {
        _expirou.tryEmit(Unit)
    }
}

/** "Xiaomi Redmi Note 12 · Android 14" — para a pessoa reconhecer a sessão. */
object Dispositivo {
    fun descricao(): String {
        val fabricante = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val modelo = Build.MODEL.orEmpty()
        val nome = if (modelo.startsWith(fabricante, ignoreCase = true)) modelo else "$fabricante $modelo"
        return "${nome.trim()} · Android ${Build.VERSION.RELEASE}"
    }
}

/**
 * Põe o token em toda requisição e percebe quando a sessão acabou.
 *
 * Só um 401 **com token** manda para o login — é a resposta do servidor
 * dizendo "essa sessão não vale mais" (desativado, senha redefinida, 60 dias
 * sem uso). Falta de sinal, 500, timeout: nada disso desloga ninguém. O SeeNet
 * aprendeu a diferença depois de muito técnico reclamar que "pede login toda
 * hora".
 */
class InterceptadorAutenticacao @Inject constructor(
    private val portador: PortadorToken,
    private val eventos: EventosSessao,
) : Interceptor {

    private val agente = "Selebi-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = portador.token
        val pedido = chain.request().newBuilder()
            .header("User-Agent", agente)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

        val resposta = chain.proceed(pedido)
        if (resposta.code == 401 && token != null && !pedido.url.encodedPath.endsWith("/sessao")) {
            eventos.avisarExpirou()
        }
        return resposta
    }
}
