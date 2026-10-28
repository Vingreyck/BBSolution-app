package br.com.selebi.app.nucleo.modelo

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime

/**
 * Situação da OS. Valor novo vindo do servidor vira [DESCONHECIDO] em vez de
 * fechar o app — a versão instalada no celular sempre fica para trás de alguém.
 */
enum class StatusOs(val api: String, val rotulo: String) {
    ABERTA("aberta", "A agendar"),
    AGENDADA("agendada", "Agendada"),
    /** Saiu para o endereço — o "Deslocamento" do IXC. */
    EM_DESLOCAMENTO("em_deslocamento", "A caminho"),
    /** "Em atendimento", como no site: o técnico está no local. */
    EM_ANDAMENTO("em_andamento", "Em atendimento"),
    AGUARDANDO_PECA("aguardando_peca", "Aguardando peça"),
    CONCLUIDA("concluida", "Concluída"),
    CANCELADA("cancelada", "Cancelada"),
    DESCONHECIDO("", "—");

    val encerrada: Boolean get() = this == CONCLUIDA || this == CANCELADA

    /** A caminho ou atendendo: o técnico já está com esta OS. */
    val emCampo: Boolean get() = this == EM_DESLOCAMENTO || this == EM_ANDAMENTO

    companion object {
        fun daApi(valor: String): StatusOs = entries.firstOrNull { it.api == valor } ?: DESCONHECIDO
    }
}

enum class TipoOs(val api: String, val rotulo: String) {
    VISTORIA("vistoria", "Vistoria"),
    INSTALACAO("instalacao", "Instalação"),
    PREVENTIVA("preventiva", "Preventiva"),
    CORRETIVA("corretiva", "Corretiva"),
    LIMPEZA("limpeza", "Limpeza"),
    GARANTIA("garantia", "Garantia"),
    OUTRO("", "Serviço");

    companion object {
        fun daApi(valor: String): TipoOs = entries.firstOrNull { it.api == valor } ?: OUTRO
    }
}

/** `peso` ordena: urgente primeiro. */
enum class Prioridade(val api: String, val rotulo: String, val peso: Int) {
    URGENTE("urgente", "Urgente", 0),
    ALTA("alta", "Alta", 1),
    NORMAL("normal", "Normal", 2),
    BAIXA("baixa", "Baixa", 3);

    companion object {
        fun daApi(valor: String): Prioridade = entries.firstOrNull { it.api == valor } ?: NORMAL
    }
}

/** Como o técnico responde a cada item — o mesmo vocabulário do servidor. */
enum class TipoResposta(val api: String) {
    CHECK("check"),
    SIM_NAO("sim_nao"),
    NUMERO("numero"),
    TEXTO("texto"),
    ESCOLHA("escolha"),
    MULTIPLA("multipla"),
    FOTO("foto"),
    SERIAL("serial");

    companion object {
        fun daApi(valor: String): TipoResposta = entries.firstOrNull { it.api == valor } ?: CHECK
    }
}

/**
 * Quando a observação do item é obrigatória (`exigeObservacao` do servidor):
 * `SEM_FOTO` deixa a explicação valer no lugar da foto que não deu para tirar;
 * `SEMPRE` pede a observação junto com a foto.
 */
enum class ExigeObservacao(val api: String) {
    NAO("nao"),
    SEM_FOTO("sem_foto"),
    SEMPRE("sempre");

    companion object {
        fun daApi(valor: String?): ExigeObservacao = entries.firstOrNull { it.api == valor } ?: NAO
    }
}

/**
 * A resposta de um item: marcado (check, sim/não), número, texto (e a opção
 * escolhida), ou lista (várias opções, seriais). Foto não é resposta — é anexo.
 */
sealed interface Resposta {
    data class Marcado(val sim: Boolean) : Resposta
    data class Numero(val valor: Double) : Resposta
    data class Texto(val valor: String) : Resposta
    data class Lista(val valores: List<String>) : Resposta
}

data class ClienteResumo(
    val id: String,
    val nome: String,
    /** Só dígitos, com DDD. */
    val telefone: String?,
    val endereco: String?,
    val cidade: String?,
    val uf: String?,
)

data class UsinaResumo(
    val id: String,
    val nome: String,
    val potenciaKwp: Double?,
    val latitude: Double?,
    val longitude: Double?,
)

data class Pessoa(val id: String, val nome: String)

data class ProjetoResumo(val id: String, val titulo: String)

data class ItemChecklist(
    val id: String,
    val ordem: Int,
    val descricao: String,
    val obrigatorio: Boolean,
    /** Cumprido: resposta dada e fotos mínimas. O servidor decide; o app antecipa. */
    val concluido: Boolean,
    val observacao: String?,
    val secao: String? = null,
    val ajuda: String? = null,
    val tipoResposta: TipoResposta = TipoResposta.CHECK,
    val opcoes: List<String> = emptyList(),
    val unidade: String? = null,
    val fotosMinimas: Int = 0,
    /** Só vale foto tirada na hora — três vistorias usaram print do Street View. */
    val apenasCamera: Boolean = false,
    val exigeObservacao: ExigeObservacao = ExigeObservacao.NAO,
    /** Pode ficar para uma visita de volta (sombra, espaço útil, caminho do cabo). */
    val permiteAgendar: Boolean = false,
    /** A foto pode ser um vídeo curto (onde vai ficar o inversor). */
    val aceitaVideo: Boolean = false,
    /** O técnico deixou para a visita de volta. Conta como cumprido. */
    val agendar: Boolean = false,
    val valor: Resposta? = null,
    val respondidoEm: Instant? = null,
)

