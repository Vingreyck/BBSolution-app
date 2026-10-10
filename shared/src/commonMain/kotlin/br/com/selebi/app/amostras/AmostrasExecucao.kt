package br.com.selebi.app.amostras

import br.com.selebi.app.nucleo.modelo.AcaoOs
import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.modelo.ClienteResumo
import br.com.selebi.app.nucleo.modelo.Coordenada
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.EventoOs
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.ModoRastreio
import br.com.selebi.app.nucleo.modelo.NotaOs
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Pessoa
import br.com.selebi.app.nucleo.modelo.PosicaoCampo
import br.com.selebi.app.nucleo.modelo.Prioridade
import br.com.selebi.app.nucleo.modelo.ProjetoResumo
import br.com.selebi.app.nucleo.modelo.Resposta
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TecnicoEmCampo
import br.com.selebi.app.nucleo.modelo.TipoEnvio
import br.com.selebi.app.nucleo.modelo.TipoOs
import br.com.selebi.app.nucleo.modelo.TipoResposta
import br.com.selebi.app.nucleo.modelo.UsinaResumo
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * As OS de amostra da execução: a vistoria com o roteiro inteiro, a
 * instalação para concluir e a concluída com relatório.
 *
 * Os itens são os mesmos do modelo do servidor (`src/os/modelos.ts`) — é
 * assim que o técnico vai ver. Nomes, endereços e números **inventados**.
 */
object AmostrasExecucao {
    private val agora: LocalDateTime get() = Amostras.agora
    private val fuso = TimeZone.of("America/Sao_Paulo")
    private fun instante(dt: LocalDateTime): Instant = dt.toInstant(fuso)

    private val diego = Pessoa("u2", "Diego Andrade")

    private fun item(
        id: String,
        ordem: Int,
        secao: String?,
        descricao: String,
        tipo: TipoResposta,
        obrigatorio: Boolean = true,
        fotos: Int = 0,
        camera: Boolean = false,
        opcoes: List<String> = emptyList(),
        unidade: String? = null,
        ajuda: String? = null,
    ) = ItemChecklist(
        id = id,
        ordem = ordem,
        descricao = descricao,
        obrigatorio = obrigatorio,
        concluido = false,
        observacao = null,
        secao = secao,
        ajuda = ajuda,
        tipoResposta = tipo,
        opcoes = opcoes,
        unidade = unidade,
        fotosMinimas = fotos,
        apenasCamera = camera,
    )

