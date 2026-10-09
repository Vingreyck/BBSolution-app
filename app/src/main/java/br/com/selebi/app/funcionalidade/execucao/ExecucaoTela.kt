package br.com.selebi.app.funcionalidade.execucao

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.selebi.app.funcionalidade.ordens.AcoesOs
import br.com.selebi.app.nucleo.design.componentes.EstadoVazio
import br.com.selebi.app.nucleo.design.componentes.LocalCarregadorMiniatura
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.AcaoOs
import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.RegrasOs
import br.com.selebi.app.nucleo.modelo.Resposta
import br.com.selebi.app.nucleo.modelo.SituacaoEnvio
import br.com.selebi.app.nucleo.util.Enderecos
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Tudo o que a tela do wizard pode pedir. Num objeto só, com "não faz nada"
 * de padrão: a captura de tela e o Preview montam a tela sem ViewModel.
 */
class AcoesExecucao(
    val aoFechar: () -> Unit = {},
    val aoVoltarParaLista: () -> Unit = {},
    val aoIrPara: (Int) -> Unit = {},
    val aoProximo: () -> Unit = {},
    val aoAnterior: () -> Unit = {},
    /** Da chegada para o checklist: o primeiro que falta. */
    val aoContinuar: () -> Unit = {},
    val aoResponder: (ItemChecklist, Resposta?) -> Unit = { _, _ -> },
    val aoObservar: (ItemChecklist, String) -> Unit = { _, _ -> },
    val aoCamera: (String?) -> Unit = {},
    val aoGaleria: (String?) -> Unit = {},
    val aoRemoverFoto: (AnexoOs) -> Unit = {},
    val aoLerCodigo: LeitorDeCodigo = { it(null) },
    val aoAgir: (AcaoOs, String?) -> Unit = { _, _ -> },
    val aoPausar: () -> Unit = {},
    val aoNaoAtendida: () -> Unit = {},
    val aoMudarConclusao: (FormConclusao) -> Unit = {},
    val aoAbrirAssinatura: () -> Unit = {},
    val aoAssinar: (Bitmap, String) -> Unit = { _, _ -> },
    val aoConcluir: () -> Unit = {},
    val aoAnotar: (String, () -> Unit) -> Unit = { _, _ -> },
    val aoDescartar: (String) -> Unit = {},
    val aoEnviarAgora: () -> Unit = {},
    val aoDispensarAviso: (Long) -> Unit = {},
    val aoRota: () -> Unit = {},
    val aoLigar: () -> Unit = {},
    val aoAvisarACaminho: () -> Unit = {},
    val aoEnviarRelatorio: () -> Unit = {},
    val aoCompartilharRelatorio: () -> Unit = {},
    val aoAbrirRelatorio: () -> Unit = {},
    val aoPedirLocalizacao: () -> Unit = {},
)

/** O que está aberto por cima do passo: folhas, visor, assinatura. */
enum class Camada { NADA, INDICE, FILA, NOTA, PAUSA, NAO_ATENDIDA, ASSINATURA }

