package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.util.Datas
import br.com.selebi.app.nucleo.banco.emTransacao
import br.com.selebi.app.nucleo.arquivos.ArquivosOs
import br.com.selebi.app.nucleo.banco.AvisoDao
import br.com.selebi.app.nucleo.banco.AvisoEntidade
import br.com.selebi.app.nucleo.banco.OrdemDao
import br.com.selebi.app.nucleo.banco.PendenteDao
import br.com.selebi.app.nucleo.banco.PendenteEntidade
import br.com.selebi.app.nucleo.banco.SelebiBanco
import br.com.selebi.app.nucleo.modelo.EsperaEnvio
import br.com.selebi.app.nucleo.modelo.MotivoEspera
import br.com.selebi.app.nucleo.rede.AcaoPedido
import br.com.selebi.app.nucleo.rede.ArquivoEnvio
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.NotaPedido
import br.com.selebi.app.nucleo.rede.OrdemDto
import br.com.selebi.app.nucleo.rede.RespostaPedido
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Quem põe a fila para andar. No Android é o WorkManager; no iPhone, o próprio app; nos testes, nada. */
fun interface AgendadorEnvio {
    fun agendar()
}

enum class ResultadoEnvio {
    /** A fila desta pessoa esvaziou (ou só sobrou o que o servidor recusou). */
    TUDO_ENVIADO,

    /** Sem sinal, ou o servidor fora do ar: tenta de novo daqui a pouco. */
    TENTAR_DE_NOVO,

    /** A sessão acabou: a fila espera a pessoa entrar de novo. */
    SEM_SESSAO,
}

/**
 * A fila do que o técnico fez e ainda não chegou ao servidor.
 *
 * Cada ação, resposta, foto e nota entra aqui primeiro e é aplicada na cópia
 * local na hora — o técnico no telhado vê o item ficar verde sem sinal. Depois
 * a fila sobe, **na ordem em que as coisas aconteceram**: a foto do item antes
 * da conclusão, o "iniciar" antes do "pausar". Por isso ela para no primeiro
 * envio que falha por falta de sinal, em vez de pular e mandar o resto fora de
 * ordem.
 *
 * Recusa do servidor (a OS foi cancelada pelo escritório, o serial não serve)
 * é outra coisa: não adianta tentar de novo. O item fica marcado com o motivo,
 * a cópia local volta a ser a do servidor, e o técnico vê o aviso e descarta.
 *
 * Reenviar é seguro: cada linha leva o `idCliente` que o servidor usa para
 * reconhecer o que já recebeu.
 */