    /** O roteiro da vistoria — os 12 pontos das 10 vistorias do WhatsApp, em 17 itens. */
    fun itensVistoria(): List<ItemChecklist> = listOf(
        item("v01", 10, "Cliente e unidade", "Número da unidade consumidora (UC)", TipoResposta.TEXTO, fotos = 1,
            ajuda = "Está na conta de luz. Fotografe a conta inteira, com o nome do titular aparecendo."),
        item("v02", 20, "Cliente e unidade", "Nome do titular da conta de luz", TipoResposta.TEXTO,
            ajuda = "Como está na conta — nem sempre é quem está atendendo, e é o nome que vai no projeto."),
        item("v03", 30, "Cliente e unidade", "Fachada com o número da casa", TipoResposta.FOTO, fotos = 1, camera = true),
        item("v04", 40, "Padrão de entrada", "Número do medidor", TipoResposta.TEXTO, fotos = 1, camera = true),
        item("v05", 50, "Padrão de entrada", "Disjuntor do padrão — amperagem", TipoResposta.NUMERO, fotos = 1, camera = true,
            unidade = "A", ajuda = "Fotografe de perto, com o número legível."),
        item("v06", 60, "Padrão de entrada", "Disjuntor do padrão — polos", TipoResposta.ESCOLHA,
            opcoes = listOf("Monopolar", "Bipolar", "Tripolar")),
        item("v07", 70, "Padrão de entrada", "Bitola do cabo de entrada", TipoResposta.ESCOLHA, fotos = 1, camera = true,
            opcoes = listOf("4 mm²", "6 mm²", "10 mm²", "16 mm²", "25 mm²", "35 mm²", "Não deu para ver")),
        item("v08", 80, "Padrão de entrada", "Aterramento", TipoResposta.ESCOLHA, fotos = 1, camera = true,
            opcoes = listOf("Existe e está bom", "Existe, mas precisa refazer", "Não existe")),
        item("v09", 90, "Quadro e cabos", "Vagas livres no quadro de distribuição (QDG)", TipoResposta.NUMERO, fotos = 1,
            camera = true, unidade = "vagas", ajuda = "Para o disjuntor da usina. Fotografe o quadro aberto."),
        item("v10", 100, "Quadro e cabos", "Caminho do cabo até o quadro e distância", TipoResposta.TEXTO, fotos = 1,
            camera = true, ajuda = "Ex.: \"desce pela parede lateral, uns 15 m até o QDG na cozinha\"."),
        item("v11", 110, "Quadro e cabos", "Vai precisar rasgar parede?", TipoResposta.SIM_NAO),
        item("v12", 120, "Telhado", "Tipo de telhado", TipoResposta.ESCOLHA, fotos = 2, camera = true,
            opcoes = listOf("Cerâmica", "Colonial", "Fibrocimento", "Metálico", "Laje", "Outro")),
        item("v13", 130, "Telhado", "Estado do telhado", TipoResposta.MULTIPLA,
            opcoes = listOf("Bom", "Telhas quebradas", "Madeiramento comprometido", "Sinais de infiltração"),
            ajuda = "Telha quebrada ou infiltração que já existe precisa ficar registrada antes da instalação."),
        item("v14", 140, "Telhado", "Sombra sobre o telhado", TipoResposta.ESCOLHA, fotos = 1, camera = true,
            opcoes = listOf("Sem sombra", "Sombra de manhã", "Sombra à tarde", "Muita sombra")),
        item("v15", 150, "Telhado", "Espaço útil para os módulos", TipoResposta.NUMERO, unidade = "m²"),
        item("v16", 160, "Inversor e futuro", "Onde vai ficar o inversor", TipoResposta.TEXTO, fotos = 1, camera = true,
            ajuda = "Lugar ventilado, longe de sol direto e com acesso para manutenção."),
        item("v17", 170, "Inversor e futuro", "Aumento de consumo previsto", TipoResposta.TEXTO, obrigatorio = false,
            ajuda = "Ar-condicionado, piscina, carro elétrico… Ex.: \"2 ar-condicionados até o verão\"."),
    )

    private val instrucoesVistoria =
        "Todas as fotos pela câmera do app, na hora — print do Street View não vale. Se algo não existir " +
            "(sem aterramento, sem vaga no quadro), responda assim mesmo e fotografe: é isso que o engenheiro " +
            "precisa saber antes de fazer o projeto."

    private fun foto(id: String, itemId: String?, minutosAtras: Long, pendente: Boolean = false, categoria: String = "foto") = AnexoOs(
        id = id,
        itemId = itemId,
        categoria = categoria,
        capturadoEm = instante(agora.minusMinutes(minutosAtras)),
        pendente = pendente,
        enviadoPorId = "u2",
    )

    private fun evento(id: String, tipo: String, titulo: String, detalhe: String?, quem: String?, origem: String, quando: LocalDateTime) =
        EventoOs(id, tipo, titulo, detalhe, quem, origem, instante(quando))

    private val recadoDoEscritorio = NotaOs(
        id = "n1",
        texto = "Cliente pediu para ligar 30 min antes — tem cachorro solto no quintal.",
        autorId = "u5",
        autor = "Paula Souza",
        criadoEm = instante(agora.minusDays(1).withHour(16).withMinute(20)),
    )

    private fun vistoriaBase(status: StatusOs, itens: List<ItemChecklist>, anexos: List<AnexoOs>, historico: List<EventoOs>) = Ordem(
        id = "o49",
        numero = 49,
        tipo = TipoOs.VISTORIA,
        status = status,
        prioridade = Prioridade.NORMAL,
        descricao = "Vistoria para 11 módulos. Conferir espaço no quadro de distribuição e o caminho do cabo até o telhado.",
        laudo = null,
        agendadaPara = agora.withHour(14).withMinute(0),
        prazoSla = agora.plusDays(2).withHour(10).withMinute(0),
        iniciadaEm = if (status == StatusOs.EM_ANDAMENTO) instante(agora.withHour(14).withMinute(8)) else null,
        concluidaEm = null,
        criadoEm = instante(agora.minusDays(3)),
        cliente = ClienteResumo("c49", "Antônio Carlos Menezes", "79999990000", "Travessa São José, 45 · Libório", "Lagarto", "SE"),
        usina = null,
        responsavel = diego,
        checklist = itens,
        projeto = ProjetoResumo("p49", "Antônio Carlos — 6,05 kWp"),
        instrucoes = instrucoesVistoria,
        anexos = anexos,
        historico = historico,
        notas = listOf(recadoDoEscritorio),
    )

