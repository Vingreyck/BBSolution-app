package br.com.selebi.app.funcionalidade.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.selebi.app.BuildConfig
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.FilaEnvio
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.design.componentes.Avatar
import br.com.selebi.app.nucleo.design.componentes.BlocoDetalhe
import br.com.selebi.app.nucleo.design.componentes.ItemDetalhe
import br.com.selebi.app.nucleo.design.componentes.SeloPapel
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Usuario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PerfilViewModel @Inject constructor(private val sessao: SessaoRepository, fila: FilaEnvio) : ViewModel() {
    val usuario: StateFlow<Usuario?> = sessao.estado
        .map { (it as? EstadoSessao.Logado)?.usuario }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessao.usuario)

    /** O que ainda está só neste celular — o aviso antes de sair. */
    val enviosPendentes: StateFlow<Int> = fila.contar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun sair() {
        viewModelScope.launch { sessao.sair() }
    }
}

@Composable
fun PerfilTela(aoTrocarSenha: () -> Unit, vm: PerfilViewModel = hiltViewModel()) {
    val usuario by vm.usuario.collectAsStateWithLifecycle()
    val pendentes by vm.enviosPendentes.collectAsStateWithLifecycle()
    usuario?.let { PerfilConteudo(it, aoTrocarSenha, vm::sair, pendentes) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilConteudo(usuario: Usuario, aoTrocarSenha: () -> Unit, aoSair: () -> Unit, enviosPendentes: Int = 0) {
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { espaco ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(espaco)
                .consumeWindowInsets(espaco)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(usuario.nome, usuario.papel, tamanho = 88.dp)
                Text(usuario.nome, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                SeloPapel(usuario.papel)
            }
            BlocoDetalhe("Seus dados") {
                ItemDetalhe(Icons.Outlined.Badge, "CPF", usuario.cpf)
                ItemDetalhe(Icons.Outlined.Email, "E-mail", usuario.email ?: "Não informado")
                ItemDetalhe(Icons.Outlined.Business, "Empresa", usuario.empresa.nome)
            }
            BlocoDetalhe("Conta") {
                LinhaAcao(Icons.Outlined.Password, "Trocar senha", aoTrocarSenha)
                LinhaAcao(
                    Icons.AutoMirrored.Outlined.Logout,
                    "Sair do app",
                    { confirmarSaida = true },
                    cor = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "BBSolution para Android · versão ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Sair do app?") },
            text = {
                val aviso = when (enviosPendentes) {
                    0 -> ""
                    1 -> "\n\n1 envio ainda não subiu."
                    else -> "\n\n$enviosPendentes envios ainda não subiram."
                }
                Text(
                    "As ordens de serviço guardadas neste celular são apagadas. Para voltar, é só entrar com o seu CPF e a senha." +
                        aviso +
                        if (enviosPendentes > 0) {
                            " Eles ficam guardados aqui e sobem quando você entrar de novo — mas, se outra pessoa entrar neste celular, são apagados."
                        } else {
                            ""
                        },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarSaida = false
                        aoSair()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { confirmarSaida = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LinhaAcao(icone: ImageVector, texto: String, aoClicar: () -> Unit, cor: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(onClick = aoClicar, color = Color.Transparent, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icone, contentDescription = null, tint = if (cor == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else cor)
            Text(texto, style = MaterialTheme.typography.bodyLarge, color = cor, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PerfilPreview() {
    SelebiTema { PerfilConteudo(Amostras.usuario, {}, {}) }
}
