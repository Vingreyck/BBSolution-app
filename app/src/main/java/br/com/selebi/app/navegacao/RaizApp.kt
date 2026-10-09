package br.com.selebi.app.navegacao

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.com.selebi.app.funcionalidade.acesso.CadastroEnviadoTela
import br.com.selebi.app.funcionalidade.acesso.CadastroTela
import br.com.selebi.app.funcionalidade.acesso.EmpresaTela
import br.com.selebi.app.funcionalidade.acesso.LoginTela
import br.com.selebi.app.funcionalidade.acesso.TrocarSenhaTela
import br.com.selebi.app.funcionalidade.campo.EmCampoTela
import br.com.selebi.app.funcionalidade.agenda.AgendaTela
import br.com.selebi.app.funcionalidade.equipe.EquipeTela
import br.com.selebi.app.funcionalidade.execucao.ExecucaoTela
import br.com.selebi.app.funcionalidade.ordens.OrdemDetalheTela
import br.com.selebi.app.funcionalidade.ordens.OrdensTela
import br.com.selebi.app.funcionalidade.perfil.PerfilTela
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.modelo.Permissao
import br.com.selebi.app.nucleo.modelo.Usuario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class RaizViewModel @Inject constructor(sessao: SessaoRepository) : ViewModel() {
    val estado: StateFlow<EstadoSessao> = sessao.estado
}

/**
 * Qual app mostrar, decidido pelo estado da sessão — e não por quem navegou
 * para onde.
 *
 * Entrar, sair, a sessão vencer, o administrador redefinir a senha: tudo isso
 * muda o estado, e a tela certa aparece sozinha. Os dois grafos (acesso e
 * principal) são separados de propósito: depois de sair, o botão "voltar" não
 * tem como levar de volta para uma OS.
 */
@Composable
fun RaizApp(estado: EstadoSessao) {
    when (estado) {
        EstadoSessao.Carregando -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        is EstadoSessao.Deslogado ->
            if (estado.empresa == null) EmpresaTela() else NavegacaoAcesso()
        is EstadoSessao.Logado ->
            if (estado.usuario.deveTrocarSenha) {
                TrocarSenhaTela(obrigatoria = true, aoConcluir = {})
            } else {
                NavegacaoPrincipal(estado.usuario)
            }
    }
}

@Composable
private fun NavegacaoAcesso() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = RotaLogin) {
        composable<RotaLogin> {
            LoginTela(aoCriarConta = { nav.navigate(RotaCadastro) })
        }
        composable<RotaCadastro> {
            CadastroTela(
                aoVoltar = { nav.popBackStack() },
                aoEnviado = { mensagem ->
                    nav.navigate(RotaCadastroEnviado(mensagem)) { popUpTo<RotaLogin>() }
                },
            )
        }
        composable<RotaCadastroEnviado> { entrada ->
            CadastroEnviadoTela(
                mensagem = entrada.toRoute<RotaCadastroEnviado>().mensagem,
                aoVoltar = { nav.popBackStack(RotaLogin, inclusive = false) },
            )
        }
    }
}

/** As abas de baixo. Quem não tem a permissão não vê a aba — nem a tela. */
internal enum class Destino(
    val rota: Any,
    val rotulo: String,
    val icone: ImageVector,
    val iconeMarcado: ImageVector,
    val permissao: Permissao? = null,
) {
    ORDENS(RotaOrdens, "Ordens", Icons.AutoMirrored.Outlined.Assignment, Icons.AutoMirrored.Filled.Assignment),
    AGENDA(RotaAgenda, "Agenda", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    CAMPO(RotaCampo, "Em campo", Icons.Outlined.Map, Icons.Filled.Map, Permissao.VER_TODAS_OS),
    EQUIPE(RotaEquipe, "Equipe", Icons.Outlined.Groups, Icons.Filled.Groups, Permissao.GERIR_EQUIPE),
    PERFIL(RotaPerfil, "Perfil", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}

@Composable
private fun NavegacaoPrincipal(usuario: Usuario) {
    val nav = rememberNavController()
    val destinos = remember(usuario.permissoes) {
        Destino.entries.filter { it.permissao == null || usuario.pode(it.permissao) }
    }
    val entrada by nav.currentBackStackEntryAsState()
    val atual = entrada?.destination
    val naRaiz = destinos.any { d -> atual?.hasRoute(d.rota::class) == true }

    fun irPara(destino: Destino) {
        nav.navigate(destino.rota) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (naRaiz) {
                BarraDeBaixo(
                    destinos,
                    marcado = { destino -> atual?.hierarchy?.any { it.hasRoute(destino.rota::class) } == true },
                    aoIr = ::irPara,
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { espaco ->
        NavHost(
            nav,
            startDestination = RotaOrdens,
            modifier = Modifier.padding(espaco).consumeWindowInsets(espaco),
        ) {
            composable<RotaOrdens> {
                OrdensTela(
                    aoAbrir = { id -> nav.navigate(RotaOrdem(id)) },
                    aoAbrirPerfil = { irPara(Destino.PERFIL) },
                )
            }
            composable<RotaAgenda> { AgendaTela(aoAbrir = { id -> nav.navigate(RotaOrdem(id)) }) }
            if (usuario.pode(Permissao.VER_TODAS_OS)) {
                composable<RotaCampo> { EmCampoTela(aoAbrirOs = { id -> nav.navigate(RotaOrdem(id)) }) }
            }
            if (usuario.pode(Permissao.GERIR_EQUIPE)) {
                composable<RotaEquipe> { EquipeTela() }
            }
            composable<RotaPerfil> { PerfilTela(aoTrocarSenha = { nav.navigate(RotaTrocarSenha) }) }
            composable<RotaOrdem> {
                OrdemDetalheTela(
                    aoVoltar = { nav.popBackStack() },
                    aoExecutar = { id, itemId -> nav.navigate(RotaExecucao(id, itemId)) { launchSingleTop = true } },
                )
            }
            composable<RotaExecucao> {
                ExecucaoTela(
                    aoFechar = { nav.popBackStack() },
                    aoVoltarParaLista = { nav.popBackStack(RotaOrdens, inclusive = false) },
                )
            }
            composable<RotaTrocarSenha> {
                TrocarSenhaTela(obrigatoria = false, aoConcluir = { nav.popBackStack() })
            }
        }
    }
}

@Composable
internal fun BarraDeBaixo(destinos: List<Destino>, marcado: (Destino) -> Boolean, aoIr: (Destino) -> Unit) {
    NavigationBar {
        destinos.forEach { destino ->
            val ativo = marcado(destino)
            NavigationBarItem(
                selected = ativo,
                onClick = { aoIr(destino) },
                icon = { Icon(if (ativo) destino.iconeMarcado else destino.icone, contentDescription = null) },
                label = { Text(destino.rotulo) },
            )
        }
    }
}
