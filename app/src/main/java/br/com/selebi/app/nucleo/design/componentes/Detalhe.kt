package br.com.selebi.app.nucleo.design.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Bloco com título, para agrupar informação numa tela de detalhe. */
@Composable
fun BlocoDetalhe(titulo: String, modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            conteudo()
        }
    }
}

/** Ícone, rótulo pequeno e o valor — uma linha de ficha. */
@Composable
fun ItemDetalhe(
    icone: ImageVector,
    rotulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    corValor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.bodyLarge, color = corValor)
        }
    }
}

/** Botão quadrado com ícone em cima — a fileira de ações rápidas das telas de detalhe. */
@Composable
fun AcaoRapida(
    icone: ImageVector,
    rotulo: String,
    aoClicar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitada: Boolean = true,
) {
    val esquema = MaterialTheme.colorScheme
    Surface(
        onClick = aoClicar,
        enabled = habilitada,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (habilitada) esquema.secondaryContainer else esquema.surfaceContainerHigh,
        contentColor = if (habilitada) esquema.onSecondaryContainer else esquema.onSurfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(rotulo, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
