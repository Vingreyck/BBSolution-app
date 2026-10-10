package br.com.selebi.app.funcionalidade.campo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import br.com.selebi.app.nucleo.modelo.Campo
import br.com.selebi.app.nucleo.modelo.Frescor
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TecnicoEmCampo
import br.com.selebi.app.nucleo.modelo.TrilhaCampo
import kotlinx.datetime.Instant

/*
 * O mapa do campo. Os pinos são as iniciais do técnico na cor da situação —
 * cobre a caminho, verde no local, cinza sem sinal —, e o escolhido ganha o
 * trajeto. No Android é o OpenStreetMap (osmdroid), grátis e sem chave, o
 * mesmo mapa do site; no iPhone, o mapa da Apple.
 */

internal const val LATITUDE_SERGIPE = -10.75
internal const val LONGITUDE_SERGIPE = -37.3
internal const val COR_TRILHA = 0xFF2F6FB0

internal enum class Tom(val cor: Long) {
    CAMINHO(0xFFA8521F),
    LOCAL(0xFF2C6E49),
    SEM_SINAL(0xFF6E7A85),
}

internal fun tomDe(t: TecnicoEmCampo, agora: Instant): Tom {
    val p = t.posicao ?: return Tom.SEM_SINAL
    if (Campo.frescor(p.capturadoEm, p.modo, agora) == Frescor.SEM_SINAL) return Tom.SEM_SINAL
    return if (t.status == StatusOs.EM_DESLOCAMENTO) Tom.CAMINHO else Tom.LOCAL
}

/**
 * @param enquadrar muda quando o mapa deve mostrar todo mundo (botão "Ver todos").
 * @param mostrarMapa falso nas capturas de tela: o mapa precisa de rede e de
 *   ladrilhos, e lá fica só o lugar dele.
 */
@Composable
fun MapaCampo(
    tecnicos: List<TecnicoEmCampo>,
    selecionado: String?,
    trilha: TrilhaCampo?,
    agora: Instant,
    enquadrar: Int,
    aoEscolher: (String) -> Unit,
    modifier: Modifier = Modifier,
    mostrarMapa: Boolean = true,
) {
    if (!mostrarMapa) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Map, contentDescription = "Mapa", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    MapaNativo(tecnicos, selecionado, trilha, agora, enquadrar, aoEscolher, modifier)
}

/** O mapa do sistema, com os pinos e o trajeto do escolhido. */
@Composable
internal expect fun MapaNativo(
    tecnicos: List<TecnicoEmCampo>,
    selecionado: String?,
    trilha: TrilhaCampo?,
    agora: Instant,
    enquadrar: Int,
    aoEscolher: (String) -> Unit,
    modifier: Modifier,
)
