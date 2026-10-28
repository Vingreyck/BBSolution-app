package br.com.selebi.app.funcionalidade.ordens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import br.com.selebi.app.nucleo.plataforma.rememberAcoesExternas
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.vmDoNucleo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.nucleo.dados.Conectividade
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.ExecucaoRepository
import br.com.selebi.app.nucleo.dados.OrdensRepository
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.rastreio.Rastreador
import br.com.selebi.app.nucleo.design.componentes.Aviso
import br.com.selebi.app.nucleo.design.componentes.Avatar
import br.com.selebi.app.nucleo.design.componentes.EstadoVazio
import br.com.selebi.app.nucleo.design.componentes.FaixaOffline
import br.com.selebi.app.nucleo.design.componentes.TipoAviso
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Permissao
import br.com.selebi.app.nucleo.modelo.SituacaoEnvio
import br.com.selebi.app.nucleo.modelo.Usuario
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime

data class OrdensEstado(
    val carregando: Boolean = true,
    val sincronizando: Boolean = false,
    val usuario: Usuario? = null,
    val painel: Painel = Painel(),
    val filtro: FiltroOrdens = FiltroOrdens.TODAS,
    val escopo: EscopoOrdens = EscopoOrdens.EQUIPE,
    val busca: String = "",
    val online: Boolean = true,
    val sincronizadoEm: Instant? = null,
    /** Erro da última sincronização — "sem conexão" não entra, a faixa já diz. */
    val erro: String? = null,
    val agora: LocalDateTime = Datas.agoraRelogio(),
    /** O que ainda está só neste celular. */
    val envios: SituacaoEnvio = SituacaoEnvio(),
    /** A OS do primeiro envio recusado — o toque na faixa leva até ela. */
    val ordemRecusada: String? = null,
) {
    val podeVerTodas: Boolean get() = usuario?.pode(Permissao.VER_TODAS_OS) == true
}

private data class Entradas(val filtro: FiltroOrdens, val escopo: EscopoOrdens, val busca: String)
private data class Rede(
    val sincronizando: Boolean,
    val erro: String?,
    val online: Boolean,
    val em: Instant?,
    val envios: List<Envio>,
)

class OrdensViewModel(
    private val sessao: SessaoRepository,
    private val ordens: OrdensRepository,
    private val execucao: ExecucaoRepository,
    private val rastreador: Rastreador,
    conectividade: Conectividade,
) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroOrdens.TODAS)
    private val escopo = MutableStateFlow(EscopoOrdens.EQUIPE)
    private val busca = MutableStateFlow("")
    private val sincronizando = MutableStateFlow(false)
    private val erro = MutableStateFlow<String?>(null)

    /** Uma batida por minuto: é o que faz uma OS virar "atrasada" com a tela aberta. */
    private val relogio = flow {
        while (true) {
            emit(Datas.agoraRelogio())
            delay(60_000)
        }
    }

    private val usuario = sessao.estado.map { (it as? EstadoSessao.Logado)?.usuario }

    val estado: StateFlow<OrdensEstado> = combine(
        usuario,
        ordens.observarTodas(),
        combine(filtro, escopo, busca) { f, e, b -> Entradas(f, e, b) },
        combine(sincronizando, erro, conectividade.online, ordens.sincronizadoEm, execucao.todosEnvios()) { s, e, o, em, envios ->
            Rede(s, e, o, em, envios)
        },
        relogio,
    ) { u, lista, entradas, rede, agora ->
        val veTodas = u?.pode(Permissao.VER_TODAS_OS) == true
        OrdensEstado(
            carregando = false,
            sincronizando = rede.sincronizando,
            usuario = u,
            painel = montarPainel(
                ordens = lista,
                usuarioId = u?.id,
                escopo = if (veTodas) entradas.escopo else EscopoOrdens.EQUIPE,
                filtro = entradas.filtro,
                busca = entradas.busca,
                agora = agora,
            ),
            filtro = entradas.filtro,
            escopo = entradas.escopo,
            busca = entradas.busca,
            online = rede.online,
            sincronizadoEm = rede.em,
            erro = rede.erro,
            agora = agora,
            envios = SituacaoEnvio.de(rede.envios),
            ordemRecusada = rede.envios.firstOrNull { it.recusado }?.ordemId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdensEstado())

    init {
        sincronizar()
        // Voltou o sinal: atualiza sozinho, sem esperar o técnico puxar a tela.
        viewModelScope.launch {
            conectividade.online.drop(1).filter { it }.collect { sincronizar() }
        }
    }

    fun sincronizar() {
        if (sincronizando.value) return
        sincronizando.value = true
        viewModelScope.launch {
            val resultado = ordens.sincronizar()
            erro.value = (resultado as? Resultado.Falha)?.erro
                ?.takeUnless { it is ErroApp.SemConexao }
                ?.mensagem
            sincronizando.value = false
            // Retoma o rastreamento que o Android interrompeu, ou desliga o de
            // uma OS que saiu de campo — com ou sem rede, pela cópia local.
            rastreador.conferir(ordens.observarTodas().first(), sessao.usuario)
            // O papel pode ter mudado desde o login — aproveita a ida ao servidor.
            if (resultado is Resultado.Ok) sessao.atualizarPerfil()
        }
    }

    fun enviarAgora() = execucao.enviarAgora()

    fun definirFiltro(novo: FiltroOrdens) {
        filtro.value = novo
    }

    fun definirEscopo(novo: EscopoOrdens) {
        escopo.value = novo
    }

    fun definirBusca(texto: String) {
        busca.value = texto.take(80)
    }
}

