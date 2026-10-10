package br.com.selebi.app.funcionalidade.ordens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SolarPower
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.plataforma.rememberAcoesExternas
import br.com.selebi.app.nucleo.vmDoNucleo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.funcionalidade.execucao.BarraDoPasso
import br.com.selebi.app.funcionalidade.execucao.BlocoRelatorio
import br.com.selebi.app.funcionalidade.execucao.FolhaFila
import br.com.selebi.app.funcionalidade.execucao.FolhaNota
import br.com.selebi.app.funcionalidade.execucao.Roteiro
import br.com.selebi.app.funcionalidade.execucao.SeloEnvio
import br.com.selebi.app.nucleo.arquivos.Miniaturas
import br.com.selebi.app.nucleo.dados.Conectividade
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.ExecucaoRepository
import br.com.selebi.app.nucleo.dados.OrdensRepository
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.design.componentes.AcaoRapida
import br.com.selebi.app.nucleo.design.componentes.BlocoDetalhe
import br.com.selebi.app.nucleo.design.componentes.BotaoPrincipal
import br.com.selebi.app.nucleo.design.componentes.EstadoVazio
import br.com.selebi.app.nucleo.design.componentes.ItemDetalhe
import br.com.selebi.app.nucleo.design.componentes.LocalCarregadorMiniatura
import br.com.selebi.app.nucleo.design.componentes.Pilula
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.AvisoOs
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Permissao
import br.com.selebi.app.nucleo.modelo.Prioridade
import br.com.selebi.app.nucleo.modelo.RegrasOs
import br.com.selebi.app.nucleo.modelo.SituacaoEnvio
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TipoResposta
import br.com.selebi.app.nucleo.modelo.Usuario
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.util.Datas
import br.com.selebi.app.nucleo.util.Numeros
import br.com.selebi.app.nucleo.util.Enderecos
import br.com.selebi.app.nucleo.util.formatarTelefone
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

data class OrdemDetalheEstado(
    val ordem: Ordem? = null,
    val atualizando: Boolean = true,
    /** O servidor disse que esta OS não é mais desta pessoa. */
    val saiu: Boolean = false,
    val mostrarResponsavel: Boolean = false,
    val agora: LocalDateTime = Datas.agoraRelogio(),
    val usuario: Usuario? = null,
    val envios: List<Envio> = emptyList(),
    val avisos: List<AvisoOs> = emptyList(),
    val online: Boolean = true,
) {
    /**
     * Quem atende pelo app: o responsável — ou qualquer um, se a OS ainda não
     * tem responsável (quem sai para atender fica com ela, como no servidor).
     */
    val executa: Boolean
        get() = ordem != null && ordem.aberta && (ordem.responsavel?.id.let { it == null || it == usuario?.id })
}

