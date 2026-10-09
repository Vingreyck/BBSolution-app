package br.com.selebi.app.nucleo.design.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * O sol do Selebi, o mesmo desenho do ícone do app — feito em Canvas para
 * ficar nítido em qualquer tamanho sem precisar de imagem.
 */
@Composable
fun SolSelebi(modifier: Modifier = Modifier, cor: Color = Color.White) {
    Canvas(modifier) {
        val centro = Offset(size.width / 2, size.height / 2)
        val unidade = size.minDimension / 66f // a zona segura do ícone tem 66 unidades
        drawCircle(cor, radius = 11f * unidade, center = centro)
        repeat(8) { i ->
            val angulo = Math.toRadians(i * 45.0)
            val dx = cos(angulo).toFloat()
            val dy = sin(angulo).toFloat()
            drawLine(
                color = cor,
                start = Offset(centro.x + dx * 16f * unidade, centro.y + dy * 16f * unidade),
                end = Offset(centro.x + dx * 23f * unidade, centro.y + dy * 23f * unidade),
                strokeWidth = 3.6f * unidade,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Selo quadrado em cobre com o sol — o ícone do app, dentro da tela. */
@Composable
fun SeloSelebi(tamanho: Dp = 56.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(tamanho)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(tamanho * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        SolSelebi(Modifier.size(tamanho * 0.9f), cor = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Selo + "Selebi" escrito. */
@Composable
fun MarcaSelebi(modifier: Modifier = Modifier, tamanho: Dp = 44.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SeloSelebi(tamanho)
        Text(
            "BBSolution",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
