package br.com.selebi.app.funcionalidade.execucao

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas as TelaDeImagem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.selebi.app.nucleo.modelo.RegrasOs
import kotlin.math.min

/**
 * O cliente assina com o dedo na tela. Vira um PNG de traço preto no branco —
 * é a imagem que vai no relatório em PDF — e o nome de quem assinou vai junto
 * com a conclusão (nem sempre é o titular: às vezes é a esposa, o caseiro).
 */
@Composable
fun PainelAssinatura(
    nomeInicial: String,
    aoCancelar: () -> Unit,
    aoConfirmar: (ImageBitmap, String) -> Unit,
) {
    val tracos = remember { mutableStateListOf<List<Offset>>() }
    var atual by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var tamanho by remember { mutableStateOf(IntSize.Zero) }
    var nome by rememberSaveable { mutableStateOf(nomeInicial) }
    val espessura = with(LocalDensity.current) { 3.5.dp.toPx() }
    val vazio = tracos.isEmpty() && atual.isEmpty()

    Dialog(onDismissRequest = aoCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Assinatura do cliente", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = aoCancelar) { Icon(Icons.Outlined.Close, contentDescription = "Fechar") }
                }
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it.take(RegrasOs.NOME_MAXIMO) },
                    label = { Text("Nome de quem está assinando") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .background(Color.White, MaterialTheme.shapes.medium)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.medium)
                        .onSizeChanged { tamanho = it }
                        .pointerInput(Unit) {
                            detectTapGestures { ponto -> tracos.add(listOf(ponto)) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { atual = listOf(it) },
                                onDrag = { mudanca, _ -> atual = atual + mudanca.position },
                                onDragEnd = {
                                    if (atual.isNotEmpty()) tracos.add(atual)
                                    atual = emptyList()
                                },
                                onDragCancel = {
                                    if (atual.isNotEmpty()) tracos.add(atual)
                                    atual = emptyList()
                                },
                            )
                        },
                ) {
                    // A linha de assinar, como no papel.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFB0B7BE)),
                    )
                    if (vazio) {
                        Text(
                            "Assine aqui com o dedo",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF8A939C),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        (tracos + listOf(atual)).filter { it.isNotEmpty() }.forEach { traco ->
                            drawPath(
                                caminho(traco),
                                color = Color.Black,
                                style = Stroke(width = espessura, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            tracos.clear()
                            atual = emptyList()
                        },
                        enabled = !vazio,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Limpar") }
                    Button(
                        onClick = { aoConfirmar(desenhar(tracos.toList(), tamanho, espessura), nome.trim()) },
                        enabled = !vazio && tamanho.width > 0,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Confirmar") }
                }
            }
        }
    }
}

/** Um traço com curvas suaves entre os pontos — a assinatura não sai "quebrada". */
private fun caminho(pontos: List<Offset>): Path = Path().apply {
    val primeiro = pontos.first()
    moveTo(primeiro.x, primeiro.y)
    if (pontos.size == 1) {
        lineTo(primeiro.x + 0.1f, primeiro.y + 0.1f)
        return@apply
    }
    for (i in 1 until pontos.size) {
        val anterior = pontos[i - 1]
        val ponto = pontos[i]
        quadraticTo(anterior.x, anterior.y, (anterior.x + ponto.x) / 2, (anterior.y + ponto.y) / 2)
    }
    lineTo(pontos.last().x, pontos.last().y)
}

/** A assinatura em imagem, no máximo 1200 px de largura: nítida no PDF e leve para subir. */
private fun desenhar(tracos: List<List<Offset>>, tamanho: IntSize, espessura: Float): ImageBitmap {
    val escala = min(1f, 1200f / tamanho.width.coerceAtLeast(1))
    val largura = (tamanho.width * escala).toInt().coerceAtLeast(1)
    val altura = (tamanho.height * escala).toInt().coerceAtLeast(1)
    val imagem = ImageBitmap(largura, altura)
    val tela = TelaDeImagem(imagem)
    tela.drawRect(0f, 0f, largura.toFloat(), altura.toFloat(), Paint().apply { color = Color.White })
    tela.scale(escala, escala)
    val tinta = Paint().apply {
        isAntiAlias = true
        color = Color.Black
        style = PaintingStyle.Stroke
        strokeWidth = espessura
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
    }
    tracos.filter { it.isNotEmpty() }.forEach { tela.drawPath(caminho(it), tinta) }
    return imagem
}