class OrdemDetalheViewModel(
    private val id: String,
    private val ordens: OrdensRepository,
    private val execucao: ExecucaoRepository,
    sessao: SessaoRepository,
    conectividade: Conectividade,
    val miniaturas: Miniaturas,
) : ViewModel() {
    private val atualizando = MutableStateFlow(true)
    private val saiu = MutableStateFlow(false)
    private val _mensagens = Channel<String>(Channel.BUFFERED)
    val mensagens: Flow<String> = _mensagens.receiveAsFlow()

    /** Mostra a cópia local na hora e confere no servidor por trás. */
    val estado: StateFlow<OrdemDetalheEstado> = combine(
        ordens.observar(id),
        combine(atualizando, saiu) { a, s -> a to s },
        sessao.estado,
        combine(execucao.envios(id), execucao.avisos(id)) { e, a -> e to a },
        conectividade.online,
    ) { ordem, (at, foi), s, (envios, avisos), online ->
        val usuario = (s as? EstadoSessao.Logado)?.usuario
        OrdemDetalheEstado(
            ordem = ordem,
            atualizando = at,
            saiu = foi,
            mostrarResponsavel = usuario?.pode(Permissao.VER_TODAS_OS) == true,
            agora = Datas.agoraRelogio(),
            usuario = usuario,
            envios = envios,
            avisos = avisos,
            online = online,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdemDetalheEstado())

    init {
        viewModelScope.launch {
            val resultado = ordens.atualizar(id)
            if (resultado is Resultado.Falha && (resultado.erro as? ErroApp.Servidor)?.status == 404) saiu.value = true
            atualizando.value = false
        }
    }

    fun anotar(texto: String, aoConseguir: () -> Unit) {
        val ordem = estado.value.ordem ?: return
        viewModelScope.launch {
            val erro = execucao.anotar(ordem, texto)
            if (erro == null) {
                aoConseguir()
                _mensagens.send("Nota guardada. O escritório vê assim que subir.")
            } else {
                _mensagens.send(erro)
            }
        }
    }

    fun descartar(envioId: String) {
        viewModelScope.launch { execucao.descartar(envioId) }
    }

    fun enviarAgora() = execucao.enviarAgora()

    fun dispensarAviso(avisoId: Long) {
        viewModelScope.launch { execucao.dispensarAviso(avisoId) }
    }
}

/** O que a tela de detalhe pode pedir — "não faz nada" de padrão para o Preview e as capturas. */
class AcoesDetalhe(
    val aoVoltar: () -> Unit = {},
    /** Abre o wizard; com item, direto nele. */
    val aoExecutar: (itemId: String?) -> Unit = {},
    val aoRota: () -> Unit = {},
    val aoLigar: () -> Unit = {},
    val aoWhatsapp: () -> Unit = {},
    val aoAnotar: (String, () -> Unit) -> Unit = { _, _ -> },
    val aoDescartar: (String) -> Unit = {},
    val aoEnviarAgora: () -> Unit = {},
    val aoDispensarAviso: (Long) -> Unit = {},
    val aoEnviarRelatorio: () -> Unit = {},
    val aoCompartilharRelatorio: () -> Unit = {},
    val aoAbrirRelatorio: () -> Unit = {},
)

@Composable
fun OrdemDetalheTela(
    id: String,
    aoVoltar: () -> Unit,
    aoExecutar: (id: String, itemId: String?) -> Unit,
    vm: OrdemDetalheViewModel = vmDoNucleo {
        OrdemDetalheViewModel(id, it.ordens, it.execucao, it.sessao, it.conectividade, it.miniaturas)
    },
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = rememberAcoesExternas()
    val rodape = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.mensagens.collect { rodape.showSnackbar(it) } }

    fun relatorio(): Pair<Ordem, String>? {
        val ordem = estado.ordem ?: return null
        return ordem.relatorio?.let { ordem to Enderecos.publico(it) }
    }
    val empresa = estado.usuario?.empresa?.nome

    CompositionLocalProvider(LocalCarregadorMiniatura provides vm.miniaturas) {
        OrdemDetalheConteudo(
            estado = estado,
            rodape = rodape,
            acoes = AcoesDetalhe(
                aoVoltar = aoVoltar,
                aoExecutar = { itemId -> estado.ordem?.let { aoExecutar(it.id, itemId) } },
                aoRota = { estado.ordem?.let { AcoesOs.abrirRota(contexto, it) } },
                aoLigar = { estado.ordem?.cliente?.telefone?.let { AcoesOs.ligar(contexto, it) } },
                aoWhatsapp = { estado.ordem?.cliente?.telefone?.let { AcoesOs.whatsapp(contexto, it) } },
                aoAnotar = vm::anotar,
                aoDescartar = vm::descartar,
                aoEnviarAgora = vm::enviarAgora,
                aoDispensarAviso = vm::dispensarAviso,
                aoEnviarRelatorio = {
                    relatorio()?.let { (ordem, link) ->
                        ordem.cliente.telefone?.let { AcoesOs.whatsapp(contexto, it, Roteiro.mensagemRelatorio(ordem, link, empresa)) }
                    }
                },
                aoCompartilharRelatorio = {
                    relatorio()?.let { (ordem, link) ->
                        AcoesOs.compartilhar(contexto, Roteiro.mensagemRelatorio(ordem, link, empresa), "Relatório da OS")
                    }
                },
                aoAbrirRelatorio = { relatorio()?.let { AcoesOs.abrirLink(contexto, it.second) } },
            ),
        )
    }
}

