package br.com.selebi.app.nucleo.rede

import kotlinx.serialization.Serializable

/*
 * O rastreamento em campo, como trafega com o Selebi — espelho de
 * `src/rastreamento/` e das rotas `posicoes`, `acompanhamento` e
 * `ordens/{id}/trilha` do servidor.
 */

/** Um ponto do GPS do técnico em atendimento. */
@Serializable
data class PontoPedido(
    val ordemId: String,
    val latitude: Double,
    val longitude: Double,
    val precisao: Double? = null,
    val velocidade: Double? = null,
    val bateria: Int? = null,
    val modo: String,
    val capturadoEm: String,
)

@Serializable
data class PosicoesPedido(val pontos: List<PontoPedido>)

@Serializable
data class PosicoesResposta(
    val recebidos: Int = 0,
    val aceitos: Int = 0,
    val naTrilha: Int = 0,
    val descartados: Int = 0,
)

@Serializable
data class CoordenadaDto(val latitude: Double, val longitude: Double)

@Serializable
data class PosicaoAoVivoDto(
    val latitude: Double,
    val longitude: Double,
    val precisao: Double? = null,
    val velocidade: Double? = null,
    val bateria: Int? = null,
    val modo: String = "deslocamento",
    val capturadoEm: String,
)

@Serializable
data class ClienteCampoDto(
    val nome: String,
    val endereco: String? = null,
    val cidade: String? = null,
    val uf: String? = null,
    val telefone: String? = null,
)

@Serializable
data class TecnicoEmCampoDto(
    val ordemId: String,
    val numero: Int,
    val tipo: String,
    val status: String,
    val prioridade: String = "normal",
    val cliente: ClienteCampoDto,
    val tecnico: PessoaDto? = null,
    val desde: String? = null,
    val destino: CoordenadaDto? = null,
    val posicao: PosicaoAoVivoDto? = null,
    val distanciaDestinoM: Int? = null,
)

@Serializable
data class AcompanhamentoResposta(val tecnicos: List<TecnicoEmCampoDto> = emptyList(), val geradoEm: String? = null)

@Serializable
data class MarcoDto(
    val tipo: String,
    val titulo: String,
    val latitude: Double,
    val longitude: Double,
    val ocorridoEm: String? = null,
    val precisao: Int? = null,
)

@Serializable
data class TrilhaDto(
    val ordemId: String,
    val trechos: List<List<CoordenadaDto>> = emptyList(),
    val coladaNasRuas: Boolean = false,
    val marcos: List<MarcoDto> = emptyList(),
    val destino: CoordenadaDto? = null,
    val posicao: PosicaoAoVivoDto? = null,
)

/** O que chega pelo fluxo ao vivo (`event: posicao`). */
@Serializable
data class EventoPosicaoDto(
    val ordemId: String,
    val usuarioId: String,
    val latitude: Double,
    val longitude: Double,
    val precisao: Double? = null,
    val velocidade: Double? = null,
    val bateria: Int? = null,
    val modo: String = "deslocamento",
    val capturadoEm: String,
)
