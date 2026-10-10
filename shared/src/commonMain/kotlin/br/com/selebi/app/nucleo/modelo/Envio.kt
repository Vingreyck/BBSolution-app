package br.com.selebi.app.nucleo.modelo

import kotlinx.datetime.Instant

enum class TipoEnvio { ACAO, RESPOSTA, FOTO, ASSINATURA, NOTA }

/**
 * Uma linha da fila de envio, como a tela mostra: o que é, de qual item, e —
 * se o servidor recusou — por quê.
 */
data class Envio(
    val id: String,
    val ordemId: String,
    val tipo: TipoEnvio,
    val itemId: String?,
    val acao: AcaoOs?,
    val criadoEm: Instant,
    /** Recusa do servidor. Nulo: ainda esperando sinal. */
    val erro: String?,
) {
    val recusado: Boolean get() = erro != null
}

/** Um aviso do servidor sobre algo que subiu — "o projeto avançou para…". */
data class AvisoOs(val id: Long, val ordemId: String, val texto: String)

/** O resumo da fila para uma faixa na tela. */
data class SituacaoEnvio(val esperando: Int = 0, val recusados: Int = 0) {
    val vazia: Boolean get() = esperando == 0 && recusados == 0

    companion object {
        fun de(envios: List<Envio>) = SituacaoEnvio(
            esperando = envios.count { !it.recusado },
            recusados = envios.count { it.recusado },
        )
    }
}