private enum class FolhaDetalhe { NENHUMA, NOTA, FILA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdemDetalheConteudo(
    estado: OrdemDetalheEstado,
    acoes: AcoesDetalhe,
    rodape: SnackbarHostState = remember { SnackbarHostState() },
) {
    val ordem = estado.ordem
    var folha by rememberSaveable { mutableStateOf(FolhaDetalhe.NENHUMA) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(ordem?.let { "OS ${it.numeroFormatado}" } ?: "Ordem de serviço") },
                    navigationIcon = {
                        IconButton(onClick = acoes.aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                    },
                    actions = {
                        if (ordem != null) {
                            if (estado.envios.isNotEmpty()) {
                                SeloEnvio(SituacaoEnvio.de(estado.envios), estado.online) { folha = FolhaDetalhe.FILA }
                            }
                            IconButton(onClick = { folha = FolhaDetalhe.NOTA }) {
                                Icon(Icons.Outlined.EditNote, contentDescription = "Nota para o escritório")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                // Fio de progresso fino enquanto confere com o servidor: a tela já
                // mostra a cópia local, então não trava nada.
                if (estado.atualizando && ordem != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = { if (ordem != null && estado.executa) BarraDoDetalhe(ordem, acoes) },
        snackbarHost = { SnackbarHost(rodape) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { espaco ->
        Box(Modifier.fillMaxSize().padding(espaco).consumeWindowInsets(espaco)) {
            when {
                ordem != null -> Detalhe(ordem, estado, acoes) { folha = it }
                estado.atualizando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> EstadoVazio(
                    Icons.Outlined.SearchOff,
                    if (estado.saiu) "Esta OS não está mais com você" else "OS não encontrada",
                    "Ela pode ter passado para outra pessoa ou sido apagada. Volte para a lista e atualize.",
                    acao = { TextButton(onClick = acoes.aoVoltar) { Text("Voltar para a lista") } },
                )
            }
        }
    }
    when (folha) {
        FolhaDetalhe.NOTA -> FolhaNota(
            aoSalvar = { texto -> acoes.aoAnotar(texto) { folha = FolhaDetalhe.NENHUMA } },
            aoFechar = { folha = FolhaDetalhe.NENHUMA },
        )
        FolhaDetalhe.FILA -> FolhaFila(estado.envios, ordem, estado.online, acoes.aoDescartar, acoes.aoEnviarAgora) {
            folha = FolhaDetalhe.NENHUMA
        }
        FolhaDetalhe.NENHUMA -> Unit
    }
}

/** O botão que leva ao atendimento, com o nome do próximo passo. */
@Composable
private fun BarraDoDetalhe(ordem: Ordem, acoes: AcoesDetalhe) {
    val (texto, icone) = when (ordem.status) {
        StatusOs.EM_DESLOCAMENTO -> "Cheguei no local" to Icons.Outlined.Place
        StatusOs.EM_ANDAMENTO -> (
            if (ordem.checklist.isEmpty()) "Continuar atendimento" else "Continuar · ${ordem.checklistFeitos} de ${ordem.checklist.size}"
            ) to Icons.Outlined.PlayArrow
        StatusOs.AGUARDANDO_PECA -> "Retomar atendimento" to Icons.Outlined.PlayArrow
        else -> "Começar atendimento" to Icons.Outlined.PlayArrow
    }
    BarraDoPasso {
        BotaoPrincipal(texto, { acoes.aoExecutar(null) }, icone = icone)
    }
}

@Composable
private fun Detalhe(ordem: Ordem, estado: OrdemDetalheEstado, acoes: AcoesDetalhe, abrirFolha: (FolhaDetalhe) -> Unit) {
    val agora = estado.agora
    val hoje = agora.date
    val c = SelebiTema.cores
    val telefone = ordem.cliente.telefone
    val situacao = SituacaoEnvio.de(estado.envios)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Cabeçalho: o que é, de quem, e em que pé está.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconeTipo(ordem.tipo, tamanho = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ordem.tipo.rotulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ordem.cliente.nome, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PilulaSituacao(ordem, agora)
            if (ordem.prioridade == Prioridade.URGENTE || ordem.prioridade == Prioridade.ALTA) {
                val urgente = ordem.prioridade == Prioridade.URGENTE
                Pilula(
                    "Prioridade ${ordem.prioridade.rotulo.lowercase()}",
                    fundo = if (urgente) c.perigoFundo else c.alertaFundo,
                    frente = if (urgente) c.sobrePerigo else c.sobreAlerta,
                )
            }
        }

        // O que só está neste celular, e o que o servidor avisou.
        if (!situacao.vazia) FaixaEnvios(situacao, estado.online) { abrirFolha(FolhaDetalhe.FILA) }
        estado.avisos.forEach { aviso ->
            Surface(color = c.infoFundo, contentColor = c.sobreInfo, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(aviso.texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                    TextButton(onClick = { acoes.aoDispensarAviso(aviso.id) }) { Text("Ok") }
                }
            }
        }

        if (ordem.descricao.isNotBlank()) {
            Text(ordem.descricao, style = MaterialTheme.typography.bodyLarge)
        }

        // Ações ao alcance do polegar.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AcaoRapida(Icons.Outlined.Directions, "Rota", acoes.aoRota, Modifier.weight(1f))
            AcaoRapida(Icons.Outlined.Call, "Ligar", acoes.aoLigar, Modifier.weight(1f), habilitada = telefone != null)
            AcaoRapida(Icons.AutoMirrored.Outlined.Chat, "WhatsApp", acoes.aoWhatsapp, Modifier.weight(1f), habilitada = telefone != null)
        }

        if (ordem.status == StatusOs.CONCLUIDA) {
            BlocoDetalhe("Como terminou") {
                val resolvido = ordem.resultado != "nao_resolvido"
                ItemDetalhe(
                    if (resolvido) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    "Resultado",
                    if (resolvido) "Resolvido" else "Não resolvido — precisa voltar",
                    corValor = if (resolvido) c.sucesso else c.alerta,
                )
                ordem.laudo?.takeIf { it.isNotBlank() }?.let { ItemDetalhe(Icons.Outlined.AssignmentTurnedIn, "Laudo", it) }
                ordem.assinaturaNome?.let { ItemDetalhe(Icons.Outlined.Person, "Assinado por", it) }
            }
            BlocoRelatorio(ordem, acoes.aoEnviarRelatorio, acoes.aoCompartilharRelatorio, acoes.aoAbrirRelatorio)
        }

        BlocoDetalhe("Cliente") {
            ItemDetalhe(Icons.Outlined.Person, "Nome", ordem.cliente.nome)
            ItemDetalhe(Icons.Outlined.Call, "Telefone", telefone?.let(::formatarTelefone) ?: "Não informado")
            ItemDetalhe(Icons.Outlined.Place, "Endereço", ordem.enderecoCompleto ?: "Não informado")
        }

        ordem.usina?.let { usina ->
            BlocoDetalhe("Usina") {
                ItemDetalhe(Icons.Outlined.SolarPower, "Nome no portal", usina.nome)
                usina.potenciaKwp?.let {
                    ItemDetalhe(Icons.Outlined.Bolt, "Potência", "${Numeros.fixo(it, 2)} kWp")
                }
            }
        }
        ordem.projeto?.let { projeto ->
            BlocoDetalhe("Projeto") { ItemDetalhe(Icons.Outlined.Folder, "Venda", projeto.titulo) }
        }

        BlocoDetalhe("Agenda") {
            ItemDetalhe(
                Icons.Outlined.Event,
                "Marcada para",
                ordem.agendadaPara?.let { Datas.quando(it, hoje) } ?: "Sem data marcada",
            )
            ordem.prazoSla?.let {
                ItemDetalhe(
                    Icons.Outlined.EventBusy,
                    "Prazo",
                    Datas.quando(it, hoje),
                    corValor = if (it < agora && ordem.aberta) c.perigo else MaterialTheme.colorScheme.onSurface,
                )
            }
            ordem.iniciadaEm?.let { ItemDetalhe(Icons.Outlined.PlayArrow, "Começou", Datas.instanteCurto(it)) }
            ordem.concluidaEm?.let { ItemDetalhe(Icons.Outlined.Flag, "Concluída", Datas.instanteCurto(it)) }
            ItemDetalhe(Icons.Outlined.AssignmentTurnedIn, "Aberta", Datas.instanteCurto(ordem.criadoEm))
        }

        if (estado.mostrarResponsavel) {
            BlocoDetalhe("Responsável") {
                ItemDetalhe(
                    Icons.Outlined.Person,
                    "Técnico",
                    ordem.responsavel?.nome ?: "Ninguém ainda",
                    corValor = if (ordem.responsavel == null && ordem.aberta) c.alerta else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (ordem.checklist.isNotEmpty()) BlocoChecklist(ordem, estado.executa, acoes)

        BlocoNotas(ordem, estado.usuario?.id) { abrirFolha(FolhaDetalhe.NOTA) }

        if (ordem.historico.isNotEmpty()) BlocoHistorico(ordem)

        if (ordem.status != StatusOs.CONCLUIDA) {
            ordem.laudo?.takeIf { it.isNotBlank() }?.let { laudo ->
                BlocoDetalhe("Laudo") { Text(laudo, style = MaterialTheme.typography.bodyLarge) }
            }
        }

        Box(Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun FaixaEnvios(situacao: SituacaoEnvio, online: Boolean, aoVer: () -> Unit) {
    val c = SelebiTema.cores
    val recusado = situacao.recusados > 0
    val (texto, icone) = when {
        recusado -> (if (situacao.recusados == 1) "O servidor recusou 1 envio" else "O servidor recusou ${situacao.recusados} envios") to Icons.Outlined.ErrorOutline
        !online -> "${situacao.esperando} ${if (situacao.esperando == 1) "alteração guardada" else "alterações guardadas"} no celular · sobe quando tiver sinal" to Icons.Outlined.CloudOff
        else -> "Subindo ${situacao.esperando} ${if (situacao.esperando == 1) "alteração" else "alterações"}…" to Icons.Outlined.CloudUpload
    }
    Surface(
        onClick = aoVer,
        color = if (recusado) c.perigoFundo else c.neutroFundo,
        contentColor = if (recusado) c.sobrePerigo else c.sobreNeutro,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("Ver", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * O checklist com as respostas, como o escritório lê no site. Com a OS nas
 * mãos, cada item leva direto ao passo dele no wizard.
 */
@Composable
private fun BlocoChecklist(ordem: Ordem, executa: Boolean, acoes: AcoesDetalhe) {
    val c = SelebiTema.cores
    BlocoDetalhe("Checklist · ${ordem.checklistFeitos} de ${ordem.checklist.size}") {
        ordem.checklist.forEach { item ->
            val fotos = ordem.fotosDo(item.id).size
            val respondido = item.valor != null || fotos > 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = executa) { acoes.aoExecutar(item.id) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (item.concluido) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (item.concluido) "Feito" else "Pendente",
                    tint = if (item.concluido) c.sucesso else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.descricao, style = MaterialTheme.typography.bodyLarge)
                    if (respondido) {
                        Text(
                            RegrasOs.textoDaResposta(item, fotos),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.obrigatorio && !item.concluido) {
                            Text("Obrigatório", style = MaterialTheme.typography.labelMedium, color = c.alerta)
                        }
                        if (fotos > 0 && item.tipoResposta != TipoResposta.FOTO) {
                            Rotulo(Icons.Outlined.PhotoCamera, if (fotos == 1) "1 foto" else "$fotos fotos")
                        }
                    }
                    item.observacao?.let {
                        Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (executa) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

@Composable
private fun Rotulo(icone: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** As notas da OS — as mesmas do site, nos dois sentidos. */
@Composable
private fun BlocoNotas(ordem: Ordem, meuId: String?, aoEscrever: () -> Unit) {
    BlocoDetalhe(if (ordem.notas.isEmpty()) "Notas" else "Notas · ${ordem.notas.size}") {
        if (ordem.notas.isEmpty()) {
            Text(
                "Recados entre você e o escritório ficam aqui, junto da OS.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ordem.notas.forEach { nota ->
            val minha = nota.autorId != null && nota.autorId == meuId
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(nota.texto, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        listOfNotNull(if (minha) "Você" else nota.autor ?: "Escritório", nota.criadoEm?.let(Datas::instanteCurto)).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (nota.pendente) Rotulo(Icons.Outlined.CloudUpload, "esperando sinal")
                }
            }
        }
        TextButton(onClick = aoEscrever, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Escrever nota", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** O histórico do site: quem fez o quê, quando, e por quê. Os cinco mais recentes, e o resto a um toque. */
@Composable
private fun BlocoHistorico(ordem: Ordem) {
    var tudo by rememberSaveable { mutableStateOf(false) }
    val eventos = if (tudo) ordem.historico else ordem.historico.take(5)
    BlocoDetalhe("Histórico") {
        eventos.forEach { e ->
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    e.titulo + (e.detalhe?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    listOfNotNull(
                        e.ocorridoEm?.let(Datas::instanteCurto),
                        e.quem,
                        when (e.origem) {
                            "app" -> "pelo app"
                            "web" -> "pelo site"
                            else -> null
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (ordem.historico.size > 5) {
            TextButton(onClick = { tudo = !tudo }, contentPadding = PaddingValues(0.dp)) {
                Text(if (tudo) "Mostrar menos" else "Ver o histórico todo (${ordem.historico.size})")
            }
        }
    }
}
