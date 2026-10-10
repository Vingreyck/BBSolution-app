package br.com.selebi.app.nucleo.rastreio

import br.com.selebi.app.nucleo.modelo.ModoRastreio
import platform.Foundation.NSUserDefaults

/** A [MemoriaRastreio] do iPhone: as preferências do app (`NSUserDefaults`), lidas na hora. */
class MemoriaRastreioIos : MemoriaRastreio {
    private val prefs = NSUserDefaults.standardUserDefaults

    override fun ler(): EstadoRastreio? {
        val usuarioId = prefs.stringForKey(USUARIO) ?: return null
        val ordemId = prefs.stringForKey(ORDEM) ?: return null
        return EstadoRastreio(
            usuarioId = usuarioId,
            ordemId = ordemId,
            numero = prefs.integerForKey(NUMERO).toInt(),
            cliente = prefs.stringForKey(CLIENTE).orEmpty(),
            modo = ModoRastreio.daApi(prefs.stringForKey(MODO)),
        )
    }

    override fun salvar(estado: EstadoRastreio) {
        prefs.setObject(estado.usuarioId, USUARIO)
        prefs.setObject(estado.ordemId, ORDEM)
        prefs.setInteger(estado.numero.toLong(), NUMERO)
        prefs.setObject(estado.cliente, CLIENTE)
        prefs.setObject(estado.modo.api, MODO)
    }

    override fun apagar() {
        listOf(USUARIO, ORDEM, NUMERO, CLIENTE, MODO).forEach { prefs.removeObjectForKey(it) }
    }

    private companion object {
        const val USUARIO = "rastreio.usuario"
        const val ORDEM = "rastreio.ordem"
        const val NUMERO = "rastreio.numero"
        const val CLIENTE = "rastreio.cliente"
        const val MODO = "rastreio.modo"
    }
}
