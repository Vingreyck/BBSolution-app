package br.com.selebi.app.nucleo.design.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Papel
import br.com.selebi.app.nucleo.util.iniciais

/** Botão de ação principal: largo, 56dp de altura, com estado "carregando". */
@Composable
fun BotaoPrincipal(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    carregando: Boolean = false,
    habilitado: Boolean = true,
    icone: ImageVector? = null,
) {
    Button(
        onClick = aoClicar,
        enabled = habilitado && !carregando,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (carregando) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            if (icone != null) {
                Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
                Box(Modifier.size(8.dp))
            }
            Text(texto, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun BotaoSecundario(
    texto: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
) {
    OutlinedButton(
        onClick = aoClicar,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (icone != null) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

enum class TipoAviso { ERRO, ALERTA, INFO, ESPERA }

/** Caixa de aviso dentro da tela — erro do formulário, cadastro esperando, etc. */
@Composable
fun Aviso(texto: String, tipo: TipoAviso, modifier: Modifier = Modifier, titulo: String? = null) {
    val c = SelebiTema.cores
    val (fundo, frente, icone) = when (tipo) {
        TipoAviso.ERRO -> Triple(c.perigoFundo, c.sobrePerigo, Icons.Outlined.ErrorOutline)
        TipoAviso.ALERTA -> Triple(c.alertaFundo, c.sobreAlerta, Icons.Outlined.Info)
        TipoAviso.INFO -> Triple(c.infoFundo, c.sobreInfo, Icons.Outlined.Info)
        TipoAviso.ESPERA -> Triple(c.alertaFundo, c.sobreAlerta, Icons.Outlined.HourglassTop)
    }
    Surface(color = fundo, contentColor = frente, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (titulo != null) Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(texto, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "Sem conexão — mostrando o que foi salvo às 14:32." */
@Composable
fun FaixaOffline(desde: String?, modifier: Modifier = Modifier) {
    val c = SelebiTema.cores
    Surface(color = c.neutroFundo, contentColor = c.sobreNeutro, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (desde != null) "Sem conexão · mostrando o que foi salvo em $desde" else "Sem conexão com a internet",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Tela sem nada para mostrar — sempre dizendo o que fazer, e não só "vazio". */
@Composable
fun EstadoVazio(
    icone: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acao: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
        }
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (acao != null) Box(Modifier.padding(top = 6.dp)) { acao() }
    }
}

/** A cor de cada papel — a mesma convenção do SeeNet: adm laranja, técnico verde. */
@Composable
fun corDoPapel(papel: Papel): Color = when (papel) {
    Papel.ADM -> MaterialTheme.colorScheme.primary
    Papel.ENGENHEIRO -> SelebiTema.cores.info
    Papel.TECNICO -> SelebiTema.cores.sucesso
    Papel.VENDEDOR -> SelebiTema.cores.sucesso
    Papel.OUTRO -> MaterialTheme.colorScheme.outline
}

/** Iniciais num círculo com anel na cor do papel. */
@Composable
fun Avatar(nome: String, papel: Papel, modifier: Modifier = Modifier, tamanho: Dp = 44.dp) {
    val cor = corDoPapel(papel)
    Box(
        modifier
            .size(tamanho)
            .border(BorderStroke(2.dp, cor), CircleShape)
            .padding(3.dp)
            .background(cor.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            iniciais(nome),
            style = if (tamanho >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            color = cor,
        )
    }
}

/** Pílula com o nome do papel. */
@Composable
fun SeloPapel(papel: Papel, modifier: Modifier = Modifier) {
    val cor = corDoPapel(papel)
    Surface(color = cor.copy(alpha = 0.14f), contentColor = cor, shape = CircleShape, modifier = modifier) {
        Text(papel.rotulo, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Pílula genérica: fundo suave, texto forte. */
@Composable
fun Pilula(texto: String, fundo: Color, frente: Color, modifier: Modifier = Modifier, icone: ImageVector? = null) {
    Surface(color = fundo, contentColor = frente, shape = CircleShape, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icone != null) Icon(icone, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(texto, style = MaterialTheme.typography.labelMedium)
        }
    }
}
