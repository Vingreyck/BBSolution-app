package br.com.selebi.app.funcionalidade.ordens

import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.plataforma.AcoesExternas
import br.com.selebi.app.nucleo.util.codificarUrl

/**
 * Ligar, traçar rota, chamar no WhatsApp — sai do app para o app certo.
 *
 * Nada de checar antes se existe app que abre: no Android 11+ essa checagem
 * falha mesmo quando o app existe, e foi assim que o SeeNet ficou com "Não foi
 * possível abrir o discador". Tenta abrir e trata a falha.
 */
object AcoesOs {

    /** Coordenada da usina quando é confiável (chega na porta); senão, o endereço do cliente. */
    fun abrirRota(externas: AcoesExternas, ordem: Ordem): Boolean {
        val destino = destinoDaRota(ordem)
        if (destino.isBlank()) return false
        return externas.abrirMapa(destino)
    }

    /**
     * O que o mapa recebe como destino.
     *
     * A coordenada só vale dentro do Brasil e fora do "0,0" que o portal manda
     * quando não sabe. O endereço sai em texto puro, do jeito que o Google entende:
     * "Rua X, 12, Centro, Itabaiana, SE, Brasil", sem o " · " do envelope e sem o
     * "S/N", que só faz o pino cair no meio da rua.
     */
    fun destinoDaRota(ordem: Ordem): String {
        val usina = ordem.usina
        val latitude = usina?.latitude
        val longitude = usina?.longitude
        if (latitude != null && longitude != null && coordenadaValida(latitude, longitude)) {
            return "$latitude,$longitude"
        }
        val rua = ordem.cliente.endereco
            ?.replace(" · ", ", ")
            ?.replace(Regex(",\\s*[sS]\\s*/?\\s*[nN]\\b"), "")
            ?.trim()
        val partes = listOfNotNull(rua, ordem.cliente.cidade, ordem.cliente.uf)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (partes.isEmpty()) "" else (partes + "Brasil").joinToString(", ")
    }

    private fun coordenadaValida(latitude: Double, longitude: Double): Boolean =
        !(latitude == 0.0 && longitude == 0.0) &&
            latitude in -34.0..6.0 &&
            longitude in -74.0..-34.0

    fun ligar(externas: AcoesExternas, telefone: String): Boolean = externas.discar(telefone)

    /** Abre a conversa com o cliente; com `texto`, a mensagem já vem escrita — é só enviar. */
    fun whatsapp(externas: AcoesExternas, telefone: String, texto: String? = null): Boolean {
        val numero = if (telefone.length in 10..11) "55$telefone" else telefone
        val endereco = "https://wa.me/$numero" + (texto?.let { "?text=" + codificarUrl(it) } ?: "")
        return externas.abrirLink(endereco)
    }

    fun compartilhar(externas: AcoesExternas, texto: String, titulo: String): Boolean =
        externas.compartilhar(texto, titulo)

    fun abrirLink(externas: AcoesExternas, url: String): Boolean = externas.abrirLink(url)
}
