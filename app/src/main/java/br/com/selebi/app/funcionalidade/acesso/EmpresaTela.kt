package br.com.selebi.app.funcionalidade.acesso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.design.componentes.Aviso
import br.com.selebi.app.nucleo.design.componentes.BotaoPrincipal
import br.com.selebi.app.nucleo.design.componentes.CampoTexto
import br.com.selebi.app.nucleo.design.componentes.MarcaSelebi
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

data class EmpresaEstado(
    val codigo: String = "",
    val carregando: Boolean = false,
    /** O servidor disse que o código não existe — erro no campo. */
    val erroCodigo: String? = null,
    /** Não deu para perguntar ao servidor — aviso, sem culpar o código. */
    val aviso: String? = null,
)

/**
 * Primeira tela, uma vez por celular: o código da empresa.
 *
 * Três respostas possíveis, e o SeeNet ensinou a não confundir duas delas:
 * "código não existe" (erro no campo) é diferente de "sem sinal para
 * conferir" (aviso amarelo). Dizer que o código está errado quando o problema
 * é o sinal faz o técnico redigitar um código certo para sempre.
 */
@HiltViewModel
class EmpresaViewModel @Inject constructor(private val sessao: SessaoRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EmpresaEstado())
    val estado: StateFlow<EmpresaEstado> = _estado.asStateFlow()

    fun aoMudarCodigo(valor: String) {
        _estado.update { it.copy(codigo = valor.take(64), erroCodigo = null, aviso = null) }
    }

    fun continuar() {
        val atual = _estado.value
        if (atual.carregando) return
        if (atual.codigo.trim().length < 4) {
            _estado.update { it.copy(erroCodigo = "Digite o código completo.") }
            return
        }
        _estado.update { it.copy(carregando = true, erroCodigo = null, aviso = null) }
        viewModelScope.launch {
            // Deu certo: o estado da sessão muda e a navegação troca de tela sozinha.
            val resultado = sessao.resolverEmpresa(atual.codigo)
            if (resultado is Resultado.Falha) {
                val erro = resultado.erro
                _estado.update {
                    if (erro is ErroApp.Servidor && erro.codigo == "codigo_invalido") {
                        it.copy(carregando = false, erroCodigo = erro.mensagem)
                    } else {
                        it.copy(carregando = false, aviso = erro.mensagem)
                    }
                }
            } else {
                _estado.update { it.copy(carregando = false) }
            }
        }
    }
}

@Composable
fun EmpresaTela(vm: EmpresaViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    EmpresaConteudo(estado, vm::aoMudarCodigo, vm::continuar)
}

@Composable
fun EmpresaConteudo(
    estado: EmpresaEstado,
    aoMudarCodigo: (String) -> Unit,
    aoContinuar: () -> Unit,
) {
    LayoutAcesso {
        Spacer(Modifier.height(24.dp))
        MarcaSelebi()
        Spacer(Modifier.height(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Qual é a sua empresa?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Digite o código que a sua empresa recebeu para usar o BBSolution. É só na primeira vez.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
        CampoTexto(
            valor = estado.codigo,
            aoMudar = aoMudarCodigo,
            rotulo = "Código da empresa",
            icone = Icons.Outlined.Business,
            erro = estado.erroCodigo,
            teclado = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Go,
            ),
            acoes = KeyboardActions(onGo = { aoContinuar() }),
        )
        if (estado.aviso != null) {
            Spacer(Modifier.height(12.dp))
            Aviso(estado.aviso, TipoAviso.ALERTA)
        }
        Spacer(Modifier.height(20.dp))
        BotaoPrincipal(
            texto = "Continuar",
            aoClicar = aoContinuar,
            carregando = estado.carregando,
            habilitado = estado.codigo.isNotBlank(),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Não tem o código? Peça ao administrador da sua empresa.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmpresaPreview() {
    SelebiTema { EmpresaConteudo(EmpresaEstado(codigo = "solucoes"), {}, {}) }
}