@Composable
fun OrdensTela(aoAbrir: (String) -> Unit, aoAbrirPerfil: () -> Unit, vm: OrdensViewModel = vmDoNucleo { OrdensViewModel(it.sessao, it.ordens, it.execucao, it.rastreador, it.conectividade) }) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = rememberAcoesExternas()
    OrdensConteudo(
        estado = estado,
        aoAbrir = aoAbrir,
        aoAbrirPerfil = aoAbrirPerfil,
        aoAtualizar = vm::sincronizar,
        aoFiltrar = vm::definirFiltro,
        aoMudarEscopo = vm::definirEscopo,
        aoBuscar = vm::definirBusca,
        aoRota = { AcoesOs.abrirRota(contexto, it) },
        aoLigar = { ordem -> ordem.cliente.telefone?.let { AcoesOs.ligar(contexto, it) } },
        aoVerEnvios = { estado.ordemRecusada?.let(aoAbrir) ?: vm.enviarAgora() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdensConteudo(
    estado: OrdensEstado,
    aoAbrir: (String) -> Unit,
    aoAbrirPerfil: () -> Unit,
    aoAtualizar: () -> Unit,
    aoFiltrar: (FiltroOrdens) -> Unit,
    aoMudarEscopo: (EscopoOrdens) -> Unit,
    aoBuscar: (String) -> Unit,
    aoRota: (Ordem) -> Unit,
    aoLigar: (Ordem) -> Unit,
    aoVerEnvios: () -> Unit = {},
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { espaco ->
        PullToRefreshBox(
            isRefreshing = estado.sincronizando,
            onRefresh = aoAtualizar,
            modifier = Modifier.fillMaxSize().padding(espaco).consumeWindowInsets(espaco),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "cabecalho") { Cabecalho(estado, aoAbrirPerfil, aoAtualizar) }
                if (!estado.online) {
                    item(key = "offline") { FaixaOffline(estado.sincronizadoEm?.let { Datas.instanteCurto(it) }) }
                }
                if (!estado.envios.vazia) {
                    item(key = "envios") { FaixaFila(estado.envios, estado.online, aoVerEnvios) }
                }
                estado.erro?.let { item(key = "erro") { Aviso(it, TipoAviso.ALERTA) } }
                item(key = "resumo") { ResumoDoDia(estado.painel.contagem, estado.filtro, aoFiltrar) }
                if (estado.podeVerTodas) {
                    item(key = "escopo") { SeletorEscopo(estado.escopo, aoMudarEscopo) }
                }
                item(key = "busca") { CampoBusca(estado.busca, aoBuscar) }
                if (estado.filtro != FiltroOrdens.TODAS && estado.busca.isBlank()) {
                    item(key = "filtro") {
                        InputChip(
                            selected = true,
                            onClick = { aoFiltrar(FiltroOrdens.TODAS) },
                            label = { Text("Mostrando: ${estado.filtro.rotulo}") },
                            trailingIcon = {
                                Icon(Icons.Outlined.Close, contentDescription = "Tirar filtro", modifier = Modifier.size(InputChipDefaults.IconSize))
                            },
                        )
                    }
                }
                estado.painel.destaque?.let { destaque ->
                    item(key = "destaque") {
                        CartaoDestaque(
                            destaque = destaque,
                            agora = estado.agora,
                            aoAbrir = { aoAbrir(destaque.ordem.id) },
                            aoRota = { aoRota(destaque.ordem) },
                            aoLigar = { aoLigar(destaque.ordem) },
                        )
                    }
                }
                estado.painel.secoes.filter { it.ordens.isNotEmpty() }.forEach { secao ->
                    item(key = "secao-${secao.chave}") { CabecalhoSecao(secao) }
                    items(secao.ordens, key = { it.id }) { ordem ->
                        CartaoOrdem(
                            ordem = ordem,
                            agora = estado.agora,
                            mostrarResponsavel = estado.podeVerTodas,
                            aoAbrir = { aoAbrir(ordem.id) },
                        )
                    }
                }
                if (!estado.carregando && estado.painel.vazio) {
                    item(key = "vazio") { Vazio(estado) }
                }
                val concluidas = estado.painel.contagem.concluidas
                if (estado.filtro == FiltroOrdens.TODAS && estado.busca.isBlank() && concluidas > 0) {
                    item(key = "concluidas") { AtalhoConcluidas(concluidas) { aoFiltrar(FiltroOrdens.CONCLUIDAS) } }
                }
            }
        }
    }
}

