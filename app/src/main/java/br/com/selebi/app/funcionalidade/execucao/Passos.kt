package br.com.selebi.app.funcionalidade.execucao

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.selebi.app.funcionalidade.ordens.IconeTipo
import br.com.selebi.app.funcionalidade.ordens.LinhaInfo
import br.com.selebi.app.funcionalidade.ordens.PilulaSituacao
import br.com.selebi.app.funcionalidade.ordens.textoQuando
import br.com.selebi.app.nucleo.design.componentes.AcaoRapida
import br.com.selebi.app.nucleo.design.componentes.Aviso
import br.com.selebi.app.nucleo.design.componentes.BlocoDetalhe
import br.com.selebi.app.nucleo.design.componentes.BotaoPrincipal
import br.com.selebi.app.nucleo.design.componentes.MiniaturaAnexo
import br.com.selebi.app.nucleo.design.componentes.Pilula
import br.com.selebi.app.nucleo.design.componentes.TipoAviso
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.AcaoOs
import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.RegrasOs
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TipoResposta
import br.com.selebi.app.nucleo.util.Datas
import java.time.LocalDateTime

// ---------------------------------------------------------------------------
// Chegada

/**
 * Antes de pôr a mão: quem é, onde fica, o que o escritório mandou saber, e o
 * check-in — "estou a caminho" e "cheguei", cada um com o ponto do GPS.
 */
