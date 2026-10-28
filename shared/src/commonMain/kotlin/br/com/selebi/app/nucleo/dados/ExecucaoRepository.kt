package br.com.selebi.app.nucleo.dados

import androidx.compose.ui.graphics.ImageBitmap
import br.com.selebi.app.nucleo.arquivos.ArquivosOs
import br.com.selebi.app.nucleo.banco.AvisoDao
import br.com.selebi.app.nucleo.banco.PendenteEntidade
import br.com.selebi.app.nucleo.modelo.AcaoOs
import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.modelo.AvisoOs
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.EsperaEnvio
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.ModoRastreio
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.RegrasOs
import br.com.selebi.app.nucleo.modelo.Resposta
import br.com.selebi.app.nucleo.modelo.TipoEnvio
import br.com.selebi.app.nucleo.rastreio.EstadoRastreio
import br.com.selebi.app.nucleo.rastreio.Rastreador
import br.com.selebi.app.nucleo.rede.AcaoPedido
import br.com.selebi.app.nucleo.rede.AgendamentoPedido
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.NotaPedido
import br.com.selebi.app.nucleo.rede.RespostaPedido
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import br.com.selebi.app.nucleo.rede.paraJson
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant
import br.com.selebi.app.nucleo.plataforma.OrigemFoto
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Executar a OS pelo celular: sair, chegar, responder, fotografar, assinar,
 * anotar, concluir.
 *
 * Tudo passa pela [FilaEnvio] — com sinal sobe em segundos, sem sinal espera.
 * Antes de entrar na fila, a mesma conferência do servidor: o que ele vai
 * recusar é recusado aqui, na frente do cliente, e não horas depois.
 *
 * Cada função devolve a frase do problema, ou nulo quando deu certo.
 */
