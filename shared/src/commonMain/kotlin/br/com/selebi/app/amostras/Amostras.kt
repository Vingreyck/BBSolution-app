package br.com.selebi.app.amostras

import br.com.selebi.app.funcionalidade.ordens.EscopoOrdens
import br.com.selebi.app.funcionalidade.ordens.FiltroOrdens
import br.com.selebi.app.funcionalidade.ordens.OrdensEstado
import br.com.selebi.app.funcionalidade.ordens.montarPainel
import br.com.selebi.app.nucleo.modelo.ClienteResumo
import br.com.selebi.app.nucleo.modelo.Empresa
import br.com.selebi.app.nucleo.modelo.Equipe
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.Membro
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Papel
import br.com.selebi.app.nucleo.modelo.Permissao
import br.com.selebi.app.nucleo.modelo.Pessoa
import br.com.selebi.app.nucleo.modelo.Prioridade
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TipoOs
import br.com.selebi.app.nucleo.modelo.UsinaResumo
import br.com.selebi.app.nucleo.modelo.Usuario
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Dados de mentira para o Preview do Android Studio e para as capturas de tela.
 *
 * Nomes e endereços **inventados** — nada de cliente real aqui: este arquivo
 * vai para o repositório. As situações, essas sim, vêm das vistorias reais: a
 * usina sem comunicar há dias, a instalação em andamento, a vistoria marcada.
 */
object Amostras {
    /** Sexta, 25 de setembro de 2026, 9h40 — um dia de trabalho qualquer. */
    val agora: LocalDateTime = LocalDateTime(2026, 9, 25, 9, 40)
    private val fuso = TimeZone.of("America/Sao_Paulo")
    private fun instante(dt: LocalDateTime): Instant = dt.toInstant(fuso)

    private val empresa = Empresa("e1", "BB Soluções")

    val usuario = Usuario(
        id = "u1",
        nome = "Vinícius Lima Santos",
        cpf = "529.982.247-25",
        email = null,
        papel = Papel.ADM,
        deveTrocarSenha = false,
        empresa = empresa,
        permissoes = setOf(Permissao.VER_TODAS_OS, Permissao.GERIR_EQUIPE),
    )

    val tecnico = Usuario(
        id = "u2",
        nome = "Diego Andrade",
        cpf = "111.444.777-35",
        email = null,
        papel = Papel.TECNICO,
        deveTrocarSenha = false,
        empresa = empresa,
        permissoes = emptySet(),
    )

    private val diego = Pessoa("u2", "Diego Andrade")
    private val rafael = Pessoa("u3", "Rafael Oliveira")

    private fun ordem(
        id: String,
        numero: Int,
        tipo: TipoOs,
        status: StatusOs,
        cliente: String,
        endereco: String?,
        cidade: String,
        descricao: String,
        agendada: LocalDateTime? = null,
        prazo: LocalDateTime? = null,
        prioridade: Prioridade = Prioridade.NORMAL,
        responsavel: Pessoa? = diego,
        telefone: String? = "79999990000",
        usina: UsinaResumo? = null,
        checklist: List<ItemChecklist> = emptyList(),
        iniciada: LocalDateTime? = null,
        concluida: LocalDateTime? = null,
    ) = Ordem(
        id = id,
        numero = numero,
        tipo = tipo,
        status = status,
        prioridade = prioridade,
        descricao = descricao,
        laudo = null,
        agendadaPara = agendada,
        prazoSla = prazo,
        iniciadaEm = iniciada?.let(::instante),
        concluidaEm = concluida?.let(::instante),
        criadoEm = instante(agora.minusDays(6)),
        cliente = ClienteResumo("c$id", cliente, telefone, endereco, cidade, "SE"),
        usina = usina,
        responsavel = responsavel,
        checklist = checklist,
    )

    private fun itens(vararg descricoes: Pair<String, Boolean>) = descricoes.mapIndexed { i, (texto, feito) ->
        ItemChecklist("i$i", i, texto, obrigatorio = true, concluido = feito, observacao = null)
    }