class FilaEnvio(
    private val banco: SelebiBanco,
    private val pendentes: PendenteDao,
    private val dao: OrdemDao,
    private val avisos: AvisoDao,
    private val copia: CopiaLocal,
    private val api: SelebiApi,
    private val executor: ExecutorApi,
    private val arquivos: ArquivosOs,
    private val json: Json,
    private val agendador: AgendadorEnvio,
) {
    private val trava = Mutex()

    private val _espera = MutableStateFlow<EsperaEnvio?>(null)

    /**
     * Por que a fila está parada, se está: sem sinal ou erro do servidor. Nulo
     * quando tudo sobe. Só na memória — o app recém-aberto não sabe até tentar.
     */
    val espera: StateFlow<EsperaEnvio?> = _espera.asStateFlow()

    fun observarTodos(): Flow<List<PendenteEntidade>> = pendentes.observarTodos()

    fun observarDa(ordemId: String): Flow<List<PendenteEntidade>> = pendentes.observarDa(ordemId)

    fun contar(): Flow<Int> = pendentes.contar()

    fun agendar() = agendador.agendar()

    /** Entra na fila e já vale na tela. */
    suspend fun enfileirar(p: PendenteEntidade) {
        banco.emTransacao {
            // Resposta nova do mesmo item toma o lugar da que ainda não subiu:
            // quem corrige "40" para "50" sem sinal manda uma resposta, não duas.
            if (p.tipo == TipoPendente.RESPOSTA && p.itemId != null) {
                pendentes.respostasNaoEnviadas(p.itemId).forEach { pendentes.apagar(it.idCliente) }
            }
            pendentes.inserir(p)
            copia.aplicar(p)
        }
        agendador.agendar()
    }

    /**
     * Tira da fila: a foto que o técnico apagou antes de subir, ou o que o
     * servidor recusou e ele já viu.
     */
    suspend fun descartar(idCliente: String) {
        val p = pendentes.buscar(idCliente) ?: return
        banco.emTransacao {
            pendentes.apagar(idCliente)
            when (p.tipo) {
                TipoPendente.ANEXO -> {
                    dao.apagarAnexo(idCliente)
                    p.itemId?.let { copia.recalcular(it) }
                }
                TipoPendente.NOTA -> dao.apagarNota(idCliente)
            }
        }
        arquivos.apagar(p.arquivo)
    }

    /** Outra pessoa entrou neste celular: o que era da anterior não sobe em nome de ninguém. */
    suspend fun descartarDeOutros(usuarioId: String) {
        pendentes.deOutros(usuarioId).forEach { descartar(it.idCliente) }
    }

    /** Sobe a fila desta pessoa, do mais velho para o mais novo. */
    suspend fun enviar(usuarioId: String): ResultadoEnvio = trava.withLock {
        var p = pendentes.proximo(usuarioId)
        while (p != null) {
            when (val d = enviarUm(p)) {
                is Desfecho.Aceito -> aceito(p, d)
                is Desfecho.Recusado -> recusado(p, d.mensagem)
                is Desfecho.Depois -> {
                    pendentes.contarTentativa(p.idCliente)
                    _espera.value = d.espera
                    return@withLock ResultadoEnvio.TENTAR_DE_NOVO
                }
                Desfecho.SemSessao -> {
                    _espera.value = null
                    return@withLock ResultadoEnvio.SEM_SESSAO
                }
            }
            p = pendentes.proximo(usuarioId)
        }
        _espera.value = null
        ResultadoEnvio.TUDO_ENVIADO
    }

    // -------------------------------------------------------------------------

    private sealed interface Desfecho {
        data class Aceito(val ordem: OrdemDto?, val avisos: List<String>, val anexoId: String? = null) : Desfecho
        data class Recusado(val mensagem: String) : Desfecho
        data class Depois(val espera: EsperaEnvio) : Desfecho
        data object SemSessao : Desfecho
    }

    private suspend fun enviarUm(p: PendenteEntidade): Desfecho = when (p.tipo) {
        TipoPendente.ACAO -> {
            val pedido = json.decodeFromString(AcaoPedido.serializer(), p.corpoJson)
            desfecho(p, executor.executar { api.acao(p.ordemId, pedido) }) { Desfecho.Aceito(it.ordem, it.avisos) }
        }
        TipoPendente.RESPOSTA -> {
            val itemId = p.itemId
            if (itemId == null) {
                Desfecho.Recusado("Resposta sem item.")
            } else {
                val pedido = json.decodeFromString(RespostaPedido.serializer(), p.corpoJson)
                desfecho(p, executor.executar { api.responder(p.ordemId, itemId, pedido) }) {
                    Desfecho.Aceito(it.ordem, it.avisos)
                }
            }
        }
        TipoPendente.ANEXO -> enviarAnexo(p)
        TipoPendente.NOTA -> {
            val pedido = json.decodeFromString(NotaPedido.serializer(), p.corpoJson)
            desfecho(p, executor.executar { api.anotar(p.ordemId, pedido) }) { Desfecho.Aceito(it.ordem, emptyList()) }
        }
        else -> Desfecho.Recusado("Esta versão do app não sabe enviar isto.")
    }

    private suspend fun enviarAnexo(p: PendenteEntidade): Desfecho {
        val caminho = p.arquivo?.takeIf { arquivos.temConteudo(it) }
            ?: return Desfecho.Recusado("A foto (ou o vídeo) sumiu do celular antes de subir. Faça de novo.")
        val dados = json.decodeFromString(AnexoNaFila.serializer(), p.corpoJson)
        val nome = caminho.substringAfterLast('/')
        val tipo = when (nome.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "image/jpeg"
        }
        val resultado = executor.executar {
            api.anexar(
                id = p.ordemId,
                arquivo = ArquivoEnvio(caminho, nome, tipo),
                idCliente = p.idCliente,
                categoria = dados.categoria,
                itemId = dados.itemId,
                capturadoEm = dados.capturadoEm,
                latitude = dados.latitude?.toString(),
                longitude = dados.longitude?.toString(),
            )
        }
        return desfecho(p, resultado) { Desfecho.Aceito(it.ordem, emptyList(), anexoId = it.anexo.id) }
    }

    private fun <T> desfecho(p: PendenteEntidade, r: Resultado<T>, ok: (T) -> Desfecho): Desfecho = when (r) {
        is Resultado.Ok -> ok(r.valor)
        is Resultado.Falha -> when (val e = r.erro) {
            ErroApp.SemConexao, ErroApp.TempoEsgotado -> Desfecho.Depois(EsperaEnvio(MotivoEspera.SEM_SINAL))
            is ErroApp.Servidor -> when {
                e.status == 401 -> Desfecho.SemSessao
                // App velho demais: NADA é recusado nem apagado. Fica esperando a pessoa
                // atualizar, e sobe com a versão nova.
                e.status == 426 -> Desfecho.Depois(EsperaEnvio(MotivoEspera.APP_DESATUALIZADO, 426))
                // Servidor fora do ar, apressado ou sem o Drive: é esperar.
                e.status == 408 || e.status == 425 || e.status == 429 || e.status >= 500 ->
                    Desfecho.Depois(EsperaEnvio(MotivoEspera.SERVIDOR, e.status))
                else -> Desfecho.Recusado(e.mensagem)
            }
            // Resposta que o app não entendeu: tenta algumas vezes antes de desistir.
            is ErroApp.Inesperado ->
                if (p.tentativas >= 4) Desfecho.Recusado(e.mensagem) else Desfecho.Depois(EsperaEnvio(MotivoEspera.SERVIDOR))
        }
    }

    private suspend fun aceito(p: PendenteEntidade, d: Desfecho.Aceito) {
        // A foto guardada passa a ter o nome do id do servidor — é por ele que a
        // cópia local a reencontra, sem baixar de novo.
        if (p.tipo == TipoPendente.ANEXO && d.anexoId != null) p.arquivo?.let { arquivos.renomear(it, d.anexoId) }
        banco.emTransacao {
            pendentes.apagar(p.idCliente)
            when (p.tipo) {
                TipoPendente.ANEXO -> dao.apagarAnexo(p.idCliente)
                TipoPendente.NOTA -> dao.apagarNota(p.idCliente)
            }
            if (d.ordem != null) copia.gravarUma(d.ordem) else copia.esquecer(p.ordemId)
        }
        if (d.avisos.isNotEmpty()) {
            val agora = Datas.agoraMs()
            avisos.inserir(d.avisos.map { AvisoEntidade(ordemId = p.ordemId, texto = it, criadoEm = agora) })
        }
    }

    private suspend fun recusado(p: PendenteEntidade, mensagem: String) {
        banco.emTransacao {
            pendentes.marcarErro(p.idCliente, mensagem)
            // A foto recusada não conta mais para o item; o arquivo fica até o técnico descartar.
            when (p.tipo) {
                TipoPendente.ANEXO -> {
                    dao.apagarAnexo(p.idCliente)
                    p.itemId?.let { copia.recalcular(it) }
                }
                TipoPendente.NOTA -> dao.apagarNota(p.idCliente)
            }
        }
        // A cópia local ainda mostra o que o servidor recusou: volta a ser a dele.
        when (val r = executor.executar { api.ordem(p.ordemId) }) {
            is Resultado.Ok -> copia.gravarUma(r.valor.ordem)
            is Resultado.Falha -> if ((r.erro as? ErroApp.Servidor)?.status == 404) copia.esquecer(p.ordemId)
        }
    }
}