/**
 * O que ainda está só neste celular. Recusa do servidor leva à OS; o resto
 * só informa — sobe sozinho, e o toque tenta de novo na hora.
 */
@Composable
private fun FaixaFila(situacao: SituacaoEnvio, online: Boolean, aoTocar: () -> Unit) {
    val c = SelebiTema.cores
    val recusado = situacao.recusados > 0
    val n = situacao.esperando
    val texto = when {
        recusado && situacao.recusados == 1 -> "O servidor recusou 1 envio. Toque para ver."
        recusado -> "O servidor recusou ${situacao.recusados} envios. Toque para ver."
        online && n == 1 -> "Subindo 1 alteração feita em campo…"
        online -> "Subindo $n alterações feitas em campo…"
        n == 1 -> "1 alteração guardada no celular · sobe quando o sinal voltar"
        else -> "$n alterações guardadas no celular · sobem quando o sinal voltar"
    }
    Surface(
        onClick = aoTocar,
        color = if (recusado) c.perigoFundo else c.neutroFundo,
        contentColor = if (recusado) c.sobrePerigo else c.sobreNeutro,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    recusado -> Icons.Outlined.ErrorOutline
                    online -> Icons.Outlined.CloudUpload
                    else -> Icons.Outlined.CloudOff
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(texto, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Cabecalho(estado: OrdensEstado, aoAbrirPerfil: () -> Unit, aoAtualizar: () -> Unit) {
    val usuario = estado.usuario
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(Datas.saudacao(estado.agora.hour), usuario?.primeiroNome).joinToString(", "),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                listOfNotNull(Datas.diaPorExtenso(estado.agora.date), usuario?.empresa?.nome).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Sempre à vista: o SeeNet aprendeu que o técnico procura o "atualizar"
        // quando acha que uma OS nova não chegou.
        IconButton(onClick = aoAtualizar, enabled = !estado.sincronizando) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar")
        }
        if (usuario != null) {
            Avatar(usuario.nome, usuario.papel, Modifier.clickable(onClick = aoAbrirPerfil), tamanho = 42.dp)
        }
    }
}

@Composable
private fun ResumoDoDia(contagem: Contagem, filtro: FiltroOrdens, aoFiltrar: (FiltroOrdens) -> Unit) {
    val c = SelebiTema.cores
    fun alternar(f: FiltroOrdens) = aoFiltrar(if (filtro == f) FiltroOrdens.TODAS else f)
    // Altura única: "Em campo" pode ocupar duas linhas e os três cartões continuam iguais.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BlocoResumo(
            "Hoje", contagem.hoje, Icons.Outlined.Today, filtro == FiltroOrdens.HOJE,
            MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).fillMaxHeight(),
        ) { alternar(FiltroOrdens.HOJE) }
        BlocoResumo(
            "Atrasadas", contagem.atrasadas, Icons.Outlined.WarningAmber, filtro == FiltroOrdens.ATRASADAS,
            if (contagem.atrasadas > 0) c.perigo else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).fillMaxHeight(),
        ) { alternar(FiltroOrdens.ATRASADAS) }
        BlocoResumo(
            "Em campo", contagem.emCampo, Icons.Outlined.Engineering, filtro == FiltroOrdens.EM_CAMPO,
            if (contagem.emCampo > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).fillMaxHeight(),
        ) { alternar(FiltroOrdens.EM_CAMPO) }
    }
}

