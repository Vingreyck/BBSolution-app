package br.com.selebi.app.nucleo.util

import br.com.selebi.app.BuildConfig
import java.net.URI

/** Endereços do Selebi fora da API — o relatório público, por exemplo. */
object Enderecos {

    /**
     * `/r/abc…` → `https://servidor/r/abc…`, no mesmo servidor da API. Quando a
     * BB tiver domínio próprio, o endereço muda junto com o da API.
     */
    fun publico(caminho: String, base: String = BuildConfig.API_URL): String {
        val uri = URI(base)
        val porta = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://${uri.host}$porta/${caminho.trimStart('/')}"
    }
}