    private val historicoVistoria = listOf(
        evento("e3", "agendada", "Agendada", "para sex, 25/09 às 14:00", "Paula Souza", "web", agora.minusDays(2)),
        evento("e2", "atribuida", "Responsável definido", "para Diego Andrade", "Paula Souza", "web", agora.minusDays(3).plusMinutes(2)),
        evento("e1", "criada", "Aberta", null, "Paula Souza", "web", agora.minusDays(3)),
    )

    /** A vistoria das 14h, antes de sair: o que a tela de chegada mostra. */
    fun vistoriaAgendada(): Ordem = vistoriaBase(StatusOs.AGENDADA, itensVistoria(), emptyList(), historicoVistoria)

    /**
     * A mesma vistoria no meio: seis itens cumpridos, o disjuntor anotado mas
     * sem a foto, a bitola ainda sem resposta.
     */
    fun vistoriaEmCampo(): Ordem {
        val base = itensVistoria()
        fun responder(id: String, valor: Resposta?, cumprido: Boolean, observacao: String? = null) =
            base.first { it.id == id }.copy(valor = valor, concluido = cumprido, observacao = observacao, respondidoEm = instante(agora))
        val respondidos = mapOf(
            "v01" to responder("v01", Resposta.Texto("3012345678"), true),
            "v02" to responder("v02", Resposta.Texto("Antônio Carlos Menezes"), true),
            "v03" to base.first { it.id == "v03" }.copy(concluido = true),
            "v04" to responder("v04", Resposta.Texto("A1B2C3D4"), true),
            "v05" to responder("v05", Resposta.Numero(40.0), false),
            "v06" to responder("v06", Resposta.Texto("Bipolar"), true, observacao = "Disjuntor novo, trocado pela Energisa em agosto."),
        )
        val itens = base.map { respondidos[it.id] ?: it }
        val anexos = listOf(
            foto("a1", "v01", 12),
            foto("a2", "v03", 9, pendente = true),
            foto("a3", "v04", 6, pendente = true),
        )
        val historico = listOf(
            evento("e5", "iniciada", "Atendimento iniciado", null, "Diego Andrade", "app", agora.withHour(14).withMinute(8)),
            evento("e4", "deslocamento", "Saiu para o local", null, "Diego Andrade", "app", agora.withHour(13).withMinute(31)),
        ) + historicoVistoria
        return vistoriaBase(StatusOs.EM_ANDAMENTO, itens, anexos, historico)
    }

    /** O que a vistoria no meio tem na fila: duas fotos e duas respostas sem sinal. */
    fun enviosVistoria(): List<Envio> = listOf(
        Envio("f1", "o49", TipoEnvio.FOTO, "v03", null, instante(agora.minusMinutes(9)), null),
        Envio("f2", "o49", TipoEnvio.FOTO, "v04", null, instante(agora.minusMinutes(6)), null),
        Envio("f3", "o49", TipoEnvio.RESPOSTA, "v05", null, instante(agora.minusMinutes(4)), null),
        Envio("f4", "o49", TipoEnvio.RESPOSTA, "v06", null, instante(agora.minusMinutes(3)), null),
    )

    /** Uma recusa do servidor, para a folha da fila. */
    fun enviosComRecusa(): List<Envio> = enviosVistoria().take(2) + Envio(
        "f9", "o49", TipoEnvio.ACAO, null, AcaoOs.PAUSAR, instante(agora.minusMinutes(20)),
        "Não dá para pausar uma OS que está \"Concluída\".",
    )

    // --- instalação ----------------------------------------------------------