/**
 * Uma foto ou a assinatura. `pendente`: tirada no celular e ainda não enviada;
 * `arquivoLocal`: o arquivo no aparelho, quando existe (as fotos tiradas aqui).
 */
data class AnexoOs(
    val id: String,
    val itemId: String?,
    val categoria: String,
    val capturadoEm: Instant?,
    val arquivoLocal: String? = null,
    val pendente: Boolean = false,
    /** Quem enviou. Foto já enviada só sai por quem a enviou (ou pela gestão). */
    val enviadoPorId: String? = null,
) {
    val ehVideo: Boolean get() = categoria == "video"
}

/** Uma linha do histórico, já escrita pelo servidor com as palavras do site. */
data class EventoOs(
    val id: String,
    val tipo: String,
    val titulo: String,
    val detalhe: String?,
    /** Nome de quem fez; "Selebi" para o que o sistema fez sozinho. */
    val quem: String?,
    /** `web`, `app` ou `sistema`. */
    val origem: String,
    val ocorridoEm: Instant?,
)

/**
 * Uma nota da OS — os comentários da tela da OS no site. `pendente`: escrita
 * aqui e ainda não enviada.
 */
data class NotaOs(
    val id: String,
    val texto: String,
    val autorId: String?,
    val autor: String?,
    val criadoEm: Instant?,
    val pendente: Boolean = false,
)

data class Ordem(
    val id: String,
    val numero: Int,
    val tipo: TipoOs,
    val status: StatusOs,
    val prioridade: Prioridade,
    val descricao: String,
    val laudo: String?,
    /** Hora de relógio em Sergipe ("dia 26 às 9h"), não um instante. */
    val agendadaPara: LocalDateTime?,
    val prazoSla: LocalDateTime?,
    val iniciadaEm: Instant?,
    val concluidaEm: Instant?,
    val criadoEm: Instant,
    val cliente: ClienteResumo,
    val usina: UsinaResumo?,
    val responsavel: Pessoa?,
    val checklist: List<ItemChecklist>,
    val projeto: ProjetoResumo? = null,
    /** `resolvido` ou `nao_resolvido`. */
    val resultado: String? = null,
    val assinaturaNome: String? = null,
    /** O que o modelo do tipo manda o técnico saber antes de começar. */
    val instrucoes: String? = null,
    val exigeAssinatura: Boolean = false,
    val anexos: List<AnexoOs> = emptyList(),
    /** Caminho do relatório público (`/r/…`) — existe depois de concluída. */
    val relatorio: String? = null,
    /** Do mais novo para o mais velho. */
    val historico: List<EventoOs> = emptyList(),
    /** Em ordem de escrita. */
    val notas: List<NotaOs> = emptyList(),
) {
    val aberta: Boolean get() = !status.encerrada

    val checklistFeitos: Int get() = checklist.count { it.concluido }

    /** Obrigatórios que ainda travam a conclusão. */
    val obrigatoriosPendentes: List<ItemChecklist> get() = checklist.filter { it.obrigatorio && !it.concluido }

    /** As fotos do item — e os vídeos, que contam como foto onde o item aceita. */
    fun fotosDo(itemId: String): List<AnexoOs> =
        anexos.filter { it.itemId == itemId && (it.categoria == "foto" || it.categoria == "video") }

    val temAssinatura: Boolean get() = anexos.any { it.categoria == "assinatura" }

    val assinatura: AnexoOs? get() = anexos.lastOrNull { it.categoria == "assinatura" }

    /** #0042 — quatro dígitos, que é como o cliente fala da OS ao telefone. */
    val numeroFormatado: String get() = "#" + numero.toString().padStart(4, '0')

    /**
     * "Itabaiana - SE", que não se parte no fim da linha: espaços inquebráveis
     * e um "word joiner" depois do hífen — sem ele, o Android quebra ali.
     */
    val cidadeUf: String?
        get() = listOfNotNull(cliente.cidade, cliente.uf).joinToString("\u00A0-\u2060\u00A0").ifBlank { null }

    /** Como no envelope: rua, número e bairro numa linha; a cidade na outra. */
    val enderecoCompleto: String?
        get() = listOfNotNull(cliente.endereco, cidadeUf).joinToString("\n").ifBlank { null }

    /**
     * Passou do prazo, ou era para ter sido feita num dia que já foi.
     *
     * Calculado no celular, com a hora do celular, e não vindo pronto do
     * servidor: uma OS vira atrasada à meia-noite mesmo que o técnico esteja
     * sem sinal desde a manhã.
     */
    fun atrasada(agora: LocalDateTime): Boolean =
        aberta && (
            prazoSla?.let { it < agora } == true ||
                agendadaPara?.let { it.date < agora.date } == true
            )
}
