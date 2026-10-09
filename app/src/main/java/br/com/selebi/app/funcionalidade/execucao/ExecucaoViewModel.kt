package br.com.selebi.app.funcionalidade.execucao

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.com.selebi.app.navegacao.RotaExecucao
import br.com.selebi.app.nucleo.arquivos.ArquivosOs
import br.com.selebi.app.nucleo.arquivos.Miniaturas
import br.com.selebi.app.nucleo.dados.Conectividade
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.ExecucaoRepository
import br.com.selebi.app.nucleo.dados.OrdensRepository
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.modelo.AcaoOs
import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.modelo.AvisoOs
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Resposta
import br.com.selebi.app.nucleo.modelo.Usuario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** O que a Conclusão pede, guardado enquanto o técnico preenche. */
data class FormConclusao(
    val resolvido: Boolean = true,
    val laudo: String = "",
    /** Nome de quem assinou — nem sempre o titular. */
    val assinante: String = "",
)

data class ExecucaoEstado(
    val carregado: Boolean = false,
    val ordem: Ordem? = null,
    /** Índice em [passos]. */
    val passo: Int = 0,
    val envios: List<Envio> = emptyList(),
    val avisos: List<AvisoOs> = emptyList(),
    val online: Boolean = true,
    val usuario: Usuario? = null,
    /** Esperando o GPS ou a fila — os botões de ação ficam presos. */
    val ocupado: Boolean = false,
    /** O item cuja foto está sendo preparada (reduzida, endireitada). "" = foto avulsa. */
    val preparandoFoto: String? = null,
    /** Concluída neste wizard: mostra a tela de fim. */
    val concluidaAqui: Boolean = false,
    val conclusao: FormConclusao = FormConclusao(),
) {
    val passos: List<Passo> get() = ordem?.let(Roteiro::passos).orEmpty()
    val passoAtual: Passo? get() = passos.getOrNull(passo)
}

sealed interface EventoExecucao {
    /** Uma frase no rodapé. `avisarCliente`: oferece mandar o "estou a caminho". */
    data class Mensagem(val texto: String, val avisarCliente: Boolean = false) : EventoExecucao

    /** A OS saiu das mãos do técnico (não atendida): volta para o detalhe. */
    data object Fechar : EventoExecucao
}

