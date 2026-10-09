package br.com.selebi.app.funcionalidade.execucao

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.RegrasOs
import br.com.selebi.app.nucleo.util.Datas

/*
 * As folhas que sobem de baixo durante a execução. Todas pedem pouco e
 * decidem uma coisa só.
 */

/**
 * Motivo em lista, e não texto livre — é o que deixa o escritório contar
 * quantas visitas a chuva cancelou no mês. "Outro" exige o detalhe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaMotivo(
    titulo: String,
    explicacao: String,
    motivos: List<String>,
    botao: String,
    aoConfirmar: (String) -> Unit,
    aoFechar: () -> Unit,
    perigo: Boolean = false,
) {
    var escolhido by rememberSaveable { mutableStateOf<String?>(null) }
    var detalhe by rememberSaveable { mutableStateOf("") }
    val texto = RegrasOs.motivoCom(escolhido, detalhe)
    val faltaDetalhe = escolhido == "Outro" && detalhe.isBlank()
    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleLarge)
            Text(explicacao, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            motivos.forEach { m ->
                OpcaoGrande(m, marcada = escolhido == m, aoTocar = { escolhido = m }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = detalhe,
                onValueChange = { detalhe = it.take(RegrasOs.MOTIVO_MAXIMO - 40) },
                label = { Text(if (escolhido == "Outro") "Explique (obrigatório)" else "Detalhe (opcional)") },
                isError = faltaDetalhe,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { aoConfirmar(texto) },
                enabled = texto.isNotBlank() && !faltaDetalhe,
                shape = MaterialTheme.shapes.medium,
                colors = if (perigo) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(botao) }
        }
    }
}

/** O checklist inteiro por seção: ver o que falta e pular direto para lá. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaIndice(ordem: Ordem, passoAtual: Int, aoIr: (Int) -> Unit, aoFechar: () -> Unit) {
    val c = SelebiTema.cores
    val pendentes = ordem.obrigatoriosPendentes.size
    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                    Text("Checklist", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${ordem.checklistFeitos} de ${ordem.checklist.size} cumpridos" +
                            if (pendentes > 0) " · faltam $pendentes obrigatórios" else " · nenhum obrigatório faltando",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pendentes > 0) c.alerta else c.sucesso,
                    )
                }
            }
            item { LinhaIndice("Chegada", null, passoAtual == 0, cumprido = null, icone = Icons.Outlined.Place) { aoIr(0) } }
            Roteiro.secoes(ordem).forEach { (secao, itens) ->
                if (secao != null) {
                    item {
                        Text(
                            secao.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                }
                items(itens, key = { it.second.id }) { (passo, item) ->
                    LinhaIndice(
                        item.descricao,
                        if (item.obrigatorio) null else "Opcional",
                        passoAtual == passo,
                        cumprido = item.concluido,
                    ) { aoIr(passo) }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                LinhaIndice("Revisão e conclusão", null, passoAtual == ordem.checklist.size + 1, cumprido = null, icone = Icons.Outlined.Flag) {
                    aoIr(ordem.checklist.size + 1)
                }
            }
        }
    }
}

@Composable
private fun LinhaIndice(
    texto: String,
    detalhe: String?,
    atual: Boolean,
    cumprido: Boolean?,
    icone: ImageVector? = null,
    aoTocar: () -> Unit,
) {
    val c = SelebiTema.cores
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = aoTocar)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (cumprido) {
            true -> Icon(Icons.Outlined.CheckCircle, contentDescription = "Cumprido", tint = c.sucesso, modifier = Modifier.size(22.dp))
            false -> Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "Pendente", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
            null -> Icon(icone ?: Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                texto,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (atual) FontWeight.SemiBold else FontWeight.Normal,
                color = if (atual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (detalhe != null) {
                Text(detalhe, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * O que está só neste celular. Esperando sinal não precisa de nada; o que o
 * servidor recusou mostra o porquê e sai com "Descartar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaFila(
    envios: List<Envio>,
    ordem: Ordem?,
    online: Boolean,
    aoDescartar: (String) -> Unit,
    aoEnviarAgora: () -> Unit,
    aoFechar: () -> Unit,
) {
    val c = SelebiTema.cores
    val esperando = envios.count { !it.recusado }
    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Envios deste celular", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when {
                            envios.isEmpty() -> "Tudo o que você fez nesta OS já está no BBSolution."
                            !online -> "Sem sinal agora. Está tudo guardado aqui e sobe sozinho quando a conexão voltar — mesmo com o app fechado."
                            esperando > 0 -> "Subindo. Não precisa esperar com o app aberto."
                            else -> "O servidor recusou o que está abaixo. Veja o motivo e descarte."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(envios, key = { it.id }) { envio ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (envio.recusado) c.perigoFundo else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (envio.recusado) c.sobrePerigo else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            when {
                                envio.recusado -> Icons.Outlined.ErrorOutline
                                online -> Icons.Outlined.CloudUpload
                                else -> Icons.Outlined.CloudOff
                            },
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(Roteiro.descrever(envio, ordem), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                envio.erro ?: ("Feito em " + Datas.instanteCurto(envio.criadoEm)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (envio.recusado) {
                                TextButton(onClick = { aoDescartar(envio.id) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("Descartar")
                                }
                            }
                        }
                    }
                }
            }
            if (esperando > 0 && online) {
                item {
                    OutlinedButton(onClick = aoEnviarAgora, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Tentar enviar agora")
                    }
                }
            }
            item { Column(Modifier.padding(bottom = 16.dp)) {} }
        }
    }
}

/** Recado para o escritório — o "portão pelo lado da padaria" que hoje vai por áudio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaNota(aoSalvar: (String) -> Unit, aoFechar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nota para o escritório", style = MaterialTheme.typography.titleLarge)
            Text(
                "Fica na OS, junto dos comentários do site. Sem sinal, sobe depois.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it.take(RegrasOs.NOTA_MAXIMO) },
                label = { Text("Nota") },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { aoSalvar(texto) },
                enabled = texto.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Guardar nota") }
        }
    }
}
