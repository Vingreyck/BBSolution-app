package br.com.selebi.app.funcionalidade.equipe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.nucleo.dados.EquipeRepository
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.design.componentes.Avatar
import br.com.selebi.app.nucleo.design.componentes.Aviso
import br.com.selebi.app.nucleo.design.componentes.BotaoPrincipal
import br.com.selebi.app.nucleo.design.componentes.BotaoSecundario
import br.com.selebi.app.nucleo.design.componentes.EstadoVazio
import br.com.selebi.app.nucleo.design.componentes.Pilula
import br.com.selebi.app.nucleo.design.componentes.SeloPapel
import br.com.selebi.app.nucleo.design.componentes.TipoAviso
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Equipe
import br.com.selebi.app.nucleo.modelo.Membro
import br.com.selebi.app.nucleo.modelo.Papel
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.util.Datas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SenhaMostrada(val nome: String, val senha: String)

data class EquipeEstado(
    val carregando: Boolean = true,
    val equipe: Equipe? = null,
    val erro: String? = null,
    val processando: Boolean = false,
    val senhaMostrada: SenhaMostrada? = null,
    val mensagem: String? = null,
    val meuId: String? = null,
)

private val Membro.primeiroNome get() = nome.substringBefore(' ')

/**
 * A equipe da empresa, para o administrador: quem pediu para entrar e quem já
 * está dentro. É aqui que o CPF de cada pessoa ganha o papel — técnico,
 * engenheiro ou administrador.
 */
@HiltViewModel
class EquipeViewModel @Inject constructor(
    private val repo: EquipeRepository,
    sessao: SessaoRepository,
) : ViewModel() {
    private val _estado = MutableStateFlow(EquipeEstado(meuId = sessao.usuario?.id))
    val estado: StateFlow<EquipeEstado> = _estado.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        _estado.update { it.copy(carregando = true) }
        viewModelScope.launch {
            when (val r = repo.carregar()) {
                is Resultado.Ok -> _estado.update { it.copy(carregando = false, equipe = r.valor, erro = null) }
                is Resultado.Falha -> _estado.update { it.copy(carregando = false, erro = r.erro.mensagem) }
            }
        }
    }

    /** Toda ação: trava os botões, executa, avisa, e recarrega a lista do servidor. */
    private fun executar(sucesso: String, acao: suspend () -> Resultado<*>) {
        _estado.update { it.copy(processando = true) }
        viewModelScope.launch {
            val r = acao()
            _estado.update {
                it.copy(processando = false, mensagem = if (r is Resultado.Falha) r.erro.mensagem else sucesso)
            }
            if (r is Resultado.Ok) carregar()
        }
    }

    fun aprovar(m: Membro, papel: Papel) =
        executar("${m.primeiroNome} entrou como ${papel.rotulo.lowercase()}.") { repo.aprovar(m.id, papel) }

    fun recusar(m: Membro) = executar("Pedido de ${m.primeiroNome} recusado.") { repo.recusar(m.id) }

    fun trocarPapel(m: Membro, papel: Papel) =
        executar("${m.primeiroNome} agora é ${papel.rotulo.lowercase()}.") { repo.trocarPapel(m.id, papel) }

    fun definirAtivo(m: Membro, ativo: Boolean) = executar(
        if (ativo) "${m.primeiroNome} pode entrar de novo." else "${m.primeiroNome} foi desativado. O acesso caiu na hora.",
    ) { repo.definirAtivo(m.id, ativo) }

    fun redefinirSenha(m: Membro) {
        _estado.update { it.copy(processando = true) }
        viewModelScope.launch {
            when (val r = repo.redefinirSenha(m.id)) {
                is Resultado.Ok -> _estado.update { it.copy(processando = false, senhaMostrada = SenhaMostrada(m.nome, r.valor)) }
                is Resultado.Falha -> _estado.update { it.copy(processando = false, mensagem = r.erro.mensagem) }
            }
        }
    }

    fun fecharSenha() = _estado.update { it.copy(senhaMostrada = null) }

    fun mensagemMostrada() = _estado.update { it.copy(mensagem = null) }
}

