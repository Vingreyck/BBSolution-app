package br.com.selebi.app.funcionalidade.execucao

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.design.tema.SelebiTema

/*
 * Peças comuns aos passos do wizard: a opção grande de tocar, a dica do item
 * e a barra de baixo.
 */

enum class JeitoOpcao { UNICA, VARIAS, BOTAO }

/**
 * Uma opção de resposta do tamanho de um dedo de luva: 56dp de altura, a
 * largura toda, marcada em cobre. Sol a pino na tela e mão suja de telhado
 * não acertam um radio button de 20dp.
 */
@Composable
fun OpcaoGrande(
    texto: String,
    marcada: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    jeito: JeitoOpcao = JeitoOpcao.UNICA,
    icone: ImageVector? = null,
    habilitada: Boolean = true,
) {
    val esquema = MaterialTheme.colorScheme
    val simbolo = icone ?: when (jeito) {
        JeitoOpcao.UNICA -> if (marcada) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked
        JeitoOpcao.VARIAS -> if (marcada) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank
        JeitoOpcao.BOTAO -> if (marcada) Icons.Outlined.CheckCircle else null
    }
    Surface(
        onClick = aoTocar,
        enabled = habilitada,
        shape = MaterialTheme.shapes.medium,
        color = if (marcada) esquema.primaryContainer else esquema.surfaceContainerLowest,
        contentColor = if (marcada) esquema.onPrimaryContainer else esquema.onSurface,
        border = BorderStroke(
            if (marcada) 2.dp else 1.dp,
            if (marcada) esquema.primary else esquema.outlineVariant,
        ),
        modifier = modifier
            .heightIn(min = 56.dp)
            // Travada (atendimento não iniciado): esmaecida, para não parecer que o toque falhou.
            .alpha(if (habilitada) 1f else 0.5f)
            .semantics {
                selected = marcada
                role = if (jeito == JeitoOpcao.VARIAS) Role.Checkbox else Role.RadioButton
            },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (simbolo != null) {
                Icon(
                    simbolo,
                    contentDescription = null,
                    tint = if (marcada) esquema.primary else esquema.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A dica do item — o "fotografe de perto, com o número legível". */
@Composable
fun Dica(texto: String, modifier: Modifier = Modifier) {
    val c = SelebiTema.cores
    Surface(color = c.infoFundo, contentColor = c.sobreInfo, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A barra de baixo de cada passo: o que falta (quando falta) e os botões.
 * Fica acima da barra de navegação do Android, com sombra, para não se
 * confundir com o conteúdo que rola.
 */
@Composable
fun BarraDoPasso(
    modifier: Modifier = Modifier,
    aviso: String? = null,
    avisoPositivo: Boolean = false,
    botoes: @Composable RowScope.() -> Unit,
) {
    val c = SelebiTema.cores
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (aviso != null) {
                Text(
                    aviso,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (avisoPositivo) c.sucesso else c.alerta,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) { botoes() }
        }
    }
}

/** Espaço no fim da rolagem, para o último campo não ficar debaixo da barra. */
@Composable
fun FimDaRolagem() = Box(Modifier.padding(bottom = 24.dp))