    fun ordens(): List<Ordem> = listOf(
        // A instalação em curso e a concluída vêm com o checklist de verdade,
        // respostas, fotos, notas e histórico — são as mesmas da execução.
        AmostrasExecucao.instalacaoParaConcluir(),
        ordem(
            "o49", 49, TipoOs.VISTORIA, StatusOs.AGENDADA,
            "Antônio Carlos Menezes", "Travessa São José, 45 · Libório", "Lagarto",
            "Vistoria para 11 módulos. Conferir espaço no quadro de distribuição e o caminho do cabo até o telhado.",
            agendada = agora.withHour(14).withMinute(0), responsavel = rafael,
        ),
        ordem(
            "o41", 41, TipoOs.CORRETIVA, StatusOs.ABERTA,
            "Mercado São José", "Av. Otoniel Dória, 880 · Centro", "Itabaiana",
            "Usina sem comunicar há 9 dias. Conferir o datalogger e o disjuntor do inversor.",
            prazo = agora.minusDays(1).withHour(18).withMinute(0), prioridade = Prioridade.URGENTE, responsavel = null,
            usina = UsinaResumo("us2", "Mercado Sao Jose", 12.5, -10.68, -37.42),
            checklist = itens("Código de erro no display" to false, "Foto do quadro e do inversor" to false, "Geração depois da visita" to false),
        ),
        ordem(
            "o50", 50, TipoOs.VISTORIA, StatusOs.AGENDADA,
            "Cláudia Ramos", "Povoado Gandu", "Itabaiana",
            "Vistoria rural. Levar drone para o telhado.",
            agendada = agora.plusDays(1).withHour(9).withMinute(0), prioridade = Prioridade.ALTA,
        ),
        ordem(
            "o51", 51, TipoOs.LIMPEZA, StatusOs.AGENDADA,
            "Pedro Henrique Santos", "Rua do Sol, 12", "Macambira",
            "Limpeza dos módulos. Geração 18% abaixo do esperado no último mês.",
            agendada = agora.plusDays(2).withHour(10).withMinute(30), responsavel = rafael,
        ),
        ordem(
            "o39", 39, TipoOs.GARANTIA, StatusOs.AGUARDANDO_PECA,
            "Roberto Alves", "Rua Projetada, 846 · Porto", "Itabaiana",
            "Microinversor com defeito. Aguardando a troca pelo fabricante.",
        ),
        ordem(
            "o46", 46, TipoOs.PREVENTIVA, StatusOs.ABERTA,
            "Condomínio Solar das Acácias", "Av. Principal, 300", "Campo do Brito",
            "Revisão anual: aperto de conexões, DPS e aterramento.",
            prioridade = Prioridade.BAIXA,
        ),
        AmostrasExecucao.instalacaoConcluida(),
    )

    fun estadoOrdens(usuario: Usuario = this.usuario, online: Boolean = true): OrdensEstado {
        val lista = if (usuario.pode(Permissao.VER_TODAS_OS)) ordens() else ordens().filter { it.responsavel?.id == usuario.id }
        return OrdensEstado(
            carregando = false,
            usuario = usuario,
            painel = montarPainel(lista, usuario.id, EscopoOrdens.EQUIPE, FiltroOrdens.TODAS, "", agora),
            online = online,
            sincronizadoEm = instante(agora.minusMinutes(25)),
            agora = agora,
        )
    }

    fun equipe(): Equipe {
        fun membro(id: String, nome: String, papel: Papel, cpf: String?, ativo: Boolean = true, provisoria: Boolean = false, app: Boolean = true) = Membro(
            id = id,
            nome = nome,
            cpf = cpf,
            cpfOculto = cpf?.let { "${it.take(3)}.***.***-${it.takeLast(2)}" },
            email = null,
            papel = papel,
            ativo = ativo,
            senhaProvisoria = provisoria,
            usaApp = app,
            criadoEm = instante(agora.minusDays(20)),
            aprovadoEm = instante(agora.minusDays(19)),
        )
        return Equipe(
            pendentes = listOf(
                membro("p1", "Marcos Batista", Papel.TECNICO, "123.456.789-09").copy(criadoEm = instante(agora.minusHours(3)), aprovadoEm = null, ativo = false),
                membro("p2", "Karla Menezes", Papel.TECNICO, "987.654.321-00").copy(criadoEm = instante(agora.minusDays(1)), aprovadoEm = null, ativo = false, email = "karla@exemplo.com"),
            ),
            membros = listOf(
                membro("u1", "Vinícius Lima Santos", Papel.ADM, "529.982.247-25"),
                membro("u2", "Diego Andrade", Papel.TECNICO, "111.444.777-35"),
                membro("u3", "Rafael Oliveira", Papel.TECNICO, "222.333.444-05", provisoria = true),
                membro("u4", "Tiago Machado", Papel.ENGENHEIRO, "333.222.111-80"),
                membro("u5", "Paula Souza", Papel.OUTRO, null, app = false),
                membro("u6", "Jorge Menezes", Papel.TECNICO, "444.555.666-12", ativo = false),
            ),
        )
    }
}
