package br.com.selebi.app.nucleo.rede

import kotlin.concurrent.Volatile
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * O token da sessão, em memória.
 *
 * A cópia durável fica cifrada no [br.com.selebi.app.nucleo.dados.CofreSessao];
 * esta é a que cada requisição usa, sem ir ao disco.
 */
class PortadorToken {
    @Volatile
    var token: String? = null
}

/** Avisa o resto do app que o servidor encerrou a sessão. */
class EventosSessao {
    private val _expirou = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val expirou: SharedFlow<Unit> = _expirou.asSharedFlow()

    fun avisarExpirou() {
        _expirou.tryEmit(Unit)
    }
}
