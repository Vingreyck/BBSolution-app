package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.modelo.Coordenada
import br.com.selebi.app.nucleo.modelo.MarcoCampo
import br.com.selebi.app.nucleo.modelo.ModoRastreio
import br.com.selebi.app.nucleo.modelo.Pessoa
import br.com.selebi.app.nucleo.modelo.PosicaoCampo
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TecnicoEmCampo
import br.com.selebi.app.nucleo.modelo.TipoOs
import br.com.selebi.app.nucleo.modelo.TrilhaCampo
import br.com.selebi.app.nucleo.rede.CoordenadaDto
import br.com.selebi.app.nucleo.rede.EventoPosicaoDto
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.PosicaoAoVivoDto
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import br.com.selebi.app.nucleo.rede.TecnicoEmCampoDto
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant
import kotlin.random.Random

/** O que chega pelo fluxo ao vivo. */
sealed interface EventoCampo {
    data object Conectado : EventoCampo

    data class Posicao(val ordemId: String, val posicao: PosicaoCampo) : EventoCampo

    /** Uma OS mudou de situação: a lista vem de novo. */
    data object MudouOs : EventoCampo

    data object Caiu : EventoCampo
}

/**
 * O campo para quem é da gestão no app: quem está em campo, o trajeto de
 * cada um, e as posições ao vivo.
 *
 * Sem cópia local de propósito, como a equipe: posição velha num mapa é pior
 * que mapa nenhum. O ao vivo é o mesmo fluxo do site (SSE) e reconecta
 * sozinho, com espera crescente e um sorteio no meio — num reinício do
 * servidor, os celulares não voltam todos no mesmo segundo (a lição do SeeNet).
 */
class CampoRepository(
    private val api: SelebiApi,
    private val executor: ExecutorApi,
    private val json: Json,
) {

    suspend fun carregar(): Resultado<List<TecnicoEmCampo>> =
        when (val r = executor.executar { api.acompanhamento() }) {
            is Resultado.Ok -> Resultado.Ok(r.valor.tecnicos.map(::paraModelo))
            is Resultado.Falha -> r
        }

    suspend fun trilha(ordemId: String): Resultado<TrilhaCampo> =
        when (val r = executor.executar { api.trilha(ordemId) }) {
            is Resultado.Ok -> Resultado.Ok(
                TrilhaCampo(
                    trechos = r.valor.trechos.map { t -> t.map(::coordenada) },
                    coladaNasRuas = r.valor.coladaNasRuas,
                    marcos = r.valor.marcos.map {
                        MarcoCampo(it.tipo, it.titulo, Coordenada(it.latitude, it.longitude), Datas.lerInstante(it.ocorridoEm))
                    },
                    destino = r.valor.destino?.let(::coordenada),
                    posicao = r.valor.posicao?.let(::posicao),
                ),
            )
            is Resultado.Falha -> r
        }

    /** As posições ao vivo, para sempre — reconectando quando cair. */
    fun aoVivo(): Flow<EventoCampo> = flow {
        var espera = 2_000L
        while (true) {
            var recebeu = false
            emitAll(
                conexao { evento ->
                    if (evento is EventoCampo.Conectado) recebeu = true
                },
            )
            emit(EventoCampo.Caiu)
            // Conectou e depois caiu: recomeça rápido. Nem conectou: espera mais.
            espera = if (recebeu) 2_000L else (espera * 2).coerceAtMost(60_000L)
            delay(espera + Random.nextLong(espera / 2 + 1))
        }
    }

    private fun conexao(aoReceber: (EventoCampo) -> Unit): Flow<EventoCampo> =
        api.acompanhamentoAoVivo()
            .mapNotNull { e ->
                when (e.tipo) {
                    "pronto" -> EventoCampo.Conectado
                    "os" -> EventoCampo.MudouOs
                    "posicao" -> runCatching { json.decodeFromString(EventoPosicaoDto.serializer(), e.dados) }
                        .getOrNull()
                        ?.let { EventoCampo.Posicao(it.ordemId, posicaoDoEvento(it)) }
                    else -> null
                }
            }
            .onEach(aoReceber)
            // Queda, 401, servidor reiniciando: o laço de [aoVivo] reconecta.
            .catch { }

    private fun coordenada(c: CoordenadaDto) = Coordenada(c.latitude, c.longitude)

    private fun posicao(p: PosicaoAoVivoDto) = PosicaoCampo(
        latitude = p.latitude,
        longitude = p.longitude,
        precisao = p.precisao,
        velocidade = p.velocidade,
        bateria = p.bateria,
        modo = ModoRastreio.daApi(p.modo),
        capturadoEm = Datas.lerInstante(p.capturadoEm) ?: Datas.agoraMesmo(),
    )

    private fun posicaoDoEvento(e: EventoPosicaoDto) = PosicaoCampo(
        latitude = e.latitude,
        longitude = e.longitude,
        precisao = e.precisao,
        velocidade = e.velocidade,
        bateria = e.bateria,
        modo = ModoRastreio.daApi(e.modo),
        capturadoEm = Datas.lerInstante(e.capturadoEm) ?: Datas.agoraMesmo(),
    )

    private fun paraModelo(t: TecnicoEmCampoDto) = TecnicoEmCampo(
        ordemId = t.ordemId,
        numero = t.numero,
        tipo = TipoOs.daApi(t.tipo),
        status = StatusOs.daApi(t.status),
        clienteNome = t.cliente.nome,
        cidade = t.cliente.cidade,
        uf = t.cliente.uf,
        telefone = t.cliente.telefone,
        tecnico = t.tecnico?.let { Pessoa(it.id, it.nome) },
        desde = Datas.lerInstante(t.desde),
        destino = t.destino?.let(::coordenada),
        posicao = t.posicao?.let(::posicao),
        distanciaDestinoM = t.distanciaDestinoM,
    )
}
