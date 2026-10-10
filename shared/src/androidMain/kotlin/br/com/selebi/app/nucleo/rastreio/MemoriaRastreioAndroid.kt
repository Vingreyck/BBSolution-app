package br.com.selebi.app.nucleo.rastreio

import android.content.Context
import androidx.core.content.edit
import br.com.selebi.app.nucleo.modelo.ModoRastreio

/**
 * A [MemoriaRastreio] do Android.
 *
 * SharedPreferences, e não DataStore, de propósito: o serviço precisa ler na
 * hora em que o Android o recria depois de matar o app, antes de qualquer
 * corrotina — e é pouco dado, sem nada de sigiloso além do número da OS.
 */
class MemoriaRastreioAndroid(contexto: Context) : MemoriaRastreio {
    private val prefs = contexto.getSharedPreferences("rastreio", Context.MODE_PRIVATE)

    override fun ler(): EstadoRastreio? {
        val usuarioId = prefs.getString(USUARIO, null) ?: return null
        val ordemId = prefs.getString(ORDEM, null) ?: return null
        return EstadoRastreio(
            usuarioId = usuarioId,
            ordemId = ordemId,
            numero = prefs.getInt(NUMERO, 0),
            cliente = prefs.getString(CLIENTE, null).orEmpty(),
            modo = ModoRastreio.daApi(prefs.getString(MODO, null)),
        )
    }

    override fun salvar(estado: EstadoRastreio) = prefs.edit(commit = true) {
        putString(USUARIO, estado.usuarioId)
        putString(ORDEM, estado.ordemId)
        putInt(NUMERO, estado.numero)
        putString(CLIENTE, estado.cliente)
        putString(MODO, estado.modo.api)
    }

    override fun apagar() = prefs.edit(commit = true) { clear() }

    private companion object {
        const val USUARIO = "usuario"
        const val ORDEM = "ordem"
        const val NUMERO = "numero"
        const val CLIENTE = "cliente"
        const val MODO = "modo"
    }
}
