package br.com.selebi.app.nucleo.modelo

import br.com.selebi.app.nucleo.util.Numeros
import kotlinx.datetime.Instant
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * O rastreamento em campo — o "Acompanhar técnico" do SeeNet. As mesmas
 * regras do servidor (`src/rastreamento/trilha.ts`): se um mudar, o outro
 * muda junto, senão o site e o app marcam o mesmo técnico de jeitos diferentes.
 */

/**
 * Como o GPS trabalha. A caminho, forte e a cada 5 s — o escritório vê o
 * carro andar. No local do cliente, precisão média (Wi-Fi e antena em vez do
 * chip de GPS) a cada minuto: o técnico continua visível até concluir, e a
 * bateria aguenta o dia.
 */
enum class ModoRastreio(
    val api: String,
    /** Intervalo pedido ao GPS. */
    val intervaloMs: Long,
    /** Menor intervalo entre dois pontos guardados — o resto é o GPS repetindo. */
    val guardarACadaMs: Long,
    /** De quanto em quanto tempo o lote sobe. */
    val enviarACadaMs: Long,
) {
    DESLOCAMENTO("deslocamento", intervaloMs = 5_000, guardarACadaMs = 4_500, enviarACadaMs = 10_000),
    ECO("eco", intervaloMs = 60_000, guardarACadaMs = 45_000, enviarACadaMs = 60_000);

    companion object {
        fun daApi(valor: String?): ModoRastreio = if (valor == ECO.api) ECO else DESLOCAMENTO

        /** A caminho: modo forte; atendendo: econômico. Nas outras situações ninguém é rastreado. */
        fun para(status: StatusOs): ModoRastreio? = when (status) {
            StatusOs.EM_DESLOCAMENTO -> DESLOCAMENTO
            StatusOs.EM_ANDAMENTO -> ECO
            else -> null
        }
    }
}

data class Coordenada(val latitude: Double, val longitude: Double)

data class PosicaoCampo(
    val latitude: Double,
    val longitude: Double,
    val precisao: Double?,
    val velocidade: Double?,
    val bateria: Int?,
    val modo: ModoRastreio,
    val capturadoEm: Instant,
) {
    val coordenada: Coordenada get() = Coordenada(latitude, longitude)

    /** km/h, só quando está andando de verdade. */
    val kmh: Int? get() = velocidade?.takeIf { modo == ModoRastreio.DESLOCAMENTO && it > 1 }?.let { (it * 3.6).toInt() }
}

data class TecnicoEmCampo(
    val ordemId: String,
    val numero: Int,
    val tipo: TipoOs,
    val status: StatusOs,
    val clienteNome: String,
    val cidade: String?,
    val uf: String?,
    val telefone: String?,
    val tecnico: Pessoa?,
    /** Desde quando está a caminho, ou atendendo. */
    val desde: Instant?,
    val destino: Coordenada?,
    val posicao: PosicaoCampo?,
    val distanciaDestinoM: Int?,
) {
    val numeroFormatado: String get() = "#" + numero.toString().padStart(4, '0')
}

data class MarcoCampo(val tipo: String, val titulo: String, val coordenada: Coordenada, val ocorridoEm: Instant?)

data class TrilhaCampo(
    val trechos: List<List<Coordenada>>,
    val coladaNasRuas: Boolean,
    val marcos: List<MarcoCampo>,
    val destino: Coordenada?,
    val posicao: PosicaoCampo?,
)

enum class Frescor { AO_VIVO, ATRASADO, SEM_SINAL }

object Campo {
    /** Acima disto a leitura é tão vaga que nem a posição ao vivo aproveita. */
    const val PRECISAO_INUTIL_M = 500f

    /**
     * A posição ainda vale? No local o celular manda de minuto em minuto, e
     * os limites são folgados — senão o técnico trabalhando apareceria "sem
     * sinal".
     */
    fun frescor(capturadoEm: Instant, modo: ModoRastreio, agora: Instant): Frescor {
        val s = (agora - capturadoEm).inWholeSeconds
        val eco = modo == ModoRastreio.ECO
        return when {
            s < (if (eco) 90 else 30) -> Frescor.AO_VIVO
            s < (if (eco) 300 else 120) -> Frescor.ATRASADO
            else -> Frescor.SEM_SINAL
        }
    }

    /** "ao vivo", "há 45 s", "há 6 min · sem sinal". */
    fun textoFrescor(posicao: PosicaoCampo?, agora: Instant): String {
        if (posicao == null) return "esperando o GPS"
        val f = frescor(posicao.capturadoEm, posicao.modo, agora)
        if (f == Frescor.AO_VIVO) return "ao vivo"
        val s = (agora - posicao.capturadoEm).inWholeSeconds.coerceAtLeast(0)
        val ha = when {
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min"
            else -> "${s / 3600} h"
        }
        return "há $ha" + if (f == Frescor.SEM_SINAL) " · sem sinal" else ""
    }

    /** Distância em metros (Haversine). */
    fun distanciaMetros(a: Coordenada, b: Coordenada): Double {
        val r = 6_371_000.0
        val dLat = radianos(b.latitude - a.latitude)
        val dLon = radianos(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(radianos(a.latitude)) * cos(radianos(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    private fun radianos(graus: Double): Double = graus * PI / 180

    /** "350 m", "2,3 km". */
    fun metros(m: Int): String =
        if (m < 1000) "${(m / 10) * 10} m" else "${Numeros.umaCasa(m / 1000.0)} km"

    /**
     * Guarda este ponto? O GPS às vezes responde duas vezes no mesmo segundo,
     * e leitura com erro de centenas de metros não diz nada — nem para a
     * posição ao vivo. (A trilha tem o filtro dela, mais fino, no servidor.)
     */
    fun deveGuardar(ultimoMs: Long?, agoraMs: Long, precisao: Float?, modo: ModoRastreio): Boolean {
        if (precisao != null && precisao > PRECISAO_INUTIL_M) return false
        return ultimoMs == null || agoraMs - ultimoMs >= modo.guardarACadaMs
    }
}
