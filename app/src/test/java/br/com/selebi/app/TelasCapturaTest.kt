package br.com.selebi.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.funcionalidade.acesso.CadastroConteudo
import br.com.selebi.app.funcionalidade.acesso.CadastroEnviadoTela
import br.com.selebi.app.funcionalidade.acesso.CadastroEstado
import br.com.selebi.app.funcionalidade.acesso.EmpresaConteudo
import br.com.selebi.app.funcionalidade.acesso.EmpresaEstado
import br.com.selebi.app.funcionalidade.acesso.LoginConteudo
import br.com.selebi.app.funcionalidade.acesso.LoginEstado
import br.com.selebi.app.funcionalidade.agenda.AgendaConteudo
import br.com.selebi.app.funcionalidade.agenda.AgendaEstado
import br.com.selebi.app.funcionalidade.agenda.DiaDaSemana
import br.com.selebi.app.funcionalidade.equipe.EquipeConteudo
import br.com.selebi.app.funcionalidade.equipe.EquipeEstado
import br.com.selebi.app.amostras.AmostrasCampo
import br.com.selebi.app.amostras.AmostrasExecucao
import br.com.selebi.app.funcionalidade.campo.EmCampoConteudo
import br.com.selebi.app.funcionalidade.campo.EmCampoEstado
import br.com.selebi.app.funcionalidade.execucao.AcoesExecucao
import br.com.selebi.app.funcionalidade.execucao.Camada
import br.com.selebi.app.funcionalidade.execucao.ExecucaoConteudo
import br.com.selebi.app.funcionalidade.execucao.ExecucaoEstado
import br.com.selebi.app.funcionalidade.ordens.AcoesDetalhe
import br.com.selebi.app.funcionalidade.ordens.OrdemDetalheConteudo
import br.com.selebi.app.funcionalidade.ordens.OrdemDetalheEstado
import br.com.selebi.app.funcionalidade.ordens.OrdensConteudo
import br.com.selebi.app.funcionalidade.ordens.OrdensEstado
import br.com.selebi.app.funcionalidade.perfil.PerfilConteudo
import br.com.selebi.app.nucleo.design.componentes.TipoAviso
import br.com.selebi.app.funcionalidade.acesso.AvisoTela
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.AvisoOs
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.TrilhaCampo
import br.com.selebi.app.nucleo.modelo.Usuario
import br.com.selebi.app.navegacao.BarraDeBaixo
import br.com.selebi.app.navegacao.Destino
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fotografa as telas com dados de amostra, sem emulador nem celular.
 *
 *   gradlew :app:testDebugUnitTest --tests "*TelasCapturaTest*"
 *
 * As imagens saem em app/build/telas/. Servem para olhar o desenho sem abrir
 * o app — e, mais adiante, para comparar versão com versão e pegar mudança de
 * layout que ninguém pediu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class TelasCapturaTest {

    @get:Rule
    val regra = createComposeRule()

    private fun capturar(nome: String, escuro: Boolean = false, conteudo: @Composable () -> Unit) {
        regra.setContent { SelebiTema(escuro = escuro) { conteudo() } }
        regra.onRoot().captureRoboImage("build/telas/$nome.png")
    }

    private fun estadoOrdens(): OrdensEstado = Amostras.estadoOrdens()

    /** A tela dentro do app: com a barra de baixo e só as abas que o papel vê. */
    @Composable
    private fun NaCasca(aba: Destino, usuario: Usuario = Amostras.usuario, conteudo: @Composable () -> Unit) {
        val destinos = Destino.entries.filter { it.permissao == null || usuario.pode(it.permissao) }
        Scaffold(
            bottomBar = { BarraDeBaixo(destinos, marcado = { it == aba }, aoIr = {}) },
            contentWindowInsets = WindowInsets(0),
        ) { espaco -> Box(Modifier.padding(espaco)) { conteudo() } }
    }

    @Test
    fun empresa() = capturar("01-empresa") {
        // Código inventado. O de verdade só existe como hash no banco.
        EmpresaConteudo(EmpresaEstado(codigo = "exemplo2026"), {}, {})
    }

    @Test
    fun login() = capturar("02-login") {
        LoginConteudo(LoginEstado(empresa = "BB Soluções", cpf = "52998224725", senha = "12345678"), {}, {}, {}, {}, {})
    }

    @Test
    fun loginAguardando() = capturar("03-login-aguardando") {
        LoginConteudo(
            LoginEstado(
                empresa = "BB Soluções",
                cpf = "52998224725",
                senha = "12345678",
                aviso = AvisoTela(
                    TipoAviso.ESPERA,
                    "Seu cadastro ainda espera a liberação de um administrador da BB Soluções.",
                    "Cadastro em análise",
                ),
            ),
            {}, {}, {}, {}, {},
        )
    }

    @Test
    fun cadastro() = capturar("04-cadastro") {
        CadastroConteudo(
            CadastroEstado(
                empresa = "BB Soluções",
                nome = "Diego Andrade",
                cpf = "529982247",
                senha = "",
                erros = mapOf("cpf" to "Esse CPF não é válido. Confira os números."),
            ),
            {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun cadastroEnviado() = capturar("05-cadastro-enviado") {
        CadastroEnviadoTela(
            "Agora falta um administrador da BB Soluções liberar o seu acesso.",
        ) {}
    }

    @Test
    fun ordens() = capturar("10-ordens") {
        NaCasca(Destino.ORDENS) { OrdensConteudo(estadoOrdens(), {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = "w411dp-h1900dp-xhdpi")
    fun ordensInteira() = capturar("11-ordens-inteira") {
        OrdensConteudo(estadoOrdens(), {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun ordensEscuro() = capturar("12-ordens-escuro", escuro = true) {
        NaCasca(Destino.ORDENS) { OrdensConteudo(estadoOrdens(), {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test
    fun ordensTecnicoSemSinal() = capturar("13-ordens-tecnico-sem-sinal") {
        NaCasca(Destino.ORDENS, usuario = Amostras.tecnico) {
            OrdensConteudo(Amostras.estadoOrdens(usuario = Amostras.tecnico, online = false), {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h1500dp-xhdpi")
    fun detalhe() = capturar("20-detalhe") {
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
    @Config(qualifiers = "w411dp-h2400dp-xhdpi")
    fun detalheEmCampo() = capturar("21-detalhe-em-campo") {
        OrdemDetalheConteudo(
            OrdemDetalheEstado(
                ordem = AmostrasExecucao.instalacaoParaConcluir(),
                atualizando = false,
                agora = Amostras.agora,
                usuario = Amostras.tecnico,
                envios = AmostrasExecucao.enviosVistoria().take(2).map { it.copy(ordemId = "o47", itemId = "i4") },
                online = false,
            ),
            AcoesDetalhe(),
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h2200dp-xhdpi")
    fun detalheConcluida() = capturar("22-detalhe-concluida") {
        OrdemDetalheConteudo(
            OrdemDetalheEstado(
                ordem = AmostrasExecucao.instalacaoConcluida(),
                atualizando = false,
                agora = Amostras.agora,
                usuario = Amostras.tecnico,
                avisos = listOf(AvisoOs(1, "o44", "O projeto avançou para \"Documentação\".")),
            ),
            AcoesDetalhe(),
        )
    }

    // --- em campo (a gestão acompanhando) -------------------------------------

    @Test
    @Config(qualifiers = "w411dp-h1300dp-xhdpi")
    fun emCampo() = capturar("80-em-campo") {
        NaCasca(Destino.CAMPO) {
            EmCampoConteudo(
                EmCampoEstado(
                    carregando = false,
                    tecnicos = AmostrasCampo.tecnicos(),
                    aoVivo = true,
                    selecionado = "o49",
                    trilha = TrilhaCampo(emptyList(), coladaNasRuas = true, marcos = emptyList(), destino = null, posicao = null),
                    agora = AmostrasCampo.momento,
                ),
                {}, {}, {}, {},
                mostrarMapa = false,
            )
        }
    }

    @Test
    fun emCampoVazio() = capturar("81-em-campo-vazio") {
        NaCasca(Destino.CAMPO) {
            EmCampoConteudo(EmCampoEstado(carregando = false, aoVivo = false), {}, {}, {}, {}, mostrarMapa = false)
        }
    }

    // --- execução da OS (o wizard) ------------------------------------------

    private fun execucao(ordem: Ordem, passo: Int, envios: List<Envio> = emptyList(), online: Boolean = true) = ExecucaoEstado(
        carregado = true,
        ordem = ordem,
        passo = passo,
        envios = envios,
        online = online,
        usuario = Amostras.tecnico,
    )

    @Composable
    private fun Wizard(estado: ExecucaoEstado, localizacao: Boolean = true, camada: Camada = Camada.NADA) =
        ExecucaoConteudo(estado, AcoesExecucao(), localizacaoLiberada = localizacao, camada = camada, agora = Amostras.agora)

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xhdpi")
    fun execucaoChegada() = capturar("60-execucao-chegada") {
        Wizard(execucao(AmostrasExecucao.vistoriaAgendada(), 0), localizacao = false)
    }

    @Test
    fun execucaoItemNumero() = capturar("61-execucao-item-numero") {
        Wizard(execucao(AmostrasExecucao.vistoriaEmCampo(), 5, AmostrasExecucao.enviosVistoria(), online = false))
    }

    @Test
    fun execucaoItemEscolha() = capturar("62-execucao-item-escolha") {
        Wizard(execucao(AmostrasExecucao.vistoriaEmCampo(), 7, AmostrasExecucao.enviosVistoria()))
    }

    @Test
    fun execucaoItemMultipla() = capturar("63-execucao-item-multipla") {
        Wizard(execucao(AmostrasExecucao.vistoriaEmCampo(), 13))
    }

    @Test
    fun execucaoItemSerial() = capturar("64-execucao-item-serial") {
        Wizard(execucao(AmostrasExecucao.instalacaoParaConcluir(), 4))
    }

    @Test
    fun execucaoItemFoto() = capturar("65-execucao-item-foto") {
        Wizard(execucao(AmostrasExecucao.vistoriaEmCampo(), 3, AmostrasExecucao.enviosVistoria(), online = false))
    }

    @Test
    fun execucaoItemEscuro() = capturar("66-execucao-item-escuro", escuro = true) {
        Wizard(execucao(AmostrasExecucao.vistoriaEmCampo(), 6))
    }

    @Test
    fun execucaoAntesDeIniciar() = capturar("67-execucao-antes-de-iniciar") {
        Wizard(execucao(AmostrasExecucao.vistoriaAgendada(), 11))
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xhdpi")
    fun execucaoConclusao() = capturar("68-execucao-conclusao") {
        val ordem = AmostrasExecucao.instalacaoParaConcluir()
        Wizard(execucao(ordem, ordem.checklist.size + 1))
    }

    @Test
    @Config(qualifiers = "w411dp-h1100dp-xhdpi")
    fun execucaoConcluida() = capturar("69-execucao-concluida") {
        Wizard(
            execucao(AmostrasExecucao.instalacaoConcluida(), 7).copy(
                concluidaAqui = true,
                avisos = listOf(AvisoOs(1, "o44", "O projeto avançou para \"Documentação\".")),
            ),
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun execucaoIndice() {
        regra.setContent {
            SelebiTema { Wizard(execucao(AmostrasExecucao.vistoriaEmCampo(), 5), camada = Camada.INDICE) }
        }
        captureScreenRoboImage("build/telas/70-execucao-indice.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun execucaoFila() {
        regra.setContent {
            SelebiTema {
                Wizard(execucao(AmostrasExecucao.vistoriaEmCampo(), 5, AmostrasExecucao.enviosComRecusa(), online = false), camada = Camada.FILA)
            }
        }
        captureScreenRoboImage("build/telas/71-execucao-fila.png")
    }

    @Test
    fun agenda() = capturar("30-agenda") { NaCasca(Destino.AGENDA) { AgendaAmostra() } }

    @Composable
    private fun AgendaAmostra() {
        val ordens = Amostras.ordens()
        val dia = Amostras.agora.toLocalDate()
        val segunda = dia.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        AgendaConteudo(
            AgendaEstado(
                selecionado = dia,
                semana = (0L..6L).map { segunda.plusDays(it) }.map { d ->
                    DiaDaSemana(d, ordens.count { it.agendadaPara?.toLocalDate() == d })
                },
                doDia = ordens.filter { it.agendadaPara?.toLocalDate() == dia },
                semData = 2,
                mostrarResponsavel = true,
                agora = Amostras.agora,
            ),
            {}, {}, {}, {},
        )
    }

    @Test
    fun equipe() = capturar("40-equipe") {
        NaCasca(Destino.EQUIPE) {
            EquipeConteudo(
                EquipeEstado(carregando = false, equipe = Amostras.equipe(), meuId = Amostras.usuario.id),
                {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {}, {},
            )
        }
    }

    @Test
    fun perfil() = capturar("50-perfil") {
        NaCasca(Destino.PERFIL) { PerfilConteudo(Amostras.usuario, {}, {}) }
    }
}
