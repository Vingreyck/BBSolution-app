package br.com.selebi.app.funcionalidade.campo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import br.com.selebi.app.nucleo.plataforma.rememberAcoesExternas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.vmDoNucleo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.funcionalidade.ordens.AcoesOs
import br.com.selebi.app.funcionalidade.ordens.IconeTipo
import br.com.selebi.app.nucleo.dados.CampoRepository
import br.com.selebi.app.nucleo.dados.EventoCampo
import br.com.selebi.app.nucleo.design.componentes.EstadoVazio
import br.com.selebi.app.nucleo.design.componentes.Pilula
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Campo
import br.com.selebi.app.nucleo.modelo.Frescor
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TecnicoEmCampo
import br.com.selebi.app.nucleo.modelo.TrilhaCampo
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class EmCampoEstado(
    val carregando: Boolean = true,
    val tecnicos: List<TecnicoEmCampo> = emptyList(),
    /** O fluxo ao vivo está de pé; falso = só a consulta a cada 30 s. */
    val aoVivo: Boolean = false,
    val erro: String? = null,
    val selecionado: String? = null,
    val trilha: TrilhaCampo? = null,
    val agora: Instant = Datas.agoraMesmo(),
) {
    val escolhido: TecnicoEmCampo? get() = tecnicos.firstOrNull { it.ordemId == selecionado }
}

/**
 * O "Acompanhar técnico" no app de quem é da gestão. A lista vem a cada 30 s
 * (e quando uma OS muda); as posições chegam ao vivo. Só corre com a tela à
 * vista — o app em segundo plano não fica ouvindo o campo à toa.
 */
class EmCampoViewModel(private val campo: CampoRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EmCampoEstado())
    val estado: StateFlow<EmCampoEstado> = _estado.asStateFlow()

    private var recarga: Job? = null

    /** Roda enquanto a tela está à vista. */
    suspend fun acompanhar() = coroutineScope {
        launch {
            while (true) {
                recarregar()
                delay(30_000)
            }
        }
        launch { campo.aoVivo().collect(::aplicar) }
        launch {
            while (true) {
                delay(5_000)
                _estado.update { it.copy(agora = Datas.agoraMesmo()) }
            }
        }
        launch {
            while (true) {
                delay(30_000)
                _estado.value.selecionado?.let { carregarTrilha(it) }
            }
        }
    }

    suspend fun recarregar() {
        when (val r = campo.carregar()) {
            is Resultado.Ok -> _estado.update { e ->
                e.copy(
                    carregando = false,
                    tecnicos = r.valor,
                    erro = null,
                    selecionado = e.selecionado?.takeIf { s -> r.valor.any { it.ordemId == s } },
                    agora = Datas.agoraMesmo(),
                )
            }
            is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
        }
    }

    fun escolher(ordemId: String) {
        val novo = if (_estado.value.selecionado == ordemId) null else ordemId
        _estado.update { it.copy(selecionado = novo, trilha = null) }
        recarga?.cancel()
        if (novo != null) recarga = viewModelScope.launch { carregarTrilha(novo) }
    }

    private suspend fun carregarTrilha(ordemId: String) {
        val r = campo.trilha(ordemId)
        if (r is Resultado.Ok && _estado.value.selecionado == ordemId) _estado.update { it.copy(trilha = r.valor) }
    }

    private suspend fun aplicar(evento: EventoCampo) {
        when (evento) {
            EventoCampo.Conectado -> _estado.update { it.copy(aoVivo = true) }
            EventoCampo.Caiu -> _estado.update { it.copy(aoVivo = false) }
            EventoCampo.MudouOs -> recarregar()
            is EventoCampo.Posicao -> {
                val lista = _estado.value.tecnicos
                if (lista.none { it.ordemId == evento.ordemId }) {
                    // Alguém novo em campo: a lista inteira vem de novo.
                    recarregar()
                    return
                }
                _estado.update { e ->
                    e.copy(
                        tecnicos = e.tecnicos.map { t ->
                            if (t.ordemId != evento.ordemId) {
                                t
                            } else {
                                t.copy(
                                    posicao = evento.posicao,
                                    distanciaDestinoM = t.destino?.let { Campo.distanciaMetros(evento.posicao.coordenada, it).toInt() },
                                )
                            }
                        },
                        agora = Datas.agoraMesmo(),
                    )
                }
            }
        }
    }
}