@Composable
fun ExecucaoTela(aoFechar: () -> Unit, aoVoltarParaLista: () -> Unit, vm: ExecucaoViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val rodape = remember { SnackbarHostState() }
    var camada by rememberSaveable { mutableStateOf(Camada.NADA) }

    fun temLocalizacao() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED }

    var localizacao by remember { mutableStateOf(temLocalizacao()) }
    var acaoEsperandoPermissao by rememberSaveable { mutableStateOf<AcaoOs?>(null) }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        localizacao = r.filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }.values.any { it } || temLocalizacao()
        // Com ou sem permissão, a ação segue — o GPS é bom de ter, não trava nada.
        acaoEsperandoPermissao?.let { vm.agir(it) }
        acaoEsperandoPermissao = null
    }
    fun pedirLocalizacao(depois: AcaoOs?) {
        acaoEsperandoPermissao = depois
        // A notificação é o aviso de que o rastreamento está ligado: pede junto.
        val notificacao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        permissao.launch((listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) + notificacao).toTypedArray())
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.fotoTirada(ok) }
    var itemDaGaleria by rememberSaveable { mutableStateOf<String?>(null) }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.fotoDaGaleria(itemDaGaleria?.ifEmpty { null }, uri)
        itemDaGaleria = null
    }

    fun avisarACaminho() {
        val ordem = estado.ordem ?: return
        val telefone = ordem.cliente.telefone ?: return
        val u = estado.usuario
        AcoesOs.whatsapp(contexto, telefone, Roteiro.mensagemACaminho(ordem, u?.nome, u?.empresa?.nome))
    }

    fun linkDoRelatorio(): String? = estado.ordem?.relatorio?.let { Enderecos.publico(it) }

    LaunchedEffect(Unit) {
        vm.mensagens.collect { evento ->
            when (evento) {
                is EventoExecucao.Mensagem -> escopo.launch {
                    val r = rodape.showSnackbar(
                        evento.texto,
                        actionLabel = if (evento.avisarCliente) "Avisar" else null,
                        withDismissAction = !evento.avisarCliente,
                        duration = if (evento.avisarCliente) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed && evento.avisarCliente) avisarACaminho()
                }
                EventoExecucao.Fechar -> aoFechar()
            }
        }
    }

    val acoes = AcoesExecucao(
        aoFechar = aoFechar,
        aoVoltarParaLista = aoVoltarParaLista,
        aoIrPara = vm::irPara,
        aoProximo = vm::proximo,
        aoAnterior = vm::anterior,
        aoContinuar = vm::irParaPendente,
        aoResponder = vm::responder,
        aoObservar = vm::observar,
        aoCamera = { itemId -> camera.launch(vm.prepararCamera(itemId)) },
        aoGaleria = { itemId ->
            itemDaGaleria = itemId.orEmpty()
            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        aoRemoverFoto = vm::removerFoto,
        aoLerCodigo = { aoLer ->
            val opcoes = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93,
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_ITF,
                )
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(contexto, opcoes).startScan()
                .addOnSuccessListener { aoLer(it.rawValue) }
                .addOnCanceledListener { aoLer(null) }
                .addOnFailureListener {
                    aoLer(null)
                    escopo.launch { rodape.showSnackbar("O leitor não abriu neste celular. Digite o número da etiqueta.") }
                }
        },
        aoAgir = { acao, motivo ->
            val checkIn = acao == AcaoOs.DESLOCAMENTO || acao == AcaoOs.INICIAR
            if (checkIn && !localizacao) pedirLocalizacao(acao) else vm.agir(acao, motivo)
        },
        aoPausar = { camada = Camada.PAUSA },
        aoNaoAtendida = { camada = Camada.NAO_ATENDIDA },
        aoMudarConclusao = vm::mudarConclusao,
        aoAbrirAssinatura = { camada = Camada.ASSINATURA },
        aoAssinar = { desenho, nome ->
            vm.mudarConclusao(estado.conclusao.copy(assinante = nome))
            vm.assinar(desenho)
        },
        aoConcluir = vm::concluir,
        aoAnotar = vm::anotar,
        aoDescartar = { vm.descartar(it) },
        aoEnviarAgora = vm::enviarAgora,
        aoDispensarAviso = { vm.dispensarAviso(it) },
        aoRota = { estado.ordem?.let { AcoesOs.abrirRota(contexto, it) } },
        aoLigar = { estado.ordem?.cliente?.telefone?.let { AcoesOs.ligar(contexto, it) } },
        aoAvisarACaminho = ::avisarACaminho,
        aoEnviarRelatorio = {
            val ordem = estado.ordem
            val link = linkDoRelatorio()
            val telefone = ordem?.cliente?.telefone
            if (ordem != null && link != null && telefone != null) {
                AcoesOs.whatsapp(contexto, telefone, Roteiro.mensagemRelatorio(ordem, link, estado.usuario?.empresa?.nome))
            }
        },
        aoCompartilharRelatorio = {
            val ordem = estado.ordem
            val link = linkDoRelatorio()
            if (ordem != null && link != null) {
                AcoesOs.compartilhar(contexto, Roteiro.mensagemRelatorio(ordem, link, estado.usuario?.empresa?.nome), "Relatório da OS")
            }
        },
        aoAbrirRelatorio = { linkDoRelatorio()?.let { AcoesOs.abrirLink(contexto, it) } },
        aoPedirLocalizacao = { pedirLocalizacao(null) },
    )

    CompositionLocalProvider(LocalCarregadorMiniatura provides vm.miniaturas) {
        ExecucaoConteudo(
            estado = estado,
            acoes = acoes,
            localizacaoLiberada = localizacao,
            rodape = rodape,
            camada = camada,
            aoMudarCamada = { camada = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecucaoConteudo(
    estado: ExecucaoEstado,
    acoes: AcoesExecucao,
    localizacaoLiberada: Boolean = true,
    rodape: SnackbarHostState = remember { SnackbarHostState() },
    camada: Camada = Camada.NADA,
    aoMudarCamada: (Camada) -> Unit = {},
    agora: LocalDateTime = LocalDateTime.now(),
) {
    val ordem = estado.ordem
    var fotoAberta by remember { mutableStateOf<AnexoOs?>(null) }
    val passo = estado.passoAtual
    val concluida = estado.concluidaAqui

    // O "voltar" do Android anda para trás no wizard antes de sair dele.
    BackHandler(enabled = !concluida && estado.passo > 0) { acoes.aoAnterior() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = if (concluida) acoes.aoVoltarParaLista else acoes.aoFechar) {
                            Icon(Icons.Outlined.Close, contentDescription = "Fechar")
                        }
                    },
                    title = {
                        if (ordem != null) {
                            Column {
                                Text("${ordem.tipo.rotulo} ${ordem.numeroFormatado}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    ordem.cliente.nome,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        if (ordem != null) {
                            SeloEnvio(SituacaoEnvio.de(estado.envios), estado.online) { aoMudarCamada(Camada.FILA) }
                            if (ordem.checklist.isNotEmpty() && !concluida) {
                                IconButton(onClick = { aoMudarCamada(Camada.INDICE) }) {
                                    Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = "Todos os itens")
                                }
                            }
                            MenuDaExecucao(estado, acoes, aoMudarCamada)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                if (ordem != null && passo != null && !concluida && passo != Passo.Chegada) {
                    Progresso(estado)
                }
            }
        },
        bottomBar = {
            if (ordem != null && passo != null) {
                when {
                    concluida -> BarraConcluida(acoes)
                    passo is Passo.Chegada -> BarraChegada(estado, acoes)
                    passo is Passo.Item -> ordem.checklist.firstOrNull { it.id == passo.itemId }?.let { BarraItem(estado, it, acoes) }
                    passo is Passo.Conclusao -> BarraConclusao(estado, acoes)
                }
            }
        },
        snackbarHost = { SnackbarHost(rodape) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { espaco ->
        Box(Modifier.fillMaxSize().padding(espaco)) {
            when {
                !estado.carregado -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ordem == null -> EstadoVazio(
                    Icons.Outlined.SearchOff,
                    "Esta OS não está mais com você",
                    "Ela pode ter passado para outra pessoa ou sido cancelada. O que você fez e ainda não subiu continua guardado.",
                    acao = { TextButton(onClick = acoes.aoFechar) { Text("Voltar") } },
                )
                concluida -> ConteudoConcluida(estado, acoes)
                passo != null -> AnimatedContent(
                    targetState = estado.passo,
                    transitionSpec = {
                        val frente = targetState > initialState
                        (slideInHorizontally(tween(220)) { if (frente) it / 5 else -it / 5 } + fadeIn(tween(220)))
                            .togetherWith(slideOutHorizontally(tween(160)) { if (frente) -it / 5 else it / 5 } + fadeOut(tween(160)))
                    },
                    label = "passo",
                ) { indice ->
                    when (val p = estado.passos.getOrNull(indice)) {
                        Passo.Chegada -> ConteudoChegada(estado, acoes, localizacaoLiberada, agora)
                        is Passo.Item -> ordem.checklist.firstOrNull { it.id == p.itemId }?.let {
                            ConteudoItem(estado, it, acoes) { foto -> fotoAberta = foto }
                        }
                        Passo.Conclusao -> ConteudoConclusao(estado, acoes)
                        null -> Unit
                    }
                }
            }
        }
    }

    if (ordem != null) {
        when (camada) {
            Camada.INDICE -> FolhaIndice(ordem, estado.passo, { aoMudarCamada(Camada.NADA); acoes.aoIrPara(it) }, { aoMudarCamada(Camada.NADA) })
            Camada.FILA -> FolhaFila(estado.envios, ordem, estado.online, acoes.aoDescartar, acoes.aoEnviarAgora) { aoMudarCamada(Camada.NADA) }
            Camada.NOTA -> FolhaNota(
                aoSalvar = { texto -> acoes.aoAnotar(texto) { aoMudarCamada(Camada.NADA) } },
                aoFechar = { aoMudarCamada(Camada.NADA) },
            )
            Camada.PAUSA -> FolhaMotivo(
                titulo = "Pausar o atendimento",
                explicacao = "A OS fica \"Aguardando peça\" e o tempo em campo para de contar. Retome quando voltar.",
                motivos = RegrasOs.MOTIVOS_PAUSA,
                botao = "Pausar",
                aoConfirmar = { motivo -> aoMudarCamada(Camada.NADA); acoes.aoAgir(AcaoOs.PAUSAR, motivo) },
                aoFechar = { aoMudarCamada(Camada.NADA) },
            )
            Camada.NAO_ATENDIDA -> FolhaMotivo(
                titulo = "Não consegui atender",
                explicacao = "A OS volta para \"A agendar\", sem data, e o escritório marca de novo. O que você já respondeu fica.",
                motivos = RegrasOs.MOTIVOS_NAO_ATENDIDA,
                botao = "Devolver para agendar",
                perigo = true,
                aoConfirmar = { motivo -> aoMudarCamada(Camada.NADA); acoes.aoAgir(AcaoOs.NAO_ATENDIDA, motivo) },
                aoFechar = { aoMudarCamada(Camada.NADA) },
            )
            Camada.ASSINATURA -> PainelAssinatura(
                nomeInicial = estado.conclusao.assinante.ifBlank { ordem.cliente.nome },
                aoCancelar = { aoMudarCamada(Camada.NADA) },
                aoConfirmar = { desenho, nome ->
                    aoMudarCamada(Camada.NADA)
                    acoes.aoAssinar(desenho, nome)
                },
            )
            Camada.NADA -> Unit
        }
        fotoAberta?.let { foto ->
            // Foto já enviada só sai por quem enviou — como no servidor.
            val podeApagar = !ordem.status.encerrada && (foto.pendente || foto.enviadoPorId == estado.usuario?.id)
            VisorDeFoto(
                ordem.id,
                foto,
                podeApagar = podeApagar,
                aoFechar = { fotoAberta = null },
                aoApagar = {
                    fotoAberta = null
                    acoes.aoRemoverFoto(foto)
                },
            )
        }
    }
}

/** "Item 5 de 17 · Padrão de entrada" e a barra do quanto já foi. */
@Composable
private fun Progresso(estado: ExecucaoEstado) {
    val ordem = estado.ordem ?: return
    val passo = estado.passoAtual ?: return
    val total = ordem.checklist.size.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Roteiro.rotulo(passo, ordem),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${ordem.checklistFeitos}/${ordem.checklist.size} cumpridos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { ordem.checklistFeitos.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            drawStopIndicator = {},
        )
    }
}

/**
 * A nuvem do canto: o que ainda está só neste celular. Sem nada pendente, só
 * o ícone — o técnico sabe que está tudo no Selebi.
 */
@Composable
fun SeloEnvio(situacao: SituacaoEnvio, online: Boolean, aoTocar: () -> Unit) {
    val c = SelebiTema.cores
    val (icone, fundo, frente, texto) = when {
        situacao.recusados > 0 -> Selo(Icons.Outlined.ErrorOutline, c.perigoFundo, c.sobrePerigo, "${situacao.recusados}")
        situacao.esperando > 0 && !online -> Selo(Icons.Outlined.CloudOff, c.neutroFundo, c.sobreNeutro, "${situacao.esperando}")
        situacao.esperando > 0 -> Selo(Icons.Outlined.CloudUpload, c.infoFundo, c.sobreInfo, "${situacao.esperando}")
        else -> Selo(Icons.Outlined.CloudDone, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant, null)
    }
    Surface(onClick = aoTocar, shape = MaterialTheme.shapes.extraLarge, color = fundo, contentColor = frente) {
        Row(
            Modifier.padding(horizontal = if (texto == null) 8.dp else 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                icone,
                contentDescription = when {
                    situacao.recusados > 0 -> "Envios recusados"
                    situacao.esperando > 0 -> "Envios esperando"
                    else -> "Tudo enviado"
                },
                modifier = Modifier.size(20.dp),
            )
            if (texto != null) Text(texto, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private data class Selo(
    val icone: ImageVector,
    val fundo: Color,
    val frente: Color,
    val texto: String?,
)

@Composable
private fun MenuDaExecucao(estado: ExecucaoEstado, acoes: AcoesExecucao, aoMudarCamada: (Camada) -> Unit) {
    val ordem = estado.ordem ?: return
    var aberto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { aberto = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mais opções") }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(
                text = { Text("Nota para o escritório") },
                leadingIcon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                onClick = { aberto = false; aoMudarCamada(Camada.NOTA) },
            )
            if (RegrasOs.permitida(AcaoOs.PAUSAR, ordem.status)) {
                DropdownMenuItem(
                    text = { Text("Pausar — aguardando peça") },
                    leadingIcon = { Icon(Icons.Outlined.PauseCircle, contentDescription = null) },
                    onClick = { aberto = false; acoes.aoPausar() },
                )
            }
            if (RegrasOs.permitida(AcaoOs.NAO_ATENDIDA, ordem.status)) {
                DropdownMenuItem(
                    text = { Text("Não consegui atender") },
                    leadingIcon = { Icon(Icons.Outlined.EventBusy, contentDescription = null) },
                    onClick = { aberto = false; acoes.aoNaoAtendida() },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Rota até o cliente") },
                leadingIcon = { Icon(Icons.Outlined.Directions, contentDescription = null) },
                onClick = { aberto = false; acoes.aoRota() },
            )
            if (ordem.cliente.telefone != null) {
                DropdownMenuItem(
                    text = { Text("Ligar para o cliente") },
                    leadingIcon = { Icon(Icons.Outlined.Call, contentDescription = null) },
                    onClick = { aberto = false; acoes.aoLigar() },
                )
                DropdownMenuItem(
                    text = { Text("Avisar que estou a caminho") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
                    onClick = { aberto = false; acoes.aoAvisarACaminho() },
                )
            }
        }
    }
}