class ExecucaoRepository(
    private val fila: FilaEnvio,
    private val copia: CopiaLocal,
    private val sessao: SessaoRepository,
    private val arquivos: ArquivosOs,
    private val localizacao: Localizacao,
    private val avisos: AvisoDao,
    private val api: SelebiApi,
    private val executor: ExecutorApi,
    private val json: Json,
    private val rastreador: Rastreador,
) {
    /** Por que a fila está esperando (sem sinal, erro do servidor), ou nulo. */
    fun espera(): Flow<EsperaEnvio?> = fila.espera

    fun envios(ordemId: String): Flow<List<Envio>> = fila.observarDa(ordemId).map { lista -> lista.map(::envio) }

    fun todosEnvios(): Flow<List<Envio>> = fila.observarTodos().map { lista -> lista.map(::envio) }

    fun avisos(ordemId: String): Flow<List<AvisoOs>> =
        avisos.observarDa(ordemId).map { lista -> lista.map { AvisoOs(it.id, it.ordemId, it.texto) } }

    suspend fun dispensarAviso(id: Long) = avisos.apagar(id)

    fun temLocalizacao(): Boolean = localizacao.temPermissao()

    // --- ações ---------------------------------------------------------------

    suspend fun agir(
        ordem: Ordem,
        acao: AcaoOs,
        motivo: String? = null,
        resolvido: Boolean = true,
        laudo: String? = null,
        assinaturaNome: String? = null,
        /** Quem volta para o que ficou para depois ("agendar"). Só vale se há item marcado. */
        agendamento: AgendamentoPedido? = null,
    ): String? {
        val usuario = sessao.usuario ?: return "Sua sessão acabou. Entre de novo."
        if (!RegrasOs.permitida(acao, ordem.status)) {
            return "Esta OS está \"${ordem.status.rotulo}\" — atualize a lista e confira."
        }
        val motivoLimpo = motivo?.trim()?.take(RegrasOs.MOTIVO_MAXIMO)
        if ((acao == AcaoOs.PAUSAR || acao == AcaoOs.NAO_ATENDIDA) && motivoLimpo.isNullOrEmpty()) {
            return "Diga o motivo."
        }
        val laudoLimpo = laudo?.trim()?.take(RegrasOs.LAUDO_MAXIMO)?.ifEmpty { null }
        if (acao == AcaoOs.CONCLUIR) {
            RegrasOs.impedimentos(ordem, resolvido, laudoLimpo, ordem.temAssinatura).firstOrNull()?.let { return it }
        }

        // O ponto de onde a ação foi feita: é o "no mapa" do histórico no site.
        // Cinco segundos no máximo — depois disso vale a última posição conhecida.
        val ponto = localizacao.atual(limiteMs = 5_000)
        val pedido = AcaoPedido(
            idCliente = novoId(),
            acao = acao.api,
            ocorridoEm = Datas.agoraMesmo().toString(),
            latitude = ponto?.latitude,
            longitude = ponto?.longitude,
            precisao = ponto?.precisao,
            motivo = motivoLimpo.takeIf { acao == AcaoOs.PAUSAR || acao == AcaoOs.NAO_ATENDIDA },
            resultado = if (acao == AcaoOs.CONCLUIR) (if (resolvido) "resolvido" else "nao_resolvido") else null,
            laudo = laudoLimpo.takeIf { acao == AcaoOs.CONCLUIR },
            assinaturaNome = assinaturaNome?.trim()?.take(RegrasOs.NOME_MAXIMO)?.ifEmpty { null }
                .takeIf { acao == AcaoOs.CONCLUIR },
            agendamento = agendamento.takeIf { acao == AcaoOs.CONCLUIR && ordem.checklist.any { it.agendar } },
        )
        fila.enfileirar(
            PendenteEntidade(
                idCliente = pedido.idCliente,
                ordemId = ordem.id,
                usuarioId = usuario.id,
                tipo = TipoPendente.ACAO,
                corpoJson = json.encodeToString(AcaoPedido.serializer(), pedido),
                criadoEm = Datas.agoraMs(),
            ),
        )
        rastrear(ordem, acao, usuario.id, ponto)
        return null
    }

    /**
     * O rastreamento segue a OS, como no SeeNet: liga no "Estou a caminho",
     * vira modo econômico no "Cheguei" (e no "Retomar"), e desliga ao
     * concluir, pausar ou não atender.
     */
    private fun rastrear(ordem: Ordem, acao: AcaoOs, usuarioId: String, ponto: Ponto?) {
        fun estado(modo: ModoRastreio) = EstadoRastreio(usuarioId, ordem.id, ordem.numero, ordem.cliente.nome, modo)
        when (acao) {
            AcaoOs.DESLOCAMENTO -> rastreador.iniciar(estado(ModoRastreio.DESLOCAMENTO), ponto)
            AcaoOs.INICIAR, AcaoOs.RETOMAR -> rastreador.iniciar(estado(ModoRastreio.ECO), ponto)
            AcaoOs.PAUSAR, AcaoOs.NAO_ATENDIDA, AcaoOs.CONCLUIR -> rastreador.parar(ordem.id)
        }
    }

    // --- checklist -----------------------------------------------------------

    /**
     * A resposta do item. `observacao` nula mantém a que o item tem; vazia apaga.
     * `agendar` nulo é "não": responder o item de verdade tira ele da visita de volta.
     *
     * O pedido leva o estado inteiro (resposta, observação, agendar): uma resposta
     * nova toma o lugar da que ainda não subiu, e o que só estava na anterior não
     * pode se perder.
     */
    suspend fun responder(
        ordem: Ordem,
        item: ItemChecklist,
        valor: Resposta?,
        observacao: String? = null,
        agendar: Boolean? = null,
    ): String? {
        val usuario = sessao.usuario ?: return "Sua sessão acabou. Entre de novo."
        if (ordem.status.encerrada) return "A OS já foi encerrada; o checklist não muda mais."
        val normalizado = when (val n = RegrasOs.normalizar(item, valor)) {
            is RegrasOs.Normalizado.Recusa -> return n.mensagem
            is RegrasOs.Normalizado.Ok -> n.valor
        }
        val deixar = agendar == true
        if (deixar && !item.permiteAgendar) return "Este item não pode ficar para outra visita."
        val id = novoId()
        val pedido = RespostaPedido(
            valor = normalizado.paraJson(),
            observacao = (observacao ?: item.observacao)?.trim()?.take(RegrasOs.TEXTO_MAXIMO),
            respondidoEm = Datas.agoraMesmo().toString(),
            agendar = deixar,
        )
        fila.enfileirar(
            PendenteEntidade(
                idCliente = id,
                ordemId = ordem.id,
                usuarioId = usuario.id,
                tipo = TipoPendente.RESPOSTA,
                corpoJson = json.encodeToString(RespostaPedido.serializer(), pedido),
                itemId = item.id,
                criadoEm = Datas.agoraMs(),
            ),
        )
        return null
    }

    // --- fotos e assinatura --------------------------------------------------

    /**
     * Guarda a foto (reduzida e de pé) e põe na fila. Da câmera, com o GPS de
     * agora; da galeria, com o lugar e a hora que o EXIF da foto disser.
     */
    suspend fun anexarFoto(ordem: Ordem, itemId: String?, origem: OrigemFoto, daCamera: Boolean): String? {
        val usuario = sessao.usuario ?: return "Sua sessão acabou. Entre de novo."
        if (ordem.status.encerrada) return "A OS já foi encerrada; não recebe mais foto."
        val id = novoId()
        val foto = arquivos.prepararFoto(origem, ordem.id, id) ?: return "Não deu para abrir essa foto. Tente de novo."
        val ponto = if (daCamera) localizacao.atual(limiteMs = 4_000) else null
        val dados = AnexoNaFila(
            categoria = "foto",
            itemId = itemId,
            capturadoEm = (if (daCamera) Datas.agoraMesmo() else foto.capturadoEm)?.toString(),
            latitude = ponto?.latitude ?: foto.latitude,
            longitude = ponto?.longitude ?: foto.longitude,
        )
        enfileirarAnexo(id, ordem.id, usuario.id, itemId, dados, foto.arquivo)
        return null
    }

    /**
     * Guarda o vídeo gravado (sem reduzir: a câmera já grava curto) e põe na fila.
     * Só nos itens que aceitam vídeo.
     */
    suspend fun anexarVideo(ordem: Ordem, itemId: String, origem: OrigemFoto): String? {
        val usuario = sessao.usuario ?: return "Sua sessão acabou. Entre de novo."
        if (ordem.status.encerrada) return "A OS já foi encerrada; não recebe mais vídeo."
        if (ordem.checklist.firstOrNull { it.id == itemId }?.aceitaVideo != true) return "Este item não aceita vídeo — tire uma foto."
        val id = novoId()
        val arquivo = arquivos.guardarVideo(origem, ordem.id, id) ?: return "Não deu para guardar o vídeo. Grave de novo."
        val ponto = localizacao.atual(limiteMs = 4_000)
        val dados = AnexoNaFila(
            categoria = "video",
            itemId = itemId,
            capturadoEm = Datas.agoraMesmo().toString(),
            latitude = ponto?.latitude,
            longitude = ponto?.longitude,
        )
        enfileirarAnexo(id, ordem.id, usuario.id, itemId, dados, arquivo)
        return null
    }

    /** A assinatura nova toma o lugar da que ainda não subiu. */
    suspend fun assinar(ordem: Ordem, desenho: ImageBitmap): String? {
        val usuario = sessao.usuario ?: return "Sua sessão acabou. Entre de novo."
        if (ordem.status.encerrada) return "A OS já foi encerrada."
        ordem.anexos.filter { it.categoria == "assinatura" && it.pendente }.forEach { fila.descartar(it.id) }
        val id = novoId()
        val arquivo = arquivos.salvarAssinatura(desenho, ordem.id, id)
        val dados = AnexoNaFila(categoria = "assinatura", capturadoEm = Datas.agoraMesmo().toString())
        enfileirarAnexo(id, ordem.id, usuario.id, null, dados, arquivo)
        return null
    }

    /**
     * Foto que ainda não subiu sai na hora. A que já subiu precisa do
     * servidor — e só quem enviou pode tirar.
     */
    suspend fun removerFoto(ordem: Ordem, anexo: AnexoOs): String? {
        if (anexo.pendente) {
            fila.descartar(anexo.id)
            return null
        }
        return when (val r = executor.executar { api.removerAnexo(ordem.id, anexo.id) }) {
            is Resultado.Ok -> {
                r.valor.ordem?.let { copia.gravarUma(it) } ?: copia.esquecer(ordem.id)
                arquivos.apagar(anexo.arquivoLocal)
                null
            }
            is Resultado.Falha -> when (r.erro) {
                ErroApp.SemConexao, ErroApp.TempoEsgotado -> "Sem sinal: foto que já subiu só sai com conexão."
                else -> r.erro.mensagem
            }
        }
    }

    // --- notas e fila --------------------------------------------------------

    suspend fun anotar(ordem: Ordem, texto: String): String? {
        val usuario = sessao.usuario ?: return "Sua sessão acabou. Entre de novo."
        val limpo = texto.trim()
        if (limpo.isEmpty()) return "Escreva a nota."
        if (limpo.length > RegrasOs.NOTA_MAXIMO) return "A nota passou de ${RegrasOs.NOTA_MAXIMO} caracteres."
        val pedido = NotaPedido(idCliente = novoId(), texto = limpo, criadoEm = Datas.agoraMesmo().toString())
        fila.enfileirar(
            PendenteEntidade(
                idCliente = pedido.idCliente,
                ordemId = ordem.id,
                usuarioId = usuario.id,
                tipo = TipoPendente.NOTA,
                corpoJson = json.encodeToString(NotaPedido.serializer(), pedido),
                criadoEm = Datas.agoraMs(),
            ),
        )
        return null
    }

    /** O técnico viu a recusa e tira da fila. */
    suspend fun descartar(envioId: String) = fila.descartar(envioId)

    /** "Tentar agora": põe a fila para andar sem esperar o próximo sinal. */
    fun enviarAgora() = fila.agendar()

    // -------------------------------------------------------------------------

    private suspend fun enfileirarAnexo(
        id: String,
        ordemId: String,
        usuarioId: String,
        itemId: String?,
        dados: AnexoNaFila,
        arquivo: String,
    ) {
        fila.enfileirar(
            PendenteEntidade(
                idCliente = id,
                ordemId = ordemId,
                usuarioId = usuarioId,
                tipo = TipoPendente.ANEXO,
                corpoJson = json.encodeToString(AnexoNaFila.serializer(), dados),
                itemId = itemId,
                arquivo = arquivo,
                criadoEm = Datas.agoraMs(),
            ),
        )
    }

    private fun envio(p: PendenteEntidade): Envio {
        val tipo = when (p.tipo) {
            TipoPendente.ACAO -> TipoEnvio.ACAO
            TipoPendente.RESPOSTA -> TipoEnvio.RESPOSTA
            TipoPendente.NOTA -> TipoEnvio.NOTA
            else -> when {
                p.itemId == null && p.arquivo?.endsWith(".png") == true -> TipoEnvio.ASSINATURA
                p.arquivo?.let { it.endsWith(".mp4") || it.endsWith(".mov") } == true -> TipoEnvio.VIDEO
                else -> TipoEnvio.FOTO
            }
        }
        val acao = if (tipo == TipoEnvio.ACAO) {
            runCatching { json.decodeFromString(AcaoPedido.serializer(), p.corpoJson).acao }.getOrNull()
                ?.let { nome -> AcaoOs.entries.firstOrNull { it.api == nome } }
        } else {
            null
        }
        return Envio(p.idCliente, p.ordemId, tipo, p.itemId, acao, Instant.fromEpochMilliseconds(p.criadoEm), p.erro)
    }

    /** O id do celular: vai para o servidor e torna o reenvio seguro. */
    @OptIn(ExperimentalUuidApi::class)
    private fun novoId(): String = Uuid.random().toString()
}
