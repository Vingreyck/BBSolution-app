package br.com.selebi.app.funcionalidade.acesso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.vmDoNucleo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.design.componentes.Aviso
import br.com.selebi.app.nucleo.design.componentes.BotaoPrincipal
import br.com.selebi.app.nucleo.design.componentes.CampoCpf
import br.com.selebi.app.nucleo.design.componentes.CampoSenha
import br.com.selebi.app.nucleo.design.componentes.CampoTexto
import br.com.selebi.app.nucleo.design.componentes.TipoAviso
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.util.Cpf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CadastroEstado(
    val empresa: String = "",
    val nome: String = "",
    val cpf: String = "",
    val email: String = "",
    val senha: String = "",
    val carregando: Boolean = false,
    /** Erro por campo: "nome", "cpf", "email", "senha" — os mesmos nomes do servidor. */
    val erros: Map<String, String> = emptyMap(),
    val aviso: AvisoTela? = null,
    /** Preenchido quando o cadastro foi aceito: a mensagem da tela seguinte. */
    val enviado: String? = null,
)

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

/**
 * Cadastro pelo próprio técnico: nome completo, CPF, e-mail opcional e senha.
 *
 * O código da empresa não é pedido de novo — já veio da primeira tela. O
 * papel também não: quem escolhe se a pessoa é técnico, engenheiro ou
 * administrador é o administrador, ao aprovar.
 */
class CadastroViewModel(private val sessao: SessaoRepository) : ViewModel() {
    private val _estado = MutableStateFlow(
        CadastroEstado(empresa = (sessao.estado.value as? EstadoSessao.Deslogado)?.empresa?.nome.orEmpty()),
    )
    val estado: StateFlow<CadastroEstado> = _estado.asStateFlow()

    private fun mudar(campo: String, bloco: (CadastroEstado) -> CadastroEstado) =
        _estado.update { bloco(it).copy(erros = it.erros - campo, aviso = null) }

    fun aoMudarNome(v: String) = mudar("nome") { it.copy(nome = v.take(120)) }
    fun aoMudarCpf(v: String) = mudar("cpf") { it.copy(cpf = v) }
    fun aoMudarEmail(v: String) = mudar("email") { it.copy(email = v.take(254).trim()) }
    fun aoMudarSenha(v: String) = mudar("senha") { it.copy(senha = v.take(128)) }

    /** As mesmas regras do servidor, conferidas antes — resposta na hora, sem rede. */
    private fun validar(e: CadastroEstado): Map<String, String> = buildMap {
        val nome = e.nome.trim().replace(Regex("\\s+"), " ")
        if (nome.length < 5 || !nome.contains(' ')) put("nome", "Digite o nome completo.")
        if (!Cpf.valido(e.cpf)) put("cpf", "Esse CPF não é válido. Confira os números.")
        if (e.email.isNotBlank() && !EMAIL.matches(e.email)) put("email", "Esse e-mail não parece certo.")
        if (e.senha.length < 8) put("senha", "A senha precisa de pelo menos 8 caracteres.")
        else if (e.senha.filter(Char::isDigit).contains(e.cpf) && e.cpf.length == 11) {
            put("senha", "A senha não pode conter o seu CPF.")
        }
    }

    fun enviar() {
        val atual = _estado.value
        if (atual.carregando) return
        val erros = validar(atual)
        if (erros.isNotEmpty()) {
            _estado.update { it.copy(erros = erros) }
            return
        }
        _estado.update { it.copy(carregando = true, aviso = null) }
        viewModelScope.launch {
            when (val r = sessao.cadastrar(atual.nome, atual.cpf, atual.email.ifBlank { null }, atual.senha)) {
                is Resultado.Ok -> _estado.update { it.copy(carregando = false, enviado = r.valor, senha = "") }
                is Resultado.Falha -> _estado.update { tratarErro(it.copy(carregando = false), r.erro) }
            }
        }
    }