@Composable
fun ConteudoChegada(estado: ExecucaoEstado, acoes: AcoesExecucao, localizacaoLiberada: Boolean, agora: LocalDateTime) {
    val ordem = estado.ordem ?: return
    val c = SelebiTema.cores
    val telefone = ordem.cliente.telefone
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeTipo(ordem.tipo, tamanho = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ordem.tipo.rotulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ordem.cliente.nome, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PilulaSituacao(ordem, agora) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ordem.enderecoCompleto?.let { LinhaInfo(Icons.Outlined.Place, it, linhas = 3) }
            textoQuando(ordem, agora)?.let { LinhaInfo(Icons.Outlined.Schedule, it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AcaoRapida(Icons.Outlined.Directions, "Rota", acoes.aoRota, Modifier.weight(1f))
            AcaoRapida(Icons.Outlined.Call, "Ligar", acoes.aoLigar, Modifier.weight(1f), habilitada = telefone != null)
            AcaoRapida(Icons.AutoMirrored.Outlined.Chat, "Avisar", acoes.aoAvisarACaminho, Modifier.weight(1f), habilitada = telefone != null)
        }

        when (ordem.status) {
            StatusOs.AGUARDANDO_PECA -> {
                val pausa = ordem.historico.firstOrNull { it.tipo == "pausada" }?.detalhe
                Aviso(
                    pausa?.let { "Motivo: $it. Toque em Retomar quando voltar ao local." } ?: "Toque em Retomar quando voltar ao local.",
                    TipoAviso.ESPERA,
                    titulo = "Atendimento pausado",
                )
            }
            StatusOs.CONCLUIDA, StatusOs.CANCELADA -> Aviso(
                "Esta OS está \"${ordem.status.rotulo.lowercase()}\" e não muda mais pelo app.",
                TipoAviso.INFO,
            )
            else -> Unit
        }

        ordem.instrucoes?.takeIf { it.isNotBlank() }?.let {
            Surface(color = c.infoFundo, contentColor = c.sobreInfo, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Antes de começar", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (ordem.descricao.isNotBlank()) {
            BlocoDetalhe("O que fazer") { Text(ordem.descricao, style = MaterialTheme.typography.bodyLarge) }
        }
        val recados = ordem.notas.filter { it.autorId != estado.usuario?.id }.takeLast(3)
        if (recados.isNotEmpty()) {
            BlocoDetalhe("Recados do escritório") {
                recados.forEach { nota ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(nota.texto, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(nota.autor, nota.criadoEm?.let(Datas::instanteCurto)).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (ordem.checklist.isNotEmpty()) {
            ResumoChecklist(ordem)
        }
        val checkIn = ordem.status == StatusOs.ABERTA || ordem.status == StatusOs.AGENDADA || ordem.status == StatusOs.EM_DESLOCAMENTO
        if (checkIn && localizacaoLiberada) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    "O escritório acompanha você no mapa do \"Estou a caminho\" até concluir. Fora da OS, ninguém é rastreado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (checkIn && !localizacaoLiberada) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Com a localização ligada, o escritório acompanha você no mapa do \"Estou a caminho\" até concluir a OS — e vê de onde você saiu e onde o atendimento começou.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = acoes.aoPedirLocalizacao, contentPadding = PaddingValues(0.dp)) {
                            Text("Permitir localização")
                        }
                    }
                }
            }
        }
        FimDaRolagem()
    }
}

@Composable
private fun ResumoChecklist(ordem: Ordem) {
    val c = SelebiTema.cores
    val obrigatorios = ordem.checklist.count { it.obrigatorio }
    val comFoto = ordem.checklist.count { it.fotosMinimas > 0 || it.tipoResposta == TipoResposta.FOTO }
    val faltam = ordem.obrigatoriosPendentes.size
    BlocoDetalhe("Checklist") {
        Text(
            "${ordem.checklist.size} itens · $obrigatorios obrigatórios · $comFoto pedem foto",
            style = MaterialTheme.typography.bodyLarge,
        )
        LinearProgressIndicator(
            progress = { ordem.checklistFeitos.toFloat() / ordem.checklist.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            drawStopIndicator = {},
        )
        Text(
            if (faltam == 0) "Nenhum obrigatório faltando." else "${ordem.checklistFeitos} cumpridos · faltam $faltam obrigatórios",
            style = MaterialTheme.typography.labelLarge,
            color = if (faltam == 0) c.sucesso else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BarraChegada(estado: ExecucaoEstado, acoes: AcoesExecucao) {
    val ordem = estado.ordem ?: return
    when (ordem.status) {
        StatusOs.ABERTA, StatusOs.AGENDADA -> BarraDoPasso {
            OutlinedButton(
                onClick = { acoes.aoAgir(AcaoOs.INICIAR, null) },
                enabled = !estado.ocupado,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Já cheguei") }
            BotaoPrincipal(
                "Estou a caminho",
                { acoes.aoAgir(AcaoOs.DESLOCAMENTO, null) },
                Modifier.weight(1.4f),
                carregando = estado.ocupado,
                icone = Icons.Outlined.DirectionsCar,
            )
        }
        StatusOs.EM_DESLOCAMENTO -> BarraDoPasso {
            BotaoPrincipal(
                "Cheguei · iniciar atendimento",
                { acoes.aoAgir(AcaoOs.INICIAR, null) },
                carregando = estado.ocupado,
                icone = Icons.Outlined.PlayArrow,
            )
        }
        StatusOs.EM_ANDAMENTO -> BarraDoPasso {
            BotaoPrincipal(
                if (ordem.checklist.isEmpty()) "Concluir" else if (ordem.checklistFeitos == 0) "Começar o checklist" else "Continuar o checklist",
                acoes.aoContinuar,
                icone = Icons.AutoMirrored.Outlined.ArrowForward,
            )
        }
        StatusOs.AGUARDANDO_PECA -> BarraDoPasso {
            BotaoPrincipal(
                "Retomar atendimento",
                { acoes.aoAgir(AcaoOs.RETOMAR, null) },
                carregando = estado.ocupado,
                icone = Icons.Outlined.PlayArrow,
            )
        }
        else -> BarraDoPasso { BotaoPrincipal("Fechar", acoes.aoFechar) }
    }
}

// ---------------------------------------------------------------------------
// Item do checklist

@Composable
fun ConteudoItem(estado: ExecucaoEstado, item: ItemChecklist, acoes: AcoesExecucao, aoAbrirFoto: (AnexoOs) -> Unit = {}) {
    val ordem = estado.ordem ?: return
    val c = SelebiTema.cores
    val habilitado = Roteiro.podeResponder(ordem)
    val fotos = ordem.fotosDo(item.id)
    val pedeFoto = item.tipoResposta == TipoResposta.FOTO || item.fotosMinimas > 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!habilitado) BloqueioDoChecklist(ordem, acoes)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (item.secao ?: "Checklist").uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (item.obrigatorio) {
                    Pilula("Obrigatório", c.alertaFundo, c.sobreAlerta)
                } else {
                    Pilula("Opcional", c.neutroFundo, c.sobreNeutro)
                }
            }
            Text(item.descricao, style = MaterialTheme.typography.headlineSmall)
        }
        item.ajuda?.takeIf { it.isNotBlank() }?.let { Dica(it) }
        CampoResposta(item, habilitado, { acoes.aoResponder(item, it) }, acoes.aoLerCodigo)
        if (pedeFoto || fotos.isNotEmpty()) {
            SecaoFotos(
                ordemId = ordem.id,
                fotos = fotos,
                minimo = if (item.tipoResposta == TipoResposta.FOTO) maxOf(1, item.fotosMinimas) else item.fotosMinimas,
                apenasCamera = item.apenasCamera,
                habilitado = habilitado,
                preparando = estado.preparandoFoto == item.id,
                aoCamera = { acoes.aoCamera(item.id) },
                aoGaleria = { acoes.aoGaleria(item.id) },
                aoAbrir = aoAbrirFoto,
            )
        } else {
            FotoOpcional(habilitado && estado.preparandoFoto == null, item.apenasCamera, { acoes.aoCamera(item.id) }, { acoes.aoGaleria(item.id) })
        }
        CampoObservacao(item, habilitado) { acoes.aoObservar(item, it) }
        FimDaRolagem()
    }
}

/** Por que o checklist está travado, e o botão que destrava. */
@Composable
private fun BloqueioDoChecklist(ordem: Ordem, acoes: AcoesExecucao) {
    val (texto, botao, acao) = when (ordem.status) {
        StatusOs.AGUARDANDO_PECA -> Triple("O atendimento está pausado. Retome para responder.", "Retomar", AcaoOs.RETOMAR)
        StatusOs.CONCLUIDA, StatusOs.CANCELADA -> Triple("A OS está ${ordem.status.rotulo.lowercase()}; o checklist não muda mais.", null, null)
        else -> Triple("O atendimento ainda não começou. Toque em Iniciar ao chegar no local.", "Iniciar", AcaoOs.INICIAR)
    }
    val c = SelebiTema.cores
    Surface(color = c.alertaFundo, contentColor = c.sobreAlerta, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (botao != null && acao != null) {
                TextButton(onClick = { acoes.aoAgir(acao, null) }) { Text(botao) }
            }
        }
    }
}

/** A observação do item: fechada quando vazia, para não pesar a tela. */
@Composable
private fun CampoObservacao(item: ItemChecklist, habilitado: Boolean, aoSalvar: (String) -> Unit) {
    var aberta by rememberSaveable(item.id) { mutableStateOf(!item.observacao.isNullOrBlank()) }
    if (!aberta) {
        TextButton(onClick = { aberta = true }, enabled = habilitado) {
            Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Adicionar observação", modifier = Modifier.padding(start = 6.dp))
        }
        return
    }
    val salvo = item.observacao.orEmpty()
    var rascunho by rememberSaveable(item.id + ":obs") { mutableStateOf(salvo) }
    GuardarAoParar(rascunho, salvo, aoSalvar, item.id + ":obs")
    OutlinedTextField(
        value = rascunho,
        onValueChange = { rascunho = it.take(RegrasOs.TEXTO_MAXIMO) },
        label = { Text("Observação") },
        placeholder = { Text("O que o escritório precisa saber deste item") },
        enabled = habilitado,
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun BarraItem(estado: ExecucaoEstado, item: ItemChecklist, acoes: AcoesExecucao) {
    val ordem = estado.ordem ?: return
    val falta = RegrasOs.faltaNoItem(item, ordem.fotosDo(item.id).size)
    val ultimo = estado.passo == estado.passos.lastIndex - 1
    BarraDoPasso(
        aviso = when {
            item.concluido -> "✓ Item cumprido"
            falta != null && item.obrigatorio -> falta
            falta != null -> "$falta (opcional)"
            else -> null
        },
        avisoPositivo = item.concluido,
    ) {
        OutlinedIconButton(onClick = acoes.aoAnterior, modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.medium) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
        }
        BotaoPrincipal(
            if (ultimo) "Revisar e concluir" else "Próximo",
            acoes.aoProximo,
            Modifier.weight(1f),
            icone = Icons.AutoMirrored.Outlined.ArrowForward,
        )
    }
}

// ---------------------------------------------------------------------------
// Conclusão

@Composable
fun ConteudoConclusao(estado: ExecucaoEstado, acoes: AcoesExecucao) {
    val ordem = estado.ordem ?: return
    val c = SelebiTema.cores
    val form = estado.conclusao
    val pendentes = ordem.obrigatoriosPendentes
    val passos = estado.passos
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Revisão", style = MaterialTheme.typography.headlineSmall)
        if (ordem.checklist.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${ordem.checklistFeitos}/${ordem.checklist.size}", style = MaterialTheme.typography.headlineMedium)
                        Text("itens cumpridos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    LinearProgressIndicator(
                        progress = { ordem.checklistFeitos.toFloat() / ordem.checklist.size },
                        color = if (pendentes.isEmpty()) c.sucesso else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        drawStopIndicator = {},
                    )
                }
            }
        }
        if (pendentes.isNotEmpty()) {
            Surface(color = c.alertaFundo, contentColor = c.sobreAlerta, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        if (pendentes.size == 1) "Falta 1 item obrigatório" else "Faltam ${pendentes.size} itens obrigatórios",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    pendentes.forEach { item ->
                        val passo = passos.indexOfFirst { it is Passo.Item && it.itemId == item.id }
                        Row(
                            Modifier.fillMaxWidth().clickable { acoes.aoIrPara(passo) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.descricao, style = MaterialTheme.typography.bodyLarge)
                                RegrasOs.faltaNoItem(item, ordem.fotosDo(item.id).size)?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Ir para o item")
                        }
                    }
                }
            }
        }

        Text("Como terminou", style = MaterialTheme.typography.titleMedium)
        OpcaoGrande(
            "Resolvido",
            marcada = form.resolvido,
            aoTocar = { acoes.aoMudarConclusao(form.copy(resolvido = true)) },
            icone = Icons.Outlined.TaskAlt,
            modifier = Modifier.fillMaxWidth(),
        )
        OpcaoGrande(
            "Não resolvido — precisa voltar",
            marcada = !form.resolvido,
            aoTocar = { acoes.aoMudarConclusao(form.copy(resolvido = false)) },
            icone = Icons.Outlined.Replay,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.laudo,
            onValueChange = { acoes.aoMudarConclusao(form.copy(laudo = it.take(RegrasOs.LAUDO_MAXIMO))) },
            label = { Text(if (form.resolvido) "Laudo (opcional)" else "O que ficou pendente (obrigatório)") },
            placeholder = {
                Text(if (form.resolvido) "O que foi feito, o que o cliente precisa saber" else "Ex.: falta a placa de comunicação; volto quando chegar")
            },
            isError = !form.resolvido && form.laudo.isBlank(),
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        BlocoAssinatura(ordem, form, acoes)

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("Não dá para concluir hoje?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (RegrasOs.permitida(AcaoOs.PAUSAR, ordem.status)) {
                TextButton(onClick = acoes.aoPausar) { Text("Pausar — aguardando peça ou o cliente") }
            }
            if (RegrasOs.permitida(AcaoOs.NAO_ATENDIDA, ordem.status)) {
                TextButton(onClick = acoes.aoNaoAtendida) { Text("Não consegui atender") }
            }
        }
        FimDaRolagem()
    }
}