    private fun itensInstalacao(): List<ItemChecklist> = listOf(
        item("i1", 10, "Antes de começar", "Foto do telhado antes da instalação", TipoResposta.FOTO, fotos = 1, camera = true)
            .copy(concluido = true),
        item("i2", 20, "Módulos", "Quantidade de módulos instalados", TipoResposta.NUMERO, unidade = "un")
            .copy(valor = Resposta.Numero(8.0), concluido = true),
        item("i3", 30, "Inversor", "Marca do inversor instalado", TipoResposta.ESCOLHA,
            opcoes = listOf("Growatt", "Solis", "Huawei", "FoxESS", "Hoymiles", "GoodWe", "NEP", "Outra"),
            ajuda = "A que foi instalada de fato — em 3 de 6 instalações não era a do projeto.")
            .copy(valor = Resposta.Texto("Hoymiles"), concluido = true),
        item("i4", 40, "Inversor", "Número de série do inversor", TipoResposta.SERIAL, fotos = 1, camera = true,
            ajuda = "Um por inversor. É por ele que o sistema reconhece a usina no portal do fabricante e liga ao cliente sozinho. Fotografe a etiqueta.")
            .copy(valor = Resposta.Lista(listOf("HMS2000A1B2C3")), concluido = false),
        item("i5", 50, "Padrão de entrada", "Placa de advertência instalada no padrão", TipoResposta.SIM_NAO,
            ajuda = "\"Cuidado — risco de choque elétrico — geração própria\", exigência da NDU 013."),
        item("i6", 60, "Entrega", "Cliente orientado sobre o uso e o app de monitoramento", TipoResposta.CHECK)
            .copy(valor = Resposta.Marcado(true), concluido = true),
    )

    /** A instalação da Luciana: quatro de seis, com a assinatura ainda por coletar. */
    fun instalacaoParaConcluir(): Ordem = Ordem(
        id = "o47",
        numero = 47,
        tipo = TipoOs.INSTALACAO,
        status = StatusOs.EM_ANDAMENTO,
        prioridade = Prioridade.NORMAL,
        descricao = "Instalar 8 módulos de 710 W e 2 microinversores. Padrão de entrada já confere com o projeto.",
        laudo = null,
        agendadaPara = agora.withHour(8).withMinute(0),
        prazoSla = null,
        iniciadaEm = instante(agora.withHour(8).withMinute(20)),
        concluidaEm = null,
        criadoEm = instante(agora.minusDays(6)),
        cliente = ClienteResumo("co47", "Luciana Freitas de Almeida", "79999990000", "Rua das Palmeiras, 120 · Centro", "Itabaiana", "SE"),
        usina = UsinaResumo("us1", "Luciana Freitas", 5.68, null, null),
        responsavel = diego,
        checklist = itensInstalacao(),
        projeto = ProjetoResumo("p47", "Luciana Freitas — 5,68 kWp"),
        instrucoes = "Confira o equipamento com o projeto antes de subir no telhado. Se o que chegou for diferente do projeto, anote no laudo — o engenheiro precisa saber.",
        exigeAssinatura = true,
        anexos = listOf(foto("b1", "i1", 90)),
        historico = listOf(
            evento("g2", "iniciada", "Atendimento iniciado", null, "Diego Andrade", "app", agora.withHour(8).withMinute(20)),
            evento("g1", "agendada", "Agendada", "para sex, 25/09 às 08:00", "Paula Souza", "web", agora.minusDays(4)),
        ),
        notas = listOf(
            NotaOs("n7", "Chegou só um microinversor; o outro vem na segunda.", "u2", null, instante(agora.minusMinutes(40)), pendente = true),
        ),
    )