@Composable
fun EmCampoTela(aoAbrirOs: (String) -> Unit, vm: EmCampoViewModel = vmDoNucleo { EmCampoViewModel(it.campo) }) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = rememberAcoesExternas()
    val dono = LocalLifecycleOwner.current
    LaunchedEffect(dono) { dono.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.acompanhar() } }
    EmCampoConteudo(
        estado = estado,
        aoEscolher = vm::escolher,
        aoAbrirOs = aoAbrirOs,
        aoAbrirNoMaps = { t ->
            t.posicao?.let { p ->
                AcoesOs.abrirLink(contexto, "https://www.google.com/maps/search/?api=1&query=${p.latitude},${p.longitude}")
            }
        },
        aoLigarCliente = { t -> t.telefone?.let { AcoesOs.ligar(contexto, it) } },
    )
}

private fun hora(instante: Instant): String =
    Datas.hora(instante.toLocalDateTime(TimeZone.currentSystemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmCampoConteudo(
    estado: EmCampoEstado,
    aoEscolher: (String) -> Unit,
    aoAbrirOs: (String) -> Unit,
    aoAbrirNoMaps: (TecnicoEmCampo) -> Unit,
    aoLigarCliente: (TecnicoEmCampo) -> Unit,
    mostrarMapa: Boolean = true,
) {
    var enquadrar by rememberSaveable { mutableIntStateOf(0) }
    val comGps = estado.tecnicos.count { it.posicao != null }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Em campo")
                        Text(
                            if (estado.carregando) "carregando…" else "${estado.tecnicos.size} em campo · $comGps com GPS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    SeloAoVivo(estado.aoVivo)
                    IconButton(onClick = { enquadrar++ }, enabled = comGps > 0) {
                        Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Ver todos no mapa")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { espaco ->
        Column(Modifier.fillMaxSize().padding(espaco).consumeWindowInsets(espaco)) {
            MapaCampo(
                tecnicos = estado.tecnicos,
                selecionado = estado.selecionado,
                trilha = estado.trilha,
                agora = estado.agora,
                enquadrar = enquadrar,
                aoEscolher = aoEscolher,
                mostrarMapa = mostrarMapa,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            estado.escolhido?.let { t -> AcoesDoEscolhido(t, estado.trilha, aoAbrirOs, aoAbrirNoMaps, aoLigarCliente) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(Modifier.fillMaxWidth().weight(1.1f)) {
                when {
                    estado.carregando && estado.tecnicos.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    estado.tecnicos.isEmpty() -> EstadoVazio(
                        Icons.Outlined.LocationOff,
                        "Ninguém em campo agora",
                        estado.erro ?: "Quando um técnico tocar em \"Estou a caminho\", ele aparece aqui e no mapa.",
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(estado.tecnicos, key = { it.ordemId }) { t ->
                            CartaoEmCampo(t, estado.agora, t.ordemId == estado.selecionado) { aoEscolher(t.ordemId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeloAoVivo(aoVivo: Boolean) {
    val c = SelebiTema.cores
    if (aoVivo) {
        Pilula("● ao vivo", c.sucessoFundo, c.sobreSucesso)
    } else {
        Pilula("↻ a cada 30 s", c.neutroFundo, c.sobreNeutro)
    }
}

/** O que dá para fazer com quem foi escolhido no mapa. */
@Composable
private fun AcoesDoEscolhido(
    t: TecnicoEmCampo,
    trilha: TrilhaCampo?,
    aoAbrirOs: (String) -> Unit,
    aoAbrirNoMaps: (TecnicoEmCampo) -> Unit,
    aoLigarCliente: (TecnicoEmCampo) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when {
                trilha == null -> "Carregando o trajeto…"
                trilha.trechos.isEmpty() -> "Sem trajeto gravado ainda"
                trilha.coladaNasRuas -> "Trajeto pelas ruas · ⌂ usina do cliente"
                else -> "Trajeto aproximado · ⌂ usina do cliente"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoDaLinha("OS ${t.numeroFormatado}", Icons.AutoMirrored.Outlined.OpenInNew, { aoAbrirOs(t.ordemId) }, Modifier.weight(1f), tonal = true)
            BotaoDaLinha("Maps", Icons.Outlined.Directions, { aoAbrirNoMaps(t) }, Modifier.weight(1f), habilitado = t.posicao != null)
            BotaoDaLinha("Cliente", Icons.Outlined.Call, { aoLigarCliente(t) }, Modifier.weight(1f), habilitado = t.telefone != null)
        }
    }
}

/**
 * Um dos três botões lado a lado. Cada um tem uns 90 dp numa tela estreita; a palavra
 * não quebra ("OS #004 / 9"), e o ícone sai quando falta largura.
 */
@Composable
private fun BotaoDaLinha(
    texto: String,
    icone: ImageVector,
    aoClicar: () -> Unit,
    modifier: Modifier,
    habilitado: Boolean = true,
    tonal: Boolean = false,
) {
    BoxWithConstraints(modifier) {
        val comIcone = maxWidth >= 104.dp * LocalDensity.current.fontScale
        val margem = PaddingValues(horizontal = if (comIcone) 8.dp else 4.dp)
        val conteudo: @Composable RowScope.() -> Unit = {
            if (comIcone) {
                Icon(icone, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(texto, maxLines = 1, softWrap = false)
        }
        val tamanho = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        if (tonal) {
            FilledTonalButton(onClick = aoClicar, enabled = habilitado, modifier = tamanho, contentPadding = margem, content = conteudo)
        } else {
            OutlinedButton(onClick = aoClicar, enabled = habilitado, modifier = tamanho, contentPadding = margem, content = conteudo)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoEmCampo(t: TecnicoEmCampo, agora: Instant, escolhido: Boolean, aoTocar: () -> Unit) {
    val c = SelebiTema.cores
    val esquema = MaterialTheme.colorScheme
    val p = t.posicao
    val frescor = p?.let { Campo.frescor(it.capturadoEm, it.modo, agora) } ?: Frescor.SEM_SINAL
    val corFrescor = when (frescor) {
        Frescor.AO_VIVO -> c.sucesso
        Frescor.ATRASADO -> c.alerta
        Frescor.SEM_SINAL -> c.perigo
    }
    Card(
        onClick = aoTocar,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = esquema.surfaceContainerLowest),
        border = BorderStroke(if (escolhido) 2.dp else 1.dp, if (escolhido) esquema.primary else esquema.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconeTipo(t.tipo)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.tecnico?.nome ?: "Sem técnico",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("● " + Campo.textoFrescor(p, agora), style = MaterialTheme.typography.labelMedium, color = corFrescor)
                }
                // Se não couber na linha, "#0049 · Vistoria" desce inteiro, em vez de quebrar letra por letra.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val caminho = t.status == StatusOs.EM_DESLOCAMENTO
                    Pilula(
                        t.status.rotulo,
                        if (caminho) esquema.primaryContainer else c.sucessoFundo,
                        if (caminho) esquema.onPrimaryContainer else c.sobreSucesso,
                    )
                    Text(
                        "${t.numeroFormatado} · ${t.tipo.rotulo}",
                        style = MaterialTheme.typography.labelLarge,
                        color = esquema.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Text(t.clienteNome, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detalhes = buildList {
                    add(listOfNotNull(t.cidade, t.uf).joinToString(" -⁠ ").ifBlank { "Sem cidade" })
                    t.distanciaDestinoM?.let { add("a ${Campo.metros(it)} do cliente") }
                    // Velocidade velha parece atual: sem sinal, não mostra.
                    if (frescor != Frescor.SEM_SINAL) p?.kmh?.let { add("$it km/h") }
                }
                Text(detalhes.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = esquema.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    t.desde?.let {
                        Text(
                            (if (t.status == StatusOs.EM_DESLOCAMENTO) "Saiu às " else "Chegou às ") + hora(it),
                            style = MaterialTheme.typography.labelMedium,
                            color = esquema.onSurfaceVariant,
                        )
                    }
                    p?.bateria?.let { b ->
                        Text(
                            "bateria $b%",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (b <= 15) c.perigo else esquema.onSurfaceVariant,
                            fontWeight = if (b <= 15) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