@Composable
private fun BlocoAssinatura(ordem: Ordem, form: FormConclusao, acoes: AcoesExecucao) {
    val c = SelebiTema.cores
    val assinatura = ordem.assinatura
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Assinatura do cliente", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (ordem.exigeAssinatura) {
                Pilula("Obrigatória", if (assinatura == null) c.alertaFundo else c.sucessoFundo, if (assinatura == null) c.sobreAlerta else c.sobreSucesso)
            } else {
                Pilula("Opcional", c.neutroFundo, c.sobreNeutro)
            }
        }
        if (assinatura != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniaturaAnexo(
                        ordem.id,
                        assinatura,
                        Modifier.fillMaxWidth().height(120.dp).background(Color.White, MaterialTheme.shapes.small),
                        lado = 900,
                        ajuste = ContentScale.Fit,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (form.assinante.ifBlank { null } ?: ordem.assinaturaNome)?.let { "Assinado por $it" } ?: "Assinado",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (assinatura.pendente) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Sobe com o resto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (assinatura.pendente) TextButton(onClick = acoes.aoAbrirAssinatura) { Text("Refazer") }
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = acoes.aoAbrirAssinatura,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Outlined.Draw, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Coletar a assinatura do cliente", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun BarraConclusao(estado: ExecucaoEstado, acoes: AcoesExecucao) {
    val ordem = estado.ordem ?: return
    val form = estado.conclusao
    val impedimentos = RegrasOs.impedimentos(ordem, form.resolvido, form.laudo, ordem.temAssinatura)
    val pendentes = ordem.obrigatoriosPendentes.size
    // Curto: a lista do que falta já está na tela, logo acima.
    val aviso = when {
        pendentes == 1 -> "Falta 1 item obrigatório"
        pendentes > 1 -> "Faltam $pendentes itens obrigatórios"
        else -> impedimentos.firstOrNull()
    }
    BarraDoPasso(aviso = aviso) {
        OutlinedIconButton(onClick = acoes.aoAnterior, modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.medium) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
        }
        BotaoPrincipal(
            "Concluir OS",
            acoes.aoConcluir,
            Modifier.weight(1f),
            carregando = estado.ocupado,
            habilitado = impedimentos.isEmpty() && Roteiro.podeResponder(ordem),
            icone = Icons.Outlined.CheckCircle,
        )
    }
}

