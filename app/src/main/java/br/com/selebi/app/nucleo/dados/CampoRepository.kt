package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.BuildConfig
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
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
@Singleton
class CampoRepository @Inject constructor(
    private val api: SelebiApi,
    private val executor: ExecutorApi,
    private val json: Json,
    cliente: OkHttpClient,
) {
    // O fluxo fica aberto: sem teto de leitura curto (o servidor manda um ping
    // a cada 25 s, então 70 s sem nada é conexão morta).
    private val clienteAoVivo = cliente.newBuilder().readTimeout(70, TimeUnit.SECONDS).build()

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

    private fun conexao(aoReceber: (EventoCampo) -> Unit): Flow<EventoCampo> = callbackFlow {
        val pedido = Request.Builder()
            .url(BuildConfig.API_URL + "acompanhamento/stream")
            .header("Accept", "text/event-stream")
            .build()
        val ouvinte = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val evento = when (type) {
                    "pronto" -> EventoCampo.Conectado
                    "os" -> EventoCampo.MudouOs
                    "posicao" -> runCatching { json.decodeFromString(EventoPosicaoDto.serializer(), data) }
                        .getOrNull()
                        ?.let { EventoCampo.Posicao(it.ordemId, posicaoDoEvento(it)) }
                    else -> null
                } ?: return
                aoReceber(evento)
                trySend(evento)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close()
            }
        }
        val fonte = EventSources.createFactory(clienteAoVivo).newEventSource(pedido, ouvinte)
        awaitClose { fonte.cancel() }
    }

    private fun coordenada(c: CoordenadaDto) = Coordenada(c.latitude, c.longitude)

    private fun posicao(p: PosicaoAoVivoDto) = PosicaoCampo(
        latitude = p.latitude,
        longitude = p.longitude,
        precisao = p.precisao,
        velocidade = p.velocidade,
        bateria = p.bateria,
        modo = ModoRastreio.daApi(p.modo),
        capturadoEm = Datas.lerInstante(p.capturadoEm) ?: Instant.now(),
    )

    private fun posicaoDoEvento(e: EventoPosicaoDto) = PosicaoCampo(
        latitude = e.latitude,
        longitude = e.longitude,
        precisao = e.precisao,
        velocidade = e.velocidade,
        bateria = e.bateria,
        modo = ModoRastreio.daApi(e.modo),
        capturadoEm = Datas.lerInstante(e.capturadoEm) ?: Instant.now(),
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