@Composable
fun EquipeTela(vm: EquipeViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    EquipeConteudo(
        estado = estado,
        aoAtualizar = vm::carregar,
        aoAprovar = vm::aprovar,
        aoRecusar = vm::recusar,
        aoTrocarPapel = vm::trocarPapel,
        aoDefinirAtivo = vm::definirAtivo,
        aoRedefinirSenha = vm::redefinirSenha,
        aoFecharSenha = vm::fecharSenha,
        aoMensagemMostrada = vm::mensagemMostrada,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipeConteudo(
    estado: EquipeEstado,
    aoAtualizar: () -> Unit,
    aoAprovar: (Membro, Papel) -> Unit,
    aoRecusar: (Membro) -> Unit,
    aoTrocarPapel: (Membro, Papel) -> Unit,
    aoDefinirAtivo: (Membro, Boolean) -> Unit,
    aoRedefinirSenha: (Membro) -> Unit,
    aoFecharSenha: () -> Unit,
    aoMensagemMostrada: () -> Unit,
) {
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(estado.mensagem) {
        estado.mensagem?.let {
            avisos.showSnackbar(it)
            aoMensagemMostrada()
        }
    }

    val pendentes = estado.equipe?.pendentes.orEmpty()
    val membros = estado.equipe?.membros.orEmpty()
    // Abre na aba de quem espera, se houver alguém esperando.
    var abaEscolhida by rememberSaveable { mutableIntStateOf(-1) }
    val aba = if (abaEscolhida >= 0) abaEscolhida else if (pendentes.isNotEmpty()) 0 else 1

    var aprovando by remember { mutableStateOf<Membro?>(null) }
    var gerindo by remember { mutableStateOf<Membro?>(null) }
    var recusando by remember { mutableStateOf<Membro?>(null) }
    var desativando by remember { mutableStateOf<Membro?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equipe") },
                actions = { IconButton(onClick = aoAtualizar) { Icon(Icons.Outlined.Refresh, contentDescription = "Atualizar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { espaco ->
        Column(Modifier.fillMaxSize().padding(espaco).consumeWindowInsets(espaco)) {
            PrimaryTabRow(selectedTabIndex = aba, containerColor = MaterialTheme.colorScheme.surface) {
                // Sem isto o Material pinta as duas abas com a cor de destaque,
                // e a marcada só se distingue pelo tracinho.
                val marcada = MaterialTheme.colorScheme.primary
                val desmarcada = MaterialTheme.colorScheme.onSurfaceVariant
                Tab(
                    selected = aba == 0,
                    onClick = { abaEscolhida = 0 },
                    text = { Text(if (pendentes.isEmpty()) "Aguardando" else "Aguardando (${pendentes.size})") },
                    selectedContentColor = marcada,
                    unselectedContentColor = desmarcada,
                )
                Tab(
                    selected = aba == 1,
                    onClick = { abaEscolhida = 1 },
                    text = { Text("Equipe (${membros.size})") },
                    selectedContentColor = marcada,
                    unselectedContentColor = desmarcada,
                )
            }
            PullToRefreshBox(
                isRefreshing = estado.carregando && estado.equipe != null,
                onRefresh = aoAtualizar,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (estado.equipe == null) {
                        item(key = "estado") {
                            if (estado.carregando) {
                                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                estado.erro?.let { Aviso(it, TipoAviso.ERRO) }
                            }
                        }
                    } else if (aba == 0) {
                        if (pendentes.isEmpty()) {
                            item(key = "vazio") {
                                EstadoVazio(
                                    Icons.Outlined.HowToReg,
                                    "Ninguém esperando",
                                    "Quando alguém da equipe se cadastrar pelo app com o código da empresa, o pedido aparece aqui para você aprovar.",
                                )
                            }
                        }
                        items(pendentes, key = { it.id }) { m ->
                            CartaoPendente(m, !estado.processando, aoAprovar = { aprovando = m }, aoRecusar = { recusando = m })
                        }
                    } else {
                        items(membros, key = { it.id }) { m ->
                            val eu = m.id == estado.meuId
                            LinhaMembro(m, eu, aoClicar = { if (!eu) gerindo = m })
                        }
                    }
                }
            }
        }
    }

    aprovando?.let { m ->
        FolhaAprovar(m, aoConfirmar = { papel -> aoAprovar(m, papel); aprovando = null }, aoFechar = { aprovando = null })
    }
    gerindo?.let { m ->
        FolhaGerir(
            membro = m,
            aoTrocarPapel = { papel -> aoTrocarPapel(m, papel); gerindo = null },
            aoRedefinirSenha = { aoRedefinirSenha(m); gerindo = null },
            aoDesativar = { desativando = m; gerindo = null },
            aoReativar = { aoDefinirAtivo(m, true); gerindo = null },
            aoFechar = { gerindo = null },
        )
    }
    recusando?.let { m ->
        Confirmacao(
            titulo = "Recusar o pedido de ${m.primeiroNome}?",
            texto = "O cadastro é apagado. Se foi engano, a pessoa pode se cadastrar de novo.",
            acao = "Recusar",
            aoConfirmar = { aoRecusar(m); recusando = null },
            aoCancelar = { recusando = null },
        )
    }
    desativando?.let { m ->
        Confirmacao(
            titulo = "Desativar ${m.primeiroNome}?",
            texto = "A pessoa sai do app e do navegador na hora. O histórico continua: documentos e seriais seguem com o nome dela.",
            acao = "Desativar",
            aoConfirmar = { aoDefinirAtivo(m, false); desativando = null },
            aoCancelar = { desativando = null },
        )
    }
    estado.senhaMostrada?.let { DialogoSenha(it, aoFecharSenha) }
}

@Composable
private fun CartaoPendente(m: Membro, habilitado: Boolean, aoAprovar: () -> Unit, aoRecusar: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.nome, Papel.OUTRO)
                Column(Modifier.weight(1f)) {
                    Text(m.nome, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(m.cpfOculto?.let { "CPF $it" }, m.email).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "Pediu acesso ${Datas.haQuanto(m.criadoEm)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = aoRecusar, enabled = habilitado, modifier = Modifier.weight(1f)) { Text("Recusar") }
                Button(onClick = aoAprovar, enabled = habilitado, modifier = Modifier.weight(1f)) { Text("Aprovar") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinhaMembro(m: Membro, eu: Boolean, aoClicar: () -> Unit) {
    val c = SelebiTema.cores
    Surface(
        onClick = aoClicar,
        enabled = !eu,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.nome, m.papel)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (eu) "${m.nome} (você)" else m.nome,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeloPapel(m.papel)
                    if (!m.ativo) Pilula("Desativado", c.perigoFundo, c.sobrePerigo)
                    if (m.ativo && m.senhaProvisoria) Pilula("Senha provisória", c.alertaFundo, c.sobreAlerta)
                    if (m.ativo && !m.usaApp) Pilula("Só navegador", c.neutroFundo, c.sobreNeutro)
                }
            }
            if (!eu) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** As três funções, cada uma com o que significa — escolher papel sem saber o que ele dá é chute. */
@Composable
private fun OpcoesPapel(selecionado: Papel, aoEscolher: (Papel) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Papel.doApp.forEach { papel ->
            val marcado = papel == selecionado
            Surface(
                selected = marcado,
                onClick = { aoEscolher(papel) },
                shape = MaterialTheme.shapes.medium,
                color = if (marcado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (marcado) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = marcado, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(papel.rotulo, style = MaterialTheme.typography.titleSmall)
                        Text(papel.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaAprovar(m: Membro, aoConfirmar: (Papel) -> Unit, aoFechar: () -> Unit) {
    var papel by rememberSaveable { mutableStateOf(Papel.TECNICO) }
    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Aprovar ${m.nome}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Escolha a função. Dá para mudar depois, na aba Equipe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OpcoesPapel(papel) { papel = it }
            BotaoPrincipal("Aprovar como ${papel.rotulo.lowercase()}", { aoConfirmar(papel) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaGerir(
    membro: Membro,
    aoTrocarPapel: (Papel) -> Unit,
    aoRedefinirSenha: () -> Unit,
    aoDesativar: () -> Unit,
    aoReativar: () -> Unit,
    aoFechar: () -> Unit,
) {
    var papel by rememberSaveable { mutableStateOf(membro.papel.takeIf { it in Papel.doApp } ?: Papel.TECNICO) }
    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(membro.nome, membro.papel, tamanho = 56.dp)
                Column {
                    Text(membro.nome, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(membro.cpf?.let { "CPF $it" }, membro.email).joinToString(" · ").ifBlank { "Sem CPF — usa só o navegador" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (membro.ativo) {
                Text("Função", style = MaterialTheme.typography.titleSmall)
                OpcoesPapel(papel) { papel = it }
                if (papel != membro.papel) BotaoPrincipal("Salvar: ${papel.rotulo.lowercase()}", { aoTrocarPapel(papel) })
                HorizontalDivider()
                BotaoSecundario("Redefinir senha", aoRedefinirSenha, icone = Icons.Outlined.Password)
                TextButton(
                    onClick = aoDesativar,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.PersonOff, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Desativar acesso")
                }
            } else {
                Aviso("Esta conta está desativada: não entra no app nem no navegador.", TipoAviso.ALERTA)
                BotaoPrincipal("Reativar acesso", aoReativar)
            }
        }
    }
}

@Composable
private fun Confirmacao(titulo: String, texto: String, acao: String, aoConfirmar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = aoConfirmar, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(acao)
            }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}

/**
 * A senha provisória, uma vez só.
 *
 * Não fecha tocando fora: o administrador precisa ter certeza de que anotou
 * ou copiou antes de sumir — não há como ver de novo, só gerar outra.
 */
@Composable
private fun DialogoSenha(senha: SenhaMostrada, aoFechar: () -> Unit) {
    @Suppress("DEPRECATION")
    val area = LocalClipboardManager.current
    var copiada by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Senha provisória") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Passe esta senha para ${senha.nome}. Ela aparece só agora; no primeiro acesso, a pessoa cria a própria.")
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            senha.senha,
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            area.setText(AnnotatedString(senha.senha))
                            copiada = true
                        }) {
                            Icon(
                                if (copiada) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = if (copiada) "Copiada" else "Copiar",
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Pronto") } },
    )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun EquipePreview() {
    SelebiTema {
        EquipeConteudo(
            EquipeEstado(carregando = false, equipe = Amostras.equipe(), meuId = Amostras.usuario.id),
            {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {}, {},
        )
    }
}