@HiltViewModel
class ExecucaoViewModel @Inject constructor(
    private val salvo: SavedStateHandle,
    private val ordens: OrdensRepository,
    private val execucao: ExecucaoRepository,
    private val arquivos: ArquivosOs,
    sessao: SessaoRepository,
    conectividade: Conectividade,
    val miniaturas: Miniaturas,
) : ViewModel() {

    private val rota = salvo.toRoute<RotaExecucao>()
    val ordemId: String = rota.id

    private val ocupado = MutableStateFlow(false)
    private val preparandoFoto = MutableStateFlow<String?>(null)
    private val conclusao = MutableStateFlow(
        FormConclusao(
            resolvido = salvo[CHAVE_RESOLVIDO] ?: true,
            laudo = salvo[CHAVE_LAUDO] ?: "",
            assinante = salvo[CHAVE_ASSINANTE] ?: "",
        ),
    )
    private val eventos = Channel<EventoExecucao>(Channel.BUFFERED)
    val mensagens: Flow<EventoExecucao> = eventos.receiveAsFlow()

    private val usuario = sessao.estado.map { (it as? EstadoSessao.Logado)?.usuario }

    private data class Local(val ocupado: Boolean, val foto: String?, val concluida: Boolean, val form: FormConclusao)

    val estado: StateFlow<ExecucaoEstado> = combine(
        ordens.observar(ordemId),
        salvo.getStateFlow(CHAVE_PASSO, -1),
        combine(execucao.envios(ordemId), execucao.avisos(ordemId)) { e, a -> e to a },
        combine(conectividade.online, usuario) { o, u -> o to u },
        combine(ocupado, preparandoFoto, salvo.getStateFlow(CHAVE_CONCLUIDA, false), conclusao) { o, f, c, form ->
            Local(o, f, c, form)
        },
    ) { ordem, passo, (envios, avisos), (online, u), local ->
        val total = ordem?.let { Roteiro.passos(it).size } ?: 1
        ExecucaoEstado(
            carregado = true,
            ordem = ordem,
            passo = if (passo < 0) 0 else passo.coerceAtMost(total - 1),
            envios = envios,
            avisos = avisos,
            online = online,
            usuario = u,
            ocupado = local.ocupado,
            preparandoFoto = local.foto,
            concluidaAqui = local.concluida,
            conclusao = local.form,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExecucaoEstado())

    init {
        viewModelScope.launch {
            // Abre no passo certo na primeira vez; depois de uma rotação ou de o
            // Android matar o app com a câmera aberta, volta onde estava.
            if (salvo.get<Int>(CHAVE_PASSO) == null) {
                val ordem = ordens.observar(ordemId).filterNotNull().first()
                salvo[CHAVE_PASSO] = Roteiro.passoInicial(ordem, rota.itemId)
            }
        }
        viewModelScope.launch { ordens.atualizar(ordemId) }
    }

    // --- navegação -----------------------------------------------------------

    fun irPara(passo: Int) {
        val total = estado.value.passos.size
        if (total == 0) return
        salvo[CHAVE_PASSO] = passo.coerceIn(0, total - 1)
    }

    fun proximo() = irPara(estado.value.passo + 1)

    fun anterior() = irPara(estado.value.passo - 1)

    /** "Próximo pendente": pula direto para o obrigatório que falta. */
    fun irParaPendente() {
        val s = estado.value
        val ordem = s.ordem ?: return
        Roteiro.proximoPendente(ordem, s.passo)?.let(::irPara) ?: irPara(s.passos.lastIndex)
    }

    // --- checklist -----------------------------------------------------------

    fun responder(item: ItemChecklist, valor: Resposta?) = executar { execucao.responder(it, item, valor) }

    /** Observação do item; vazia apaga. */
    fun observar(item: ItemChecklist, texto: String) = executar {
        val atual = it.checklist.firstOrNull { i -> i.id == item.id } ?: item
        if (texto.trim() == atual.observacao.orEmpty()) null else execucao.responder(it, atual, atual.valor, texto)
    }

    // --- fotos ---------------------------------------------------------------

    /** Onde a câmera vai gravar. Guardado no estado salvo: a câmera pode matar o app. */
    fun prepararCamera(itemId: String?): Uri {
        val (arquivo, uri) = arquivos.novaCaptura()
        salvo[CHAVE_CAPTURA] = arquivo.path
        salvo[CHAVE_CAPTURA_ITEM] = itemId.orEmpty()
        return uri
    }

    fun fotoTirada(ok: Boolean) {
        val caminho = salvo.get<String>(CHAVE_CAPTURA) ?: return
        val itemId = salvo.get<String>(CHAVE_CAPTURA_ITEM)?.ifEmpty { null }
        salvo.remove<String>(CHAVE_CAPTURA)
        salvo.remove<String>(CHAVE_CAPTURA_ITEM)
        if (!ok) {
            arquivos.apagar(caminho)
            return
        }
        prepararFoto(itemId) { ordem ->
            execucao.anexarFoto(ordem, itemId, Uri.fromFile(File(caminho)), daCamera = true).also { arquivos.apagar(caminho) }
        }
    }

    fun fotoDaGaleria(itemId: String?, uri: Uri) = prepararFoto(itemId) { execucao.anexarFoto(it, itemId, uri, daCamera = false) }

    fun removerFoto(anexo: AnexoOs) = executar { execucao.removerFoto(it, anexo) }

    private fun prepararFoto(itemId: String?, bloco: suspend (Ordem) -> String?) {
        val ordem = estado.value.ordem ?: return
        viewModelScope.launch {
            preparandoFoto.value = itemId.orEmpty()
            val erro = try {
                bloco(ordem)
            } finally {
                preparandoFoto.value = null
            }
            erro?.let { eventos.send(EventoExecucao.Mensagem(it)) }
        }
    }

    // --- conclusão -----------------------------------------------------------

    fun mudarConclusao(form: FormConclusao) {
        conclusao.value = form
        salvo[CHAVE_RESOLVIDO] = form.resolvido
        salvo[CHAVE_LAUDO] = form.laudo
        salvo[CHAVE_ASSINANTE] = form.assinante
    }

    fun assinar(desenho: Bitmap) = executar { execucao.assinar(it, desenho) }

    fun concluir() = executar(ocupando = true) {
        val form = conclusao.value
        execucao.agir(it, AcaoOs.CONCLUIR, resolvido = form.resolvido, laudo = form.laudo, assinaturaNome = form.assinante)
            .also { erro -> if (erro == null) salvo[CHAVE_CONCLUIDA] = true }
    }

    // --- ações da OS ---------------------------------------------------------

    fun agir(acao: AcaoOs, motivo: String? = null) = executar(ocupando = true) { ordem ->
        val erro = execucao.agir(ordem, acao, motivo)
        if (erro == null) {
            when (acao) {
                AcaoOs.DESLOCAMENTO -> eventos.send(
                    EventoExecucao.Mensagem("Saída registrada. Quer avisar o cliente?", avisarCliente = ordem.cliente.telefone != null),
                )
                AcaoOs.INICIAR, AcaoOs.RETOMAR -> {
                    // Com o atendimento em curso, vai direto para o que falta.
                    val atual = ordens.observar(ordemId).filterNotNull().first()
                    irPara(Roteiro.passoInicial(atual))
                }
                AcaoOs.PAUSAR -> eventos.send(EventoExecucao.Mensagem("Atendimento pausado. Retome quando voltar."))
                AcaoOs.NAO_ATENDIDA -> eventos.send(EventoExecucao.Fechar)
                AcaoOs.CONCLUIR -> Unit
            }
        }
        erro
    }

    // --- notas, fila e avisos ------------------------------------------------

    fun anotar(texto: String, aoConseguir: () -> Unit) = executar {
        execucao.anotar(it, texto).also { erro ->
            if (erro == null) {
                aoConseguir()
                eventos.send(EventoExecucao.Mensagem("Nota guardada. O escritório vê assim que subir."))
            }
        }
    }

    fun descartar(envioId: String) = viewModelScope.launch { execucao.descartar(envioId) }

    fun enviarAgora() = execucao.enviarAgora()

    fun dispensarAviso(id: Long) = viewModelScope.launch { execucao.dispensarAviso(id) }

    // -------------------------------------------------------------------------

    /** Roda contra a OS de agora; a frase de erro, se houver, vira mensagem no rodapé. */
    private fun executar(ocupando: Boolean = false, bloco: suspend (Ordem) -> String?) {
        val ordem = estado.value.ordem ?: return
        if (ocupando && ocupado.value) return
        viewModelScope.launch {
            if (ocupando) ocupado.value = true
            val erro = try {
                bloco(ordem)
            } finally {
                if (ocupando) ocupado.value = false
            }
            erro?.let { eventos.send(EventoExecucao.Mensagem(it)) }
        }
    }

    private companion object {
        const val CHAVE_PASSO = "passo"
        const val CHAVE_CONCLUIDA = "concluida"
        const val CHAVE_CAPTURA = "captura"
        const val CHAVE_CAPTURA_ITEM = "capturaItem"
        const val CHAVE_RESOLVIDO = "resolvido"
        const val CHAVE_LAUDO = "laudo"
        const val CHAVE_ASSINANTE = "assinante"
    }
}
