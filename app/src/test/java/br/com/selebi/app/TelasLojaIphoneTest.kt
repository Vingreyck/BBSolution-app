package br.com.selebi.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.amostras.AmostrasExecucao
import br.com.selebi.app.funcionalidade.agenda.AgendaConteudo
import br.com.selebi.app.funcionalidade.agenda.AgendaEstado
import br.com.selebi.app.funcionalidade.agenda.DiaDaSemana
import br.com.selebi.app.funcionalidade.execucao.AcoesExecucao
import br.com.selebi.app.funcionalidade.execucao.Camada
import br.com.selebi.app.funcionalidade.execucao.ExecucaoConteudo
import br.com.selebi.app.funcionalidade.execucao.ExecucaoEstado
import br.com.selebi.app.funcionalidade.ordens.AcoesDetalhe
import br.com.selebi.app.funcionalidade.ordens.OrdemDetalheConteudo
import br.com.selebi.app.funcionalidade.ordens.OrdemDetalheEstado
import br.com.selebi.app.funcionalidade.ordens.OrdensConteudo
import br.com.selebi.app.navegacao.BarraDeBaixo
import br.com.selebi.app.navegacao.Destino
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Usuario
import br.com.selebi.app.nucleo.util.Datas
import br.com.selebi.app.nucleo.util.plusDays
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * As capturas da App Store: as mesmas telas da Play, no tamanho que a Apple
 * pede no "iPhone com Dynamic Island (tela média)", de 6,3" — 402 × 874 pontos
 * a 3x = 1206 × 2622 pixels. (Em nov/2026 o 6,9", 1290 × 2796, deixou de ser aceito.)
 *
 *   gradlew :app:testDebugUnitTest --tests "*TelasLojaIphoneTest*"
 *
 * As imagens saem em app/build/telas-iphone/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w402dp-h950dp-xxhdpi")
class TelasLojaIphoneTest {

    @get:Rule
    val regra = createComposeRule()

    private fun capturar(nome: String, conteudo: @Composable () -> Unit) {
        regra.setContent {
            SelebiTema(escuro = false) {
                Box(Modifier.size(402.dp, 874.dp).testTag("captura")) { conteudo() }
            }
        }
        regra.onNodeWithTag("captura").captureRoboImage("build/telas-iphone/$nome.png")
    }

    @Composable
    private fun NaCasca(aba: Destino, usuario: Usuario = Amostras.usuario, conteudo: @Composable () -> Unit) {
        val destinos = Destino.entries.filter { d -> d.permissao.let { it == null || usuario.pode(it) } }
        Scaffold(
            bottomBar = { BarraDeBaixo(destinos, marcado = { it == aba }, aoIr = {}) },
            contentWindowInsets = WindowInsets(0),
        ) { espaco -> Box(Modifier.padding(espaco)) { conteudo() } }
    }

    @Composable
    private fun Wizard(ordem: Ordem, passo: Int, envios: List<Envio> = emptyList(), online: Boolean = true) =
        ExecucaoConteudo(
            ExecucaoEstado(
                carregado = true,
                ordem = ordem,
                passo = passo,
                envios = envios,
                online = online,
                usuario = Amostras.tecnico,
            ),
            AcoesExecucao(),
            localizacaoLiberada = true,
            camada = Camada.NADA,
            agora = Amostras.agora,
        )

    @Test
    fun ordens() = capturar("1-ordens") {
        NaCasca(Destino.ORDENS) { OrdensConteudo(Amostras.estadoOrdens(), {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test
    fun detalhe() = capturar("2-detalhe") {
        OrdemDetalheConteudo(
            OrdemDetalheEstado(
                ordem = Amostras.ordens().first { it.numero == 41 },
                atualizando = false,
                mostrarResponsavel = true,
                agora = Amostras.agora,
                usuario = Amostras.usuario,
            ),
            AcoesDetalhe(),
        )
    }

    @Test
    fun execucaoItemNumero() = capturar("3-execucao-item-numero") {
        Wizard(AmostrasExecucao.vistoriaEmCampo(), 5, AmostrasExecucao.enviosVistoria(), online = false)
    }

    @Test
    fun execucaoItemFoto() = capturar("4-execucao-item-foto") {
        Wizard(AmostrasExecucao.vistoriaEmCampo(), 3, AmostrasExecucao.enviosVistoria(), online = false)
    }

    @Test
    fun execucaoConclusao() = capturar("5-execucao-conclusao") {
        val ordem = AmostrasExecucao.instalacaoParaConcluir()
        Wizard(ordem, ordem.checklist.size + 1)
    }

    // "Em campo" fica de fora: no teste o mapa não é desenhado, e a vitrine sairia com um quadro vazio.

    @Test
    fun agenda() = capturar("6-agenda") {
        NaCasca(Destino.AGENDA) {
            val ordens = Amostras.ordens()
            val dia = Amostras.agora.date
            val segunda = Datas.segundaDaSemana(dia)
            AgendaConteudo(
                AgendaEstado(
                    selecionado = dia,
                    semana = (0L..6L).map { segunda.plusDays(it) }.map { d ->
                        DiaDaSemana(d, ordens.count { it.agendadaPara?.date == d })
                    },
                    doDia = ordens.filter { it.agendadaPara?.date == dia },
                    semData = 2,
                    mostrarResponsavel = true,
                    agora = Amostras.agora,
                ),
                {}, {}, {}, {},
            )
        }
    }
}
