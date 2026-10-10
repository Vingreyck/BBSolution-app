package br.com.selebi.app

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.funcionalidade.acesso.*
import br.com.selebi.app.funcionalidade.agenda.*
import br.com.selebi.app.funcionalidade.equipe.*
import br.com.selebi.app.funcionalidade.ordens.*
import br.com.selebi.app.funcionalidade.perfil.*
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.util.Datas
import br.com.selebi.app.nucleo.util.plusDays

/*
 * As telas com dados de amostra, para o painel de pré-visualização do Android
 * Studio. Ficam aqui, e não junto das telas, porque a pré-visualização só
 * existe no Android. As capturas de verdade são as do `TelasCapturaTest`.
 */

@Preview(showBackground = true)
@Composable
internal fun CadastroEnviadoPreview() {
    SelebiTema {
        CadastroEnviadoTela(
            "Agora falta um administrador da BB Soluções liberar o seu acesso.",
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
internal fun CadastroPreview() {
    SelebiTema {
        CadastroConteudo(
            CadastroEstado(empresa = "BB Soluções", nome = "Kayk Santos", cpf = "529982247", erros = mapOf("cpf" to "Esse CPF não é válido.")),
            {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun EmpresaPreview() {
    SelebiTema { EmpresaConteudo(EmpresaEstado(codigo = "solucoes"), {}, {}) }
}

@Preview(showBackground = true)
@Composable
internal fun LoginPreview() {
    SelebiTema {
        LoginConteudo(
            LoginEstado(empresa = "BB Soluções", cpf = "52998224725", senha = "senha"),
            {}, {}, {}, {}, {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun TrocarSenhaPreview() {
    SelebiTema { TrocarSenhaConteudo(TrocarSenhaEstado(), obrigatoria = true, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
internal fun AgendaPreview() {
    SelebiTema {
        val ordens = Amostras.ordens()
        val dia = Amostras.agora.date
        AgendaConteudo(
            AgendaEstado(
                selecionado = dia,
                semana = (0L..6L).map { Datas.segundaDaSemana(dia).plusDays(it) }.map { d -> DiaDaSemana(d, ordens.count { it.agendadaPara?.date == d }) },
                doDia = ordens.filter { it.agendadaPara?.date == dia },
                agora = Amostras.agora,
            ),
            {}, {}, {}, {},
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
internal fun EquipePreview() {
    SelebiTema {
        EquipeConteudo(
            EquipeEstado(carregando = false, equipe = Amostras.equipe(), meuId = Amostras.usuario.id),
            {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, {}, {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
internal fun OrdemDetalhePreview() {
    SelebiTema {
        OrdemDetalheConteudo(
            OrdemDetalheEstado(
                ordem = Amostras.ordens().first(),
                atualizando = false,
                mostrarResponsavel = true,
                agora = Amostras.agora,
                usuario = Amostras.tecnico,
            ),
            AcoesDetalhe(),
        )
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
internal fun OrdensPreview() {
    SelebiTema {
        OrdensConteudo(
            Amostras.estadoOrdens(),
            {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun PerfilPreview() {
    SelebiTema { PerfilConteudo(Amostras.usuario, {}, {}) }
}