// ---------------------------------------------------------------------------
// Concluída

@Composable
fun ConteudoConcluida(estado: ExecucaoEstado, acoes: AcoesExecucao) {
    val ordem = estado.ordem ?: return
    val c = SelebiTema.cores
    val esperando = estado.envios.count { !it.recusado }
    val recusados = estado.envios.count { it.recusado }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(104.dp).background(c.sucessoFundo, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = c.sucesso, modifier = Modifier.size(56.dp))
        }
        Text("OS ${ordem.numeroFormatado} concluída", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            listOf(if (ordem.resultado == "nao_resolvido") "Não resolvido — precisa voltar" else "Resolvido", ordem.cliente.nome).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        when {
            recusados > 0 -> Aviso(
                "O servidor recusou $recusados envio(s). Toque na nuvem lá em cima para ver o motivo.",
                TipoAviso.ERRO,
            )
            esperando > 0 -> Aviso(
                if (estado.online) "Subindo para o BBSolution — pode guardar o celular." else "Está tudo guardado no celular e sobe sozinho quando o sinal voltar, mesmo com o app fechado.",
                TipoAviso.INFO,
                titulo = if (estado.online) "Enviando" else "Sem sinal",
            )
            else -> Aviso("Tudo enviado. O escritório já vê a OS concluída no site.", TipoAviso.INFO, titulo = "No BBSolution")
        }
        estado.avisos.forEach { aviso ->
            Surface(color = c.sucessoFundo, contentColor = c.sobreSucesso, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(aviso.texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                    TextButton(onClick = { acoes.aoDispensarAviso(aviso.id) }) { Text("Ok") }
                }
            }
        }
        BlocoRelatorio(ordem, acoes.aoEnviarRelatorio, acoes.aoCompartilharRelatorio, acoes.aoAbrirRelatorio)
    }
}

/** O relatório em PDF para o cliente — o mesmo link público do site. */
@Composable
fun BlocoRelatorio(ordem: Ordem, aoEnviar: () -> Unit, aoCompartilhar: () -> Unit, aoAbrir: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Relatório para o cliente", style = MaterialTheme.typography.titleMedium)
            if (ordem.relatorio == null) {
                Text(
                    "Fica pronto quando a conclusão chegar ao servidor. Depois, está também na tela da OS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "PDF com o checklist, as fotos e a assinatura. O link abre sem senha.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ordem.cliente.telefone != null) {
                    Button(onClick = aoEnviar, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Mandar no WhatsApp do cliente", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = aoCompartilhar, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Compartilhar", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = aoAbrir, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Abrir PDF", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BarraConcluida(acoes: AcoesExecucao) {
    BarraDoPasso { BotaoPrincipal("Voltar para as OS", acoes.aoVoltarParaLista) }
}