    /** A instalação da Fernanda, concluída ontem, com o link do relatório. */
    fun instalacaoConcluida(): Ordem = Ordem(
        id = "o44",
        numero = 44,
        tipo = TipoOs.INSTALACAO,
        status = StatusOs.CONCLUIDA,
        prioridade = Prioridade.NORMAL,
        descricao = "Instalação concluída. Usina aparecendo no portal.",
        laudo = "Sistema de 4,26 kWp instalado e gerando. Inversor Growatt no muro da garagem, à sombra.",
        agendadaPara = agora.minusDays(1).withHour(8).withMinute(0),
        prazoSla = null,
        iniciadaEm = instante(agora.minusDays(1).withHour(8).withMinute(12)),
        concluidaEm = instante(agora.minusDays(1).withHour(16).withMinute(10)),
        criadoEm = instante(agora.minusDays(9)),
        cliente = ClienteResumo("co44", "Fernanda Cruz", "79999990000", "Povoado Manuino", "Macambira", "SE"),
        usina = UsinaResumo("us4", "Fernanda Cruz", 4.26, null, null),
        responsavel = diego,
        checklist = itensInstalacao().map { it.copy(concluido = true, valor = it.valor ?: Resposta.Marcado(true)) },
        projeto = ProjetoResumo("p44", "Fernanda Cruz — 4,26 kWp"),
        resultado = "resolvido",
        assinaturaNome = "Fernanda Cruz",
        exigeAssinatura = true,
        anexos = listOf(foto("c1", "i1", 60 * 30), foto("c2", "i4", 60 * 26), foto("c9", null, 60 * 24, categoria = "assinatura")),
        relatorio = "/r/exemploDeLinkDoRelatorio0000000000",
        historico = listOf(
            evento("h4", "esteira", "Projeto avançou na esteira", "para \"Documentação\"", "BBSolution", "sistema", agora.minusDays(1).withHour(16).withMinute(10)),
            evento("h3", "concluida", "Concluída", "Resolvido", "Diego Andrade", "app", agora.minusDays(1).withHour(16).withMinute(10)),
            evento("h2", "iniciada", "Atendimento iniciado", null, "Diego Andrade", "app", agora.minusDays(1).withHour(8).withMinute(12)),
            evento("h1", "deslocamento", "Saiu para o local", null, "Diego Andrade", "app", agora.minusDays(1).withHour(7).withMinute(30)),
        ),
    )
}

/** Quem está em campo, para a captura da tela da gestão: ao vivo, atrasado e sem sinal. */
object AmostrasCampo {
    private val agora: LocalDateTime get() = Amostras.agora
    private fun instante(dt: LocalDateTime): Instant = dt.toInstant(TimeZone.of("America/Sao_Paulo"))
    val momento: Instant get() = instante(agora)

    fun tecnicos(): List<TecnicoEmCampo> = listOf(
        TecnicoEmCampo(
            ordemId = "o49", numero = 49, tipo = TipoOs.VISTORIA, status = StatusOs.EM_DESLOCAMENTO,
            clienteNome = "Antônio Carlos Menezes", cidade = "Lagarto", uf = "SE", telefone = "79999990000",
            tecnico = Pessoa("u2", "Diego Andrade"), desde = instante(agora.minusMinutes(22)),
            destino = Coordenada(-10.9170, -37.6500),
            posicao = PosicaoCampo(-10.9300, -37.6350, 7.0, 11.7, 71, ModoRastreio.DESLOCAMENTO, instante(agora.minusSeconds(6))),
            distanciaDestinoM = 1_840,
        ),
        TecnicoEmCampo(
            ordemId = "o41", numero = 41, tipo = TipoOs.CORRETIVA, status = StatusOs.EM_ANDAMENTO,
            clienteNome = "Mercado São José", cidade = "Itabaiana", uf = "SE", telefone = "79999990000",
            tecnico = Pessoa("u3", "Rafael Oliveira"), desde = instante(agora.minusMinutes(48)),
            destino = Coordenada(-10.6849, -37.4262),
            posicao = PosicaoCampo(-10.6851, -37.4264, 60.0, 0.0, 14, ModoRastreio.ECO, instante(agora.minusMinutes(3))),
            distanciaDestinoM = 30,
        ),
        TecnicoEmCampo(
            ordemId = "o52", numero = 52, tipo = TipoOs.LIMPEZA, status = StatusOs.EM_DESLOCAMENTO,
            clienteNome = "Pedro Henrique Santos", cidade = "Macambira", uf = "SE", telefone = null,
            tecnico = Pessoa("u7", "Tiago Machado"), desde = instante(agora.minusMinutes(35)),
            destino = null,
            posicao = PosicaoCampo(-10.66, -37.54, 12.0, 14.0, 52, ModoRastreio.DESLOCAMENTO, instante(agora.minusMinutes(6))),
            distanciaDestinoM = null,
        ),
    )
}
