package br.com.selebi.app.funcionalidade.acesso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.design.componentes.Aviso
import br.com.selebi.app.nucleo.design.componentes.BotaoPrincipal
import br.com.selebi.app.nucleo.design.componentes.CampoSenha
import br.com.selebi.app.nucleo.design.componentes.TipoAviso
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.Resultado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrocarSenhaEstado(
    val atual: String = "",
    val nova: String = "",
    val carregando: Boolean = false,
    val erroAtual: String? = null,
    val erroNova: String? = null,
    val aviso: String? = null,
    val concluido: Boolean = false,
)

@HiltViewModel
class TrocarSenhaViewModel @Inject constructor(private val sessao: SessaoRepository) : ViewModel() {
    private val _estado = MutableStateFlow(TrocarSenhaEstado())
    val estado: StateFlow<TrocarSenhaEstado> = _estado.asStateFlow()

    fun aoMudarAtual(v: String) = _estado.update { it.copy(atual = v, erroAtual = null, aviso = null) }
    fun aoMudarNova(v: String) = _estado.update { it.copy(nova = v, erroNova = null, aviso = null) }

    fun salvar() {
        val e = _estado.value
        if (e.carregando) return
        if (e.nova.length < 8) {
            _estado.update { it.copy(erroNova = "A senha precisa de pelo menos 8 caracteres.") }
            return
        }
        _estado.update { it.copy(carregando = true) }
        viewModelScope.launch {
            when (val r = sessao.trocarSenha(e.atual, e.nova)) {
                is Resultado.Ok -> _estado.update { it.copy(carregando = false, concluido = true) }
                is Resultado.Falha -> _estado.update {
                    val erro = r.erro
                    if (erro is ErroApp.Servidor) {
                        it.copy(
                            carregando = false,
                            erroAtual = erro.campos["atual"],
                            erroNova = erro.campos["nova"],
                            aviso = if (erro.campos.isEmpty()) erro.mensagem else null,
                        )
                    } else {
                        it.copy(carregando = false, aviso = erro.mensagem)
                    }
                }
            }
        }
    }

    fun sair() {
        viewModelScope.launch { sessao.sair() }
    }
}

/**
 * Trocar a senha — obrigatório quando o administrador redefiniu (senha
 * provisória), voluntário quando vem do perfil.
 *
 * No modo obrigatório não há "voltar": a única saída além de trocar é sair da
 * conta. Com a senha provisória, o servidor não entrega nada além disto.
 */
@Composable
fun TrocarSenhaTela(obrigatoria: Boolean, aoConcluir: () -> Unit, vm: TrocarSenhaViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.concluido) { if (estado.concluido) aoConcluir() }
    TrocarSenhaConteudo(estado, obrigatoria, vm::aoMudarAtual, vm::aoMudarNova, vm::salvar, aoConcluir, vm::sair)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrocarSenhaConteudo(
    estado: TrocarSenhaEstado,
    obrigatoria: Boolean,
    aoMudarAtual: (String) -> Unit,
    aoMudarNova: (String) -> Unit,
    aoSalvar: () -> Unit,
    aoVoltar: () -> Unit,
    aoSair: () -> Unit,
) {
    LayoutAcesso(
        barraDoTopo = {
            if (!obrigatoria) {
                TopAppBar(
                    title = { Text("Trocar senha") },
                    navigationIcon = {
                        IconButton(onClick = aoVoltar) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) {
        if (obrigatoria) {
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Crie a sua senha", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Você entrou com uma senha provisória. Crie uma sua para continuar — a partir daí, só você sabe.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        CampoSenha(
            valor = estado.atual,
            aoMudar = aoMudarAtual,
            rotulo = if (obrigatoria) "Senha provisória" else "Senha atual",
            erro = estado.erroAtual,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(4.dp))
        CampoSenha(
            valor = estado.nova,
            aoMudar = aoMudarNova,
            rotulo = "Senha nova",
            erro = estado.erroNova,
            dica = "Pelo menos 8 caracteres.",
            aoConfirmar = aoSalvar,
        )
        Spacer(Modifier.height(16.dp))
        estado.aviso?.let {
            Aviso(it, TipoAviso.ERRO)
            Spacer(Modifier.height(16.dp))
        }
        BotaoPrincipal("Salvar senha", aoSalvar, carregando = estado.carregando)
        if (obrigatoria) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = aoSair, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Sair da conta")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrocarSenhaPreview() {
    SelebiTema { TrocarSenhaConteudo(TrocarSenhaEstado(), obrigatoria = true, {}, {}, {}, {}, {}) }
}