/** Número grande que também é filtro — tocar mostra só aquelas; tocar de novo volta. */
@Composable
private fun BlocoResumo(
    rotulo: String,
    valor: Int,
    icone: ImageVector,
    selecionado: Boolean,
    corValor: Color,
    modifier: Modifier,
    aoClicar: () -> Unit,
) {
    val esquema = MaterialTheme.colorScheme
    Surface(
        onClick = aoClicar,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selecionado) esquema.primaryContainer else esquema.surfaceContainerLowest,
        border = if (selecionado) BorderStroke(2.dp, esquema.primary) else BorderStroke(1.dp, esquema.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Icon(icone, contentDescription = null, tint = if (valor > 0) corValor else esquema.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text("$valor", style = MaterialTheme.typography.headlineMedium, color = corValor)
            // "Atrasadas" partido ao meio ("Atrasad / as") é pior que a letra um pouco menor.
            val letraGrande = LocalDensity.current.fontScale > 1.15f
            Text(
                rotulo,
                style = if (letraGrande) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = esquema.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorEscopo(escopo: EscopoOrdens, aoMudar: (EscopoOrdens) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Cada lado precisa de uns 50 dp + a largura do texto, que cresce com a letra do
        // sistema. Se não couber, sai o "✓" e a letra encolhe — e a palavra não é cortada.
        val apertado = maxWidth < (100 + 192 * LocalDensity.current.fontScale).dp
        val estilo = if (apertado) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = escopo == EscopoOrdens.EQUIPE,
                onClick = { aoMudar(EscopoOrdens.EQUIPE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { if (!apertado) SegmentedButtonDefaults.Icon(escopo == EscopoOrdens.EQUIPE) },
            ) { Text("Toda a equipe", style = estilo, maxLines = 1, softWrap = false) }
            SegmentedButton(
                selected = escopo == EscopoOrdens.MINHAS,
                onClick = { aoMudar(EscopoOrdens.MINHAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { if (!apertado) SegmentedButtonDefaults.Icon(escopo == EscopoOrdens.MINHAS) },
            ) { Text("Só as minhas", style = estilo, maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun CampoBusca(texto: String, aoMudar: (String) -> Unit) {
    val esquema = MaterialTheme.colorScheme
    OutlinedTextField(
        value = texto,
        onValueChange = aoMudar,
        placeholder = { Text("Buscar cliente, nº da OS ou cidade") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (texto.isNotEmpty()) {
                IconButton(onClick = { aoMudar("") }) { Icon(Icons.Outlined.Close, contentDescription = "Limpar busca") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = esquema.surfaceContainerLowest,
            focusedContainerColor = esquema.surfaceContainerLowest,
            unfocusedBorderColor = esquema.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CabecalhoSecao(secao: Secao) {
    val cor = when (secao.tom) {
        TomSecao.PERIGO -> SelebiTema.cores.perigo
        TomSecao.PRIMARIO -> MaterialTheme.colorScheme.primary
        TomSecao.NORMAL -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(secao.titulo, style = MaterialTheme.typography.titleSmall, color = cor)
        Spacer(Modifier.width(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
            Text(
                "${secao.ordens.size}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Vazio(estado: OrdensEstado) {
    when {
        estado.busca.isNotBlank() -> EstadoVazio(
            Icons.Outlined.SearchOff,
            "Nada encontrado",
            "Nenhuma OS com “${estado.busca.trim()}”. Tente o nome do cliente, o número ou a cidade.",
        )
        estado.filtro != FiltroOrdens.TODAS -> EstadoVazio(
            Icons.Outlined.TaskAlt,
            "Nada em “${estado.filtro.rotulo}”",
            "Quando houver, aparece aqui. Toque em Mostrando para voltar à lista toda.",
        )
        else -> EstadoVazio(
            Icons.AutoMirrored.Outlined.Assignment,
            if (estado.podeVerTodas) "Nenhuma OS em aberto" else "Nenhuma OS com você",
            if (estado.podeVerTodas) {
                "As ordens de serviço da empresa aparecem aqui assim que forem abertas. Puxe a tela para baixo para atualizar."
            } else {
                "Quando uma ordem de serviço for passada para você, ela aparece aqui. Puxe a tela para baixo para atualizar."
            },
        )
    }
}

@Composable
private fun AtalhoConcluidas(quantidade: Int, aoClicar: () -> Unit) {
    Surface(
        onClick = aoClicar,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = SelebiTema.cores.sucesso)
            Spacer(Modifier.width(12.dp))
            Text(
                "Concluídas nos últimos 30 dias",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text("$quantidade", style = MaterialTheme.typography.titleSmall)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}
