package br.com.selebi.app.nucleo.dados

/** Um ponto no mapa, com a margem de erro em metros. */
data class Ponto(val latitude: Double, val longitude: Double, val precisao: Double?)

/**
 * Onde o técnico está, na hora de uma ação — é o "no mapa" do histórico da OS
 * no site, como o IXC faz com o check-in.
 *
 * Nunca trava o trabalho: sem permissão, com o GPS desligado ou sem resposta
 * em alguns segundos, a ação segue sem o ponto.
 */
interface Localizacao {
    fun temPermissao(): Boolean

    /** O ponto de agora, ou o último conhecido de até 10 minutos atrás. */
    suspend fun atual(limiteMs: Long = 8_000): Ponto?
}
