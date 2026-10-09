package br.com.selebi.app.funcionalidade.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.funcionalidade.ordens.CartaoOrdem
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.OrdensRepository
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.design.componentes.EstadoVazio
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Permissao
import br.com.selebi.app.nucleo.util.Datas
import br.com.selebi.app.nucleo.util.contarOs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class DiaDaSemana(val data: LocalDate, val quantidade: Int)

data class AgendaEstado(
    val selecionado: LocalDate = LocalDate.now(),
    val semana: List<DiaDaSemana> = emptyList(),
    val doDia: List<Ordem> = emptyList(),
    /** Abertas sem data — ficam na aba Ordens, a agenda só avisa que existem. */
    val semData: Int = 0,
    val mostrarResponsavel: Boolean = false,
    val agora: LocalDateTime = LocalDateTime.now(),
)

/**
 * A semana do técnico, dia a dia — a mesma lista de OS da tela inicial,
 * arrumada por data. É a vista de agenda que Jobber e Housecall Pro dão ao
 * técnico, sem precisar de nada novo no servidor.
 */
@HiltViewModel
class AgendaViewModel @Inject constructor(ordens: OrdensRepository, sessao: SessaoRepository) : ViewModel() {
    private val selecionado = MutableStateFlow(LocalDate.now())

    val estado: StateFlow<AgendaEstado> = combine(ordens.observarTodas(), selecionado, sessao.estado) { lista, dia, s ->
        val inicio = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val agendadas = lista.filter { it.agendadaPara != null }
        val porDia = agendadas.groupBy { it.agendadaPara!!.toLocalDate() }
        AgendaEstado(
            selecionado = dia,
            semana = (0L..6L).map { inicio.plusDays(it) }.map { DiaDaSemana(it, porDia[it]?.size ?: 0) },
            doDia = porDia[dia].orEmpty().sortedBy { it.agendadaPara },
            semData = lista.count { it.aberta && it.agendadaPara == null },
            mostrarResponsavel = (s as? EstadoSessao.Logado)?.usuario?.pode(Permissao.VER_TODAS_OS) == true,
            agora = LocalDateTime.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaEstado())

    fun selecionar(dia: LocalDate) {
        selecionado.value = dia
    }

    fun mudarSemana(semanas: Long) {
        selecionado.value = selecionado.value.plusWeeks(semanas)
    }

    fun irParaHoje() {
        selecionado.value = LocalDate.now()
    }
}

@Composable
fun AgendaTela(aoAbrir: (String) -> Unit, vm: AgendaViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    AgendaConteudo(estado, aoAbrir, vm::selecionar, vm::mudarSemana, vm::irParaHoje)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaConteudo(
    estado: AgendaEstado,
    aoAbrir: (String) -> Unit,
    aoSelecionar: (LocalDate) -> Unit,
    aoMudarSemana: (Long) -> Unit,
    aoIrParaHoje: () -> Unit,
) {
    val hoje = estado.agora.toLocalDate()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agenda")
                        Text(
                            Datas.mesEAno(estado.selecionado),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (estado.selecionado != hoje) TextButton(onClick = aoIrParaHoje) { Text("Hoje") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { espaco ->
        LazyColumn(
            Modifier.fillMaxSize().padding(espaco).consumeWindowInsets(espaco),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "semana") { FaixaSemana(estado, hoje, aoSelecionar, aoMudarSemana) }
            item(key = "titulo") {
                Text(
                    Datas.diaPorExtenso(estado.selecionado).replaceFirstChar { it.uppercase() } +
                        if (estado.doDia.isNotEmpty()) " · ${contarOs(estado.doDia.size)}" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (estado.doDia.isEmpty()) {
                item(key = "vazio") {
                    EstadoVazio(
                        Icons.Outlined.EventAvailable,
                        "Nada marcado neste dia",
                        if (estado.semData > 0) {
                            "Há ${contarOs(estado.semData)} em aberto sem data marcada — estão na aba Ordens."
                        } else {
                            "Escolha outro dia na faixa acima."
                        },
                    )
                }
            }
            items(estado.doDia, key = { it.id }) { ordem ->
                CartaoOrdem(ordem, estado.agora, estado.mostrarResponsavel, aoAbrir = { aoAbrir(ordem.id) })
            }
        }
    }
}

/** Os sete dias da semana, com um ponto onde há OS marcada. */
@Composable
private fun FaixaSemana(
    estado: AgendaEstado,
    hoje: LocalDate,
    aoSelecionar: (LocalDate) -> Unit,
    aoMudarSemana: (Long) -> Unit,
) {
    val esquema = MaterialTheme.colorScheme
    Surface(color = esquema.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { aoMudarSemana(-1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Semana anterior")
            }
            estado.semana.forEach { dia ->
                val selecionado = dia.data == estado.selecionado
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.small).padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        dia.data.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Datas.brasil).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = esquema.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(if (selecionado) Modifier.background(esquema.primary) else Modifier)
                            .then(if (!selecionado && dia.data == hoje) Modifier.border(1.5.dp, esquema.primary, CircleShape) else Modifier)
                            .clickable { aoSelecionar(dia.data) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${dia.data.dayOfMonth}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selecionado) esquema.onPrimary else esquema.onSurface,
                        )
                    }
                    Box(
                        Modifier.size(6.dp).background(
                            if (dia.quantidade > 0) SelebiTema.cores.info else esquema.surfaceContainerLowest,
                            CircleShape,
                        ),
                    )
                }
            }
            IconButton(onClick = { aoMudarSemana(1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Próxima semana")
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AgendaPreview() {
    SelebiTema {
        val ordens = Amostras.ordens()
        val dia = Amostras.agora.toLocalDate()
        AgendaConteudo(
            AgendaEstado(
                selecionado = dia,
                semana = (0L..6L).map { dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(it) }.map { d -> DiaDaSemana(d, ordens.count { it.agendadaPara?.toLocalDate() == d }) },
                doDia = ordens.filter { it.agendadaPara?.toLocalDate() == dia },
                agora = Amostras.agora,
            ),
            {}, {}, {}, {},
        )
    }
}
