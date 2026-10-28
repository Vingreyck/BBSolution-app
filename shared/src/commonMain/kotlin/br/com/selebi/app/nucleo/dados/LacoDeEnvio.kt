package br.com.selebi.app.nucleo.dados

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * O que sobe a fila no iPhone, e quando.
 *
 * Quem pede ([pedir]) é qualquer coisa que torna o envio útil: uma foto ou
 * resposta nova, o app voltar para a frente, o "Tentar agora", a rede voltar.
 * Cada pedido faz **uma rodada**. Se a rodada acaba bem, o laço espera o
 * próximo pedido e não faz mais nada — sem consulta em segundo plano.
 *
 * Se a rodada falha por sinal ou por erro do servidor, tenta de novo com espera
 * crescente (30 s, 1 min, 2 min… até 15 min), e a espera **acaba na hora** se
 * pedirem de novo ou se a rede voltar depois de ter caído. Antes, quando a
 * internet estava ligada e o servidor dava erro, o app tentava a cada 2
 * segundos, sem parar.
 *
 * Os pedidos entram num canal de uma vaga só: dez fotos tiradas sem sinal são
 * um pedido, não dez.
 *
 * Fica no código comum, e não no do iPhone, para o `LacoDeEnvioTest` conferir o
 * tempo entre as tentativas.
 */
class LacoDeEnvio(
    private val escopo: CoroutineScope,
    /** Tem rede agora? Emite o valor de agora e cada mudança. */
    private val rede: Flow<Boolean>,
    /** Uma rodada de envio, de ponta a ponta. */
    private val envioUmaVez: suspend () -> ResultadoEnvio,
    private val esperaInicialMs: Long = 30_000,
    private val esperaMaximaMs: Long = 15 * 60_000,
) {
    private val pedidos = Channel<Unit>(Channel.CONFLATED)

    fun pedir() {
        pedidos.trySend(Unit)
    }

    /** Começa a atender os pedidos. */
    fun iniciar() {
        escopo.launch {
            while (true) {
                pedidos.receive()
                var espera = esperaInicialMs
                while (envioUmaVez() == ResultadoEnvio.TENTAR_DE_NOVO) {
                    dormir(espera)
                    espera = minOf(espera * 2, esperaMaximaMs)
                }
            }
        }
        // Rede voltou: o que estava parado sobe. O primeiro valor é só "como a rede
        // está agora", não uma volta: quem pede o envio na abertura é o login.
        escopo.launch {
            rede.drop(1).collect { temRede -> if (temRede) pedir() }
        }
    }

    /** Espera o tempo todo, ou menos se pedirem de novo ou a rede voltar. */
    private suspend fun dormir(espera: Long) {
        withTimeoutOrNull(espera) {
            merge(pedidos.receiveAsFlow(), redeVoltou()).first()
        }
        // O que acordou já vai valer para a rodada: sem sobra para uma segunda,
        // colada na primeira.
        pedidos.tryReceive()
    }

    /** Só dispara quando a rede **volta** depois de cair — estar ligada não conta. */
    private fun redeVoltou(): Flow<Unit> = rede.dropWhile { it }.filter { it }.map { }
}
