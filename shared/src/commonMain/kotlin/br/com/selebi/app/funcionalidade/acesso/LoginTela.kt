package br.com.selebi.app.funcionalidade.acesso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.vmDoNucleo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.PreferenciasApp
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.design.componentes.Aviso
import br.com.selebi.app.nucleo.design.componentes.BotaoPrincipal
import br.com.selebi.app.nucleo.design.componentes.BotaoSecundario
import br.com.selebi.app.nucleo.design.componentes.CampoCpf
import br.com.selebi.app.nucleo.design.componentes.CampoSenha
import br.com.selebi.app.nucleo.design.componentes.SeloSelebi
import br.com.selebi.app.nucleo.design.componentes.TipoAviso
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.util.Cpf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AvisoTela(val tipo: TipoAviso, val texto: String, val titulo: String? = null)

data class LoginEstado(
    val empresa: String = "",
    val cpf: String = "",
    val senha: String = "",
    val carregando: Boolean = false,
    val erroSenha: String? = null,
    val aviso: AvisoTela? = null,
) {
    val podeEntrar: Boolean get() = Cpf.valido(cpf) && senha.isNotEmpty() && !carregando
}

class LoginViewModel(
    private val sessao: SessaoRepository,
    preferencias: PreferenciasApp,
) : ViewModel() {
    private val _estado = MutableStateFlow(LoginEstado())
    val estado: StateFlow<LoginEstado> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            val empresa = (sessao.estado.value as? EstadoSessao.Deslogado)?.empresa?.nome.orEmpty()
            val ultimoCpf = preferencias.ultimoCpf.first().orEmpty()
            _estado.update { it.copy(empresa = empresa, cpf = it.cpf.ifEmpty { ultimoCpf }) }
        }
    }

    fun aoMudarCpf(valor: String) = _estado.update { it.copy(cpf = valor, aviso = null, erroSenha = null) }

    fun aoMudarSenha(valor: String) = _estado.update { it.copy(senha = valor, aviso = null, erroSenha = null) }

    fun trocarEmpresa() {
        viewModelScope.launch { sessao.trocarEmpresa() }
    }

    fun entrar() {
        val atual = _estado.value
        if (!atual.podeEntrar) return
        _estado.update { it.copy(carregando = true, aviso = null, erroSenha = null) }
        viewModelScope.launch {
            val resultado = sessao.entrar(atual.cpf, atual.senha)
            if (resultado is Resultado.Falha) {
                _estado.update { tratarErro(it.copy(carregando = false), resultado.erro) }
            } else {
                _estado.update { it.copy(carregando = false, senha = "") }
            }
        }
    }

    /** Cada recusa do servidor tem seu lugar na tela. */
    private fun tratarErro(estado: LoginEstado, erro: ErroApp): LoginEstado {
        if (erro !is ErroApp.Servidor) return estado.copy(aviso = AvisoTela(TipoAviso.ALERTA, erro.mensagem))
        return when (erro.codigo) {
            "credenciais" -> estado.copy(erroSenha = "CPF ou senha não conferem.")
            "aguardando_aprovacao" -> estado.copy(
                aviso = AvisoTela(TipoAviso.ESPERA, erro.mensagem, titulo = "Cadastro em análise"),
            )
            else -> estado.copy(aviso = AvisoTela(TipoAviso.ERRO, erro.mensagem))
        }
    }
}

@Composable
fun LoginTela(aoCriarConta: () -> Unit, vm: LoginViewModel = vmDoNucleo { LoginViewModel(it.sessao, it.preferencias) }) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LoginConteudo(
        estado = estado,
        aoMudarCpf = vm::aoMudarCpf,
        aoMudarSenha = vm::aoMudarSenha,
        aoEntrar = vm::entrar,
        aoTrocarEmpresa = vm::trocarEmpresa,
        aoCriarConta = aoCriarConta,
    )
}

@Composable
fun LoginConteudo(
    estado: LoginEstado,
    aoMudarCpf: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoEntrar: () -> Unit,
    aoTrocarEmpresa: () -> Unit,
    aoCriarConta: () -> Unit,
) {
    var mostrarEsqueci by rememberSaveable { mutableStateOf(false) }

    LayoutAcesso {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SeloSelebi(44.dp)
            Spacer(Modifier.weight(1f))
            ChipEmpresa(estado.empresa, aoTrocarEmpresa)
        }
        Spacer(Modifier.height(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Entrar", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Use o seu CPF e a senha que você criou no cadastro.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
        CampoCpf(valor = estado.cpf, aoMudar = aoMudarCpf)
        Spacer(Modifier.height(8.dp))
        CampoSenha(
            valor = estado.senha,
            aoMudar = aoMudarSenha,
            erro = estado.erroSenha,
            imeAction = ImeAction.Go,
            aoConfirmar = aoEntrar,
        )
        TextButton(onClick = { mostrarEsqueci = true }, modifier = Modifier.align(Alignment.End)) {
            Text("Esqueci minha senha")
        }
        estado.aviso?.let {
            Aviso(it.texto, it.tipo, titulo = it.titulo)
            Spacer(Modifier.height(16.dp))
        }
        BotaoPrincipal("Entrar", aoEntrar, carregando = estado.carregando, habilitado = estado.podeEntrar)
        Spacer(Modifier.height(28.dp))
        Text(
            "Primeira vez no BBSolution?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(10.dp))
        BotaoSecundario("Criar minha conta", aoCriarConta)
    }

    if (mostrarEsqueci) {
        AlertDialog(
            onDismissRequest = { mostrarEsqueci = false },
            confirmButton = { TextButton(onClick = { mostrarEsqueci = false }) { Text("Entendi") } },
            title = { Text("Esqueceu a senha?") },
            text = {
                Text(
                    "Peça a um administrador da ${estado.empresa.ifBlank { "sua empresa" }} para redefinir. " +
                        "Ele recebe uma senha provisória para te passar, e você cria uma nova no primeiro acesso.",
                )
            },
        )
    }
}

/** "🏢 BB Soluções ⇄" — mostra de qual empresa é este login e deixa trocar. */
@Composable
private fun ChipEmpresa(nome: String, aoTrocar: () -> Unit) {
    Surface(
        onClick = aoTrocar,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Business, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                nome.ifBlank { "Empresa" },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            Icon(
                Icons.Outlined.SwapHoriz,
                contentDescription = "Trocar de empresa",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