    private fun tratarErro(estado: CadastroEstado, erro: ErroApp): CadastroEstado {
        if (erro !is ErroApp.Servidor) return estado.copy(aviso = AvisoTela(TipoAviso.ALERTA, erro.mensagem))
        return when (erro.codigo) {
            "validacao" -> estado.copy(erros = erro.campos)
            "email_em_uso" -> estado.copy(erros = erro.campos.ifEmpty { mapOf("email" to erro.mensagem) })
            "cadastro_pendente" -> estado.copy(aviso = AvisoTela(TipoAviso.ESPERA, erro.mensagem, "Cadastro já enviado"))
            "cpf_cadastrado" -> estado.copy(aviso = AvisoTela(TipoAviso.INFO, erro.mensagem, "Você já tem conta"))
            "codigo_invalido" -> estado.copy(
                aviso = AvisoTela(
                    TipoAviso.ERRO,
                    "O código da empresa mudou. Volte, toque no nome da empresa e digite o código novo.",
                ),
            )
            else -> estado.copy(aviso = AvisoTela(TipoAviso.ERRO, erro.mensagem))
        }
    }
}

@Composable
fun CadastroTela(aoVoltar: () -> Unit, aoEnviado: (String) -> Unit, vm: CadastroViewModel = vmDoNucleo { CadastroViewModel(it.sessao) }) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.enviado) { estado.enviado?.let(aoEnviado) }
    CadastroConteudo(
        estado = estado,
        aoVoltar = aoVoltar,
        aoMudarNome = vm::aoMudarNome,
        aoMudarCpf = vm::aoMudarCpf,
        aoMudarEmail = vm::aoMudarEmail,
        aoMudarSenha = vm::aoMudarSenha,
        aoEnviar = vm::enviar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadastroConteudo(
    estado: CadastroEstado,
    aoVoltar: () -> Unit,
    aoMudarNome: (String) -> Unit,
    aoMudarCpf: (String) -> Unit,
    aoMudarEmail: (String) -> Unit,
    aoMudarSenha: (String) -> Unit,
    aoEnviar: () -> Unit,
) {
    LayoutAcesso(
        barraDoTopo = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Criar conta", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (estado.empresa.isNotBlank()) "Na ${estado.empresa}. Um administrador vai liberar o seu acesso."
                else "Um administrador da empresa vai liberar o seu acesso.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        CampoTexto(
            valor = estado.nome,
            aoMudar = aoMudarNome,
            rotulo = "Nome completo",
            icone = Icons.Outlined.Person,
            erro = estado.erros["nome"],
            teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(4.dp))
        CampoCpf(valor = estado.cpf, aoMudar = aoMudarCpf, erro = estado.erros["cpf"])
        Spacer(Modifier.height(4.dp))
        CampoTexto(
            valor = estado.email,
            aoMudar = aoMudarEmail,
            rotulo = "E-mail (opcional)",
            icone = Icons.Outlined.Email,
            erro = estado.erros["email"],
            dica = "Só se você também for usar o BBSolution pelo computador.",
            teclado = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(4.dp))
        CampoSenha(
            valor = estado.senha,
            aoMudar = aoMudarSenha,
            rotulo = "Crie uma senha",
            erro = estado.erros["senha"],
            dica = "Pelo menos 8 caracteres. Uma frase curta é mais forte e mais fácil de lembrar.",
            imeAction = ImeAction.Done,
            aoConfirmar = aoEnviar,
        )
        Spacer(Modifier.height(16.dp))
        estado.aviso?.let {
            Aviso(it.texto, it.tipo, titulo = it.titulo)
            Spacer(Modifier.height(16.dp))
        }
        BotaoPrincipal("Enviar cadastro", aoEnviar, carregando = estado.carregando)
        Spacer(Modifier.height(16.dp))
        Text(
            "O seu CPF vira o seu login. Ele só é usado para você entrar no BBSolution da sua empresa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
