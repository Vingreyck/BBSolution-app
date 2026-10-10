package br.com.selebi.app.funcionalidade.ordens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentPasteSearch
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.HomeRepairService
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SolarPower
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.design.componentes.Pilula
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Prioridade
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TipoOs
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.datetime.LocalDateTime

/*
 * As peças visuais de uma OS, iguais em toda tela: o ícone do tipo, a pílula
 * de situação, o cartão da lista e o cartão de destaque.
 */

fun iconeDoTipo(tipo: TipoOs): ImageVector = when (tipo) {
    TipoOs.VISTORIA -> Icons.Outlined.ContentPasteSearch
    TipoOs.INSTALACAO -> Icons.Outlined.SolarPower
    TipoOs.PREVENTIVA -> Icons.Outlined.EventRepeat
    TipoOs.CORRETIVA -> Icons.Outlined.Build
    TipoOs.LIMPEZA -> Icons.Outlined.WaterDrop
    TipoOs.GARANTIA -> Icons.Outlined.VerifiedUser
    TipoOs.OUTRO -> Icons.Outlined.HomeRepairService
}

@Composable
fun IconeTipo(tipo: TipoOs, modifier: Modifier = Modifier, tamanho: Dp = 44.dp) {
    Box(
        modifier
            .size(tamanho)
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            iconeDoTipo(tipo),
            contentDescription = tipo.rotulo,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(tamanho * 0.52f),
        )
    }
}

data class ParDeCores(val fundo: Color, val frente: Color)

/**
 * A pílula de situação. "Atrasada" passa na frente de "A agendar" e
 * "Agendada", mas não de "A caminho", "Em atendimento" e "Aguardando peça" —
 * esses dizem algo que o técnico precisa saber mesmo com a OS atrasada.
 */
@Composable
fun situacaoDa(ordem: Ordem, agora: LocalDateTime): Pair<String, ParDeCores> {
    val c = SelebiTema.cores
    val esquema = MaterialTheme.colorScheme
    val atrasada = ordem.atrasada(agora) && ordem.status in setOf(StatusOs.ABERTA, StatusOs.AGENDADA)
    if (atrasada) return "Atrasada" to ParDeCores(c.perigoFundo, c.sobrePerigo)
    val cores = when (ordem.status) {
        StatusOs.EM_ANDAMENTO, StatusOs.EM_DESLOCAMENTO -> ParDeCores(esquema.primaryContainer, esquema.onPrimaryContainer)
        StatusOs.AGENDADA -> ParDeCores(c.infoFundo, c.sobreInfo)
        StatusOs.AGUARDANDO_PECA -> ParDeCores(c.alertaFundo, c.sobreAlerta)
        StatusOs.CONCLUIDA -> ParDeCores(c.sucessoFundo, c.sobreSucesso)
        StatusOs.ABERTA, StatusOs.CANCELADA, StatusOs.DESCONHECIDO -> ParDeCores(c.neutroFundo, c.sobreNeutro)
    }
    return ordem.status.rotulo to cores
}

@Composable
fun PilulaSituacao(ordem: Ordem, agora: LocalDateTime, modifier: Modifier = Modifier) {
    val (texto, cores) = situacaoDa(ordem, agora)
    Pilula(texto, cores.fundo, cores.frente, modifier)
}

@Composable
fun LinhaInfo(
    icone: ImageVector,
    texto: String,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linhas: Int = 1,
) = LinhaInfo(icone, AnnotatedString(texto), modifier, cor, linhas)

