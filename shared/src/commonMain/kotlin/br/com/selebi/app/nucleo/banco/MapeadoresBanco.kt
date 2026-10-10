package br.com.selebi.app.nucleo.banco

import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.modelo.ClienteResumo
import br.com.selebi.app.nucleo.modelo.EventoOs
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.NotaOs
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Pessoa
import br.com.selebi.app.nucleo.modelo.Prioridade
import br.com.selebi.app.nucleo.modelo.ProjetoResumo
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TipoOs
import br.com.selebi.app.nucleo.modelo.TipoResposta
import br.com.selebi.app.nucleo.modelo.UsinaResumo
import br.com.selebi.app.nucleo.rede.EventoDto
import br.com.selebi.app.nucleo.rede.OrdemDto
import br.com.selebi.app.nucleo.rede.opcoesDoTexto
import br.com.selebi.app.nucleo.rede.opcoesParaTexto
import br.com.selebi.app.nucleo.rede.respostaDoTexto
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant

/** O histórico viaja e fica guardado em JSON; campo novo do servidor não quebra a leitura. */
private val jsonHistorico = Json { ignoreUnknownKeys = true }
private val listaDeEventos = ListSerializer(EventoDto.serializer())

fun OrdemDto.paraEntidade(): OrdemEntidade = OrdemEntidade(
    id = id,
    numero = numero,
    tipo = tipo,
    status = status,
    prioridade = prioridade,
    descricao = descricao,
    laudo = laudo,
    agendadaPara = agendadaPara,
    prazoSla = prazoSla,
    iniciadaEm = iniciadaEm,
    concluidaEm = concluidaEm,
    criadoEm = criadoEm,
    cliente = ClienteEmbutido(
        id = cliente.id,
        nome = cliente.nome,
        telefone = cliente.telefone,
        endereco = cliente.endereco,
        cidade = cliente.cidade,
        uf = cliente.uf,
    ),
    usina = usina?.let { UsinaEmbutida(it.id, it.nome, it.potenciaKwp, it.latitude, it.longitude) },
    responsavelId = responsavel?.id,
    responsavelNome = responsavel?.nome,
    projetoId = projeto?.id,
    projetoTitulo = projeto?.titulo,
    resultado = resultado,
    assinaturaNome = assinaturaNome,
    instrucoes = instrucoes,
    exigeAssinatura = exigeAssinatura,
    atualizadoEm = atualizadoEm,
    relatorio = relatorio,
    historicoJson = jsonHistorico.encodeToString(listaDeEventos, historico),
)

fun OrdemDto.itensParaEntidade(): List<ChecklistItemEntidade> = checklist.map {
    ChecklistItemEntidade(
        id = it.id,
        ordemId = id,
        ordem = it.ordem,
        descricao = it.descricao,
        obrigatorio = it.obrigatorio,
        concluido = it.concluido,
        observacao = it.observacao,
        secao = it.secao,
        ajuda = it.ajuda,
        tipoResposta = it.tipoResposta,
        opcoesJson = opcoesParaTexto(it.opcoes),
        unidade = it.unidade,
        fotosMinimas = it.fotosMinimas,
        apenasCamera = it.apenasCamera,
        valorJson = it.valor?.toString()?.takeIf { v -> v != "null" },
        respondidoEm = it.respondidoEm,
    )
}

/**
 * Os anexos do servidor. `arquivoDe` diz onde está a foto no celular quando
 * ela foi tirada aqui — o nome do arquivo passa a ser o id do servidor depois
 * do envio — para a miniatura continuar aparecendo sem baixar de novo.
 */
fun OrdemDto.anexosParaEntidade(arquivoDe: (ordemId: String, anexoId: String) -> String?): List<AnexoEntidade> =
    anexos.map {
        AnexoEntidade(
            id = it.id,
            ordemId = id,
            itemId = it.itemId,
            categoria = it.categoria,
            capturadoEm = it.capturadoEm,
            arquivoLocal = arquivoDe(id, it.id),
            pendente = false,
            enviadoPorId = it.enviadoPorId,
        )
    }

