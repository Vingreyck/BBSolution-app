package br.com.selebi.app.funcionalidade.execucao

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.selebi.app.nucleo.design.componentes.MiniaturaAnexo
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.util.Datas

/*
 * As fotos de um item: as que já existem, o lugar das que faltam, e os botões
 * de câmera e galeria. A foto que ainda não subiu leva a nuvem no canto — o
 * técnico sabe o que está só no celular dele.
 */

@Composable
fun SecaoFotos(
    ordemId: String,
    fotos: List<AnexoOs>,
    minimo: Int,
    apenasCamera: Boolean,
    habilitado: Boolean,
    preparando: Boolean,
    aoCamera: () -> Unit,
    aoGaleria: () -> Unit,
    aoAbrir: (AnexoOs) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SelebiTema.cores
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fotos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val completo = fotos.size >= minimo
            Text(
                when {
                    minimo == 0 -> if (fotos.isEmpty()) "Opcional" else "${fotos.size}"
                    else -> "${fotos.size.coerceAtMost(99)} de $minimo"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (minimo > 0 && completo) c.sucesso else if (minimo > 0) c.alerta else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Três por linha, quadradas. O lugar das que faltam aparece vazio, com o
        // número: o técnico vê quantas ainda precisa tirar.
        val faltam = (minimo - fotos.size - if (preparando) 1 else 0).coerceAtLeast(0)
        val ladrilhos: List<Ladrilho> = fotos.map { Ladrilho.Foto(it) } +
            (if (preparando) listOf(Ladrilho.Preparando) else emptyList()) +
            (1..faltam).map { Ladrilho.Vaga(fotos.size + (if (preparando) 1 else 0) + it) }
        ladrilhos.chunked(3).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { ladrilho ->
                    val forma = Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                    when (ladrilho) {
                        is Ladrilho.Foto -> Box(forma.clickable { aoAbrir(ladrilho.anexo) }) {
                            MiniaturaAnexo(ordemId, ladrilho.anexo, Modifier.fillMaxSize())
                            if (ladrilho.anexo.pendente) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(26.dp)
                                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.CloudUpload,
                                        contentDescription = "Esperando sinal",
                                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                        Ladrilho.Preparando -> Box(
                            forma.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
                        is Ladrilho.Vaga -> Box(
                            forma
                                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.medium)
                                .clickable(enabled = habilitado, onClick = aoCamera),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "Foto ${ladrilho.numero}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                // Completa a linha, para as miniaturas não esticarem.
                repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = aoCamera,
                enabled = habilitado && !preparando,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Tirar foto", modifier = Modifier.padding(start = 8.dp))
            }
            if (!apenasCamera) {
                OutlinedButton(
                    onClick = aoGaleria,
                    enabled = habilitado && !preparando,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Galeria", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (apenasCamera) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    "Só pela câmera, na hora — foto da galeria ou print não vale aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private sealed interface Ladrilho {
    data class Foto(val anexo: AnexoOs) : Ladrilho
    data object Preparando : Ladrilho
    data class Vaga(val numero: Int) : Ladrilho
}

/** Para item sem foto exigida: dois botões pequenos, para não pesar a tela. */
@Composable
fun FotoOpcional(habilitado: Boolean, apenasCamera: Boolean, aoCamera: () -> Unit, aoGaleria: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = aoCamera, enabled = habilitado) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Anexar foto", modifier = Modifier.padding(start = 6.dp))
        }
        if (!apenasCamera) {
            TextButton(onClick = aoGaleria, enabled = habilitado) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Da galeria", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * A foto grande, com quando foi tirada e se já subiu. Apagar só a que o
 * próprio técnico mandou — a do escritório fica.
 */
@Composable
fun VisorDeFoto(
    ordemId: String,
    foto: AnexoOs,
    podeApagar: Boolean,
    aoFechar: () -> Unit,
    aoApagar: () -> Unit,
) {
    var confirmar by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                MiniaturaAnexo(
                    ordemId,
                    foto,
                    Modifier.fillMaxSize().background(Color.Black),
                    lado = 1600,
                    ajuste = ContentScale.Fit,
                )
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().background(Color.Black.copy(alpha = 0.45f)).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = aoFechar) { Icon(Icons.Outlined.Close, contentDescription = "Fechar", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            foto.capturadoEm?.let { "Tirada em " + Datas.instanteCurto(it) } ?: "Foto",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (foto.pendente) "Só neste celular — sobe quando tiver sinal" else "Já está no BBSolution",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    if (podeApagar) {
                        IconButton(onClick = { confirmar = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Apagar foto", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Apagar esta foto?") },
            text = {
                Text(
                    if (foto.pendente) {
                        "Ela ainda não subiu, então some de vez."
                    } else {
                        "Ela sai da OS e o arquivo vai para a lixeira do Drive da empresa, onde fica 30 dias."
                    },
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmar = false
                    aoApagar()
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}