@Composable
fun LinhaInfo(
    icone: ImageVector,
    texto: AnnotatedString,
    modifier: Modifier = Modifier,
    cor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linhas: Int = 1,
) {
    // Em várias linhas, o ícone acompanha a primeira (linha de 21sp, ícone de
    // 16dp: uns 2dp de folga em cima), e não o meio do bloco.
    val variasLinhas = linhas > 1
    Row(
        modifier,
        verticalAlignment = if (variasLinhas) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icone,
            contentDescription = null,
            tint = cor,
            modifier = Modifier.padding(top = if (variasLinhas) 2.dp else 0.dp).size(16.dp),
        )
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = cor, maxLines = linhas, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Onde, numa linha só: a cidade primeiro e mais forte — é por ela que o
 * técnico planeja o dia —, depois rua e bairro, que o mapa resolve. Se não
 * couber, some o fim da rua, e não a cidade.
 */
@Composable
private fun LinhaLugar(ordem: Ordem) {
    val cidade = ordem.cidadeUf
    val rua = ordem.cliente.endereco
    if (cidade == null && rua == null) return
    val corCidade = MaterialTheme.colorScheme.onSurface
    val texto = buildAnnotatedString {
        if (cidade != null) {
            withStyle(SpanStyle(color = corCidade, fontWeight = FontWeight.Medium)) { append(cidade) }
            if (rua != null) append(" · ")
        }
        if (rua != null) append(rua)
    }
    LinhaInfo(Icons.Outlined.Place, texto)
}

/** Quando: a hora marcada; sem hora, o prazo. */
fun textoQuando(ordem: Ordem, agora: LocalDateTime): String? {
    val hoje = agora.date
    return ordem.agendadaPara?.let { Datas.quando(it, hoje) }
        ?: ordem.prazoSla?.let { "Prazo: " + Datas.quando(it, hoje) }
}

/**
 * O cartão de uma OS na lista.
 *
 * Em cima, o que identifica (número e tipo) e a situação; no meio, o cliente
 * em letra grande — é por ele que o técnico procura; embaixo, onde e quando.
 * Faixa colorida na esquerda só para alta e urgente, para o olho achar sem
 * ler.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoOrdem(
    ordem: Ordem,
    agora: LocalDateTime,
    mostrarResponsavel: Boolean,
    aoAbrir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SelebiTema.cores
    val corFaixa = when {
        !ordem.aberta -> null
        ordem.prioridade == Prioridade.URGENTE -> c.perigo
        ordem.prioridade == Prioridade.ALTA -> c.alerta
        else -> null
    }
    Card(
        onClick = aoAbrir,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (corFaixa != null) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(corFaixa))
            }
            Row(
                Modifier.padding(start = if (corFaixa != null) 11.dp else 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconeTipo(ordem.tipo)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${ordem.numeroFormatado} · ${ordem.tipo.rotulo}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PilulaSituacao(ordem, agora)
                    }
                    Text(
                        ordem.cliente.nome,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinhaLugar(ordem)
                    // Quebra de linha em vez de cortar: tudo aqui é informação
                    // que o técnico precisa ver, nenhuma pode virar "…".
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (corFaixa != null) {
                            LinhaInfo(Icons.Outlined.PriorityHigh, ordem.prioridade.rotulo, cor = corFaixa)
                        }
                        textoQuando(ordem, agora)?.let {
                            LinhaInfo(
                                Icons.Outlined.Schedule,
                                it,
                                cor = if (ordem.atrasada(agora)) c.perigo else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // "0/3" numa OS que ninguém começou é ruído; o progresso
                        // passa a contar quando alguém põe a mão nela.
                        if (ordem.checklist.isNotEmpty() && (ordem.status.emCampo || ordem.checklistFeitos > 0)) {
                            LinhaInfo(Icons.Outlined.TaskAlt, "${ordem.checklistFeitos}/${ordem.checklist.size}")
                        }
                        if (mostrarResponsavel) {
                            val responsavel = ordem.responsavel
                            LinhaInfo(
                                Icons.Outlined.Person,
                                responsavel?.nome?.substringBefore(' ') ?: "Sem técnico",
                                cor = if (responsavel == null && ordem.aberta) c.alerta else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * O cartão grande do topo: a OS da vez.
 *
 * Os três botões são as três primeiras coisas que o técnico faz com ela —
 * abrir o mapa, ligar para o cliente avisando que está chegando, abrir a OS.
 * Grandes, embaixo, ao alcance do polegar.
 */
@Composable
fun CartaoDestaque(
    destaque: Destaque,
    agora: LocalDateTime,
    aoAbrir: () -> Unit,
    aoRota: () -> Unit,
    aoLigar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordem = destaque.ordem
    val hoje = agora.date
    val esquema = MaterialTheme.colorScheme
    val etiqueta = when (destaque.tipo) {
        TipoDestaque.AGORA -> if (ordem.status == StatusOs.EM_DESLOCAMENTO) "Agora · a caminho" else "Agora · em atendimento"
        TipoDestaque.PROXIMA -> "Próxima" + (ordem.agendadaPara?.let { " · " + Datas.quando(it, hoje) } ?: "")
        TipoDestaque.ATRASADA -> "Atrasada" + (
            (ordem.prazoSla ?: ordem.agendadaPara)?.let { " · desde " + Datas.rotuloDia(it.date, hoje).lowercase() } ?: ""
            )
    }
    val corEtiqueta = if (destaque.tipo == TipoDestaque.ATRASADA) SelebiTema.cores.perigo else esquema.primary

    Card(
        onClick = aoAbrir,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = esquema.primaryContainer, contentColor = esquema.onPrimaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pilula(etiqueta.uppercase(), fundo = corEtiqueta, frente = if (destaque.tipo == TipoDestaque.ATRASADA) Color.White else esquema.onPrimary)
                Spacer(Modifier.weight(1f))
                Text(ordem.numeroFormatado, style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconeTipo(ordem.tipo, tamanho = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(ordem.tipo.rotulo, style = MaterialTheme.typography.labelLarge)
                    Text(ordem.cliente.nome, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            ordem.enderecoCompleto?.let { LinhaInfo(Icons.Outlined.Place, it, cor = esquema.onPrimaryContainer, linhas = 3) }
            if (ordem.descricao.isNotBlank()) {
                Text(ordem.descricao, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val contorno = BorderStroke(1.dp, esquema.onPrimaryContainer.copy(alpha = 0.35f))
                val coresContorno = ButtonDefaults.outlinedButtonColors(contentColor = esquema.onPrimaryContainer)
                OutlinedButton(onClick = aoRota, border = contorno, colors = coresContorno, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rota")
                }
                if (ordem.cliente.telefone != null) {
                    OutlinedButton(onClick = aoLigar, border = contorno, colors = coresContorno, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ligar")
                    }
                }
                Button(onClick = aoAbrir, modifier = Modifier.weight(1f).height(48.dp)) { Text("Abrir") }
            }
        }
    }
}