fun OrdemDto.notasParaEntidade(): List<NotaEntidade> = notas.map {
    NotaEntidade(
        id = it.id,
        ordemId = id,
        texto = it.texto,
        autorId = it.autorId,
        autor = it.autor,
        criadoEm = it.criadoEm,
        pendente = false,
    )
}

fun ChecklistItemEntidade.paraModelo(): ItemChecklist = ItemChecklist(
    id = id,
    ordem = ordem,
    descricao = descricao,
    obrigatorio = obrigatorio,
    concluido = concluido,
    observacao = observacao,
    secao = secao,
    ajuda = ajuda,
    tipoResposta = TipoResposta.daApi(tipoResposta),
    opcoes = opcoesDoTexto(opcoesJson),
    unidade = unidade,
    fotosMinimas = fotosMinimas,
    apenasCamera = apenasCamera,
    valor = respostaDoTexto(valorJson),
    respondidoEm = Datas.lerInstante(respondidoEm),
)

private fun historicoDe(texto: String): List<EventoOs> =
    runCatching { jsonHistorico.decodeFromString(listaDeEventos, texto) }.getOrDefault(emptyList()).map {
        EventoOs(
            id = it.id,
            tipo = it.tipo,
            titulo = it.titulo,
            detalhe = it.detalhe,
            quem = it.quem,
            origem = it.origem,
            ocorridoEm = Datas.lerInstante(it.ocorridoEm),
        )
    }

fun OrdemComChecklist.paraModelo(): Ordem = with(ordem) {
    Ordem(
        id = id,
        numero = numero,
        tipo = TipoOs.daApi(tipo),
        status = StatusOs.daApi(status),
        prioridade = Prioridade.daApi(prioridade),
        descricao = descricao,
        laudo = laudo,
        agendadaPara = Datas.lerRelogio(agendadaPara),
        prazoSla = Datas.lerRelogio(prazoSla),
        iniciadaEm = Datas.lerInstante(iniciadaEm),
        concluidaEm = Datas.lerInstante(concluidaEm),
        criadoEm = Datas.lerInstante(criadoEm) ?: Instant.fromEpochSeconds(0),
        cliente = ClienteResumo(
            id = cliente.id,
            nome = cliente.nome,
            telefone = cliente.telefone,
            endereco = cliente.endereco,
            cidade = cliente.cidade,
            uf = cliente.uf,
        ),
        usina = usina?.let { UsinaResumo(it.id, it.nome, it.potenciaKwp, it.latitude, it.longitude) },
        responsavel = if (responsavelId != null && responsavelNome != null) {
            Pessoa(responsavelId, responsavelNome)
        } else {
            null
        },
        checklist = itens.sortedBy { it.ordem }.map { it.paraModelo() },
        projeto = if (projetoId != null && projetoTitulo != null) ProjetoResumo(projetoId, projetoTitulo) else null,
        resultado = resultado,
        assinaturaNome = assinaturaNome,
        instrucoes = instrucoes,
        exigeAssinatura = exigeAssinatura,
        anexos = anexos.sortedBy { it.capturadoEm ?: "" }.map {
            AnexoOs(
                id = it.id,
                itemId = it.itemId,
                categoria = it.categoria,
                capturadoEm = Datas.lerInstante(it.capturadoEm),
                arquivoLocal = it.arquivoLocal,
                pendente = it.pendente,
                enviadoPorId = it.enviadoPorId,
            )
        },
        relatorio = relatorio,
        historico = historicoDe(historicoJson),
        // As pendentes por último: foram escritas depois de tudo o que já subiu.
        notas = notas.sortedWith(compareBy({ it.pendente }, { it.criadoEm ?: "" })).map {
            NotaOs(
                id = it.id,
                texto = it.texto,
                autorId = it.autorId,
                autor = it.autor,
                criadoEm = Datas.lerInstante(it.criadoEm),
                pendente = it.pendente,
            )
        },
    )
}
