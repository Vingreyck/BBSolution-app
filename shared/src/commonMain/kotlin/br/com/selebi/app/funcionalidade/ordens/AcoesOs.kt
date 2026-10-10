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

    /** Coordenada da usina quando existe (chega na porta); senão, o endereço. */
    fun abrirRota(externas: AcoesExternas, ordem: Ordem): Boolean {
        val usina = ordem.usina
        val destino = if (usina?.latitude != null && usina.longitude != null) {
            "${usina.latitude},${usina.longitude}"
        } else {
            // Texto puro para o mapa: "Rua X, 12, Centro, Itabaiana, SE".
            listOfNotNull(ordem.cliente.endereco?.replace(" · ", ", "), ordem.cliente.cidade, ordem.cliente.uf)
                .joinToString(", ")
        }
        if (destino.isBlank()) return false
        return externas.abrirMapa(destino)
    }

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
