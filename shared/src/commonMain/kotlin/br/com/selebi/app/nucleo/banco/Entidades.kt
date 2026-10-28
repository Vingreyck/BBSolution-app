package br.com.selebi.app.nucleo.banco

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * A cópia das OS que mora no celular.
 *
 * É daqui — e não da rede — que as telas leem. A rede só atualiza esta cópia.
 * É o desenho que o Google recomenda para app que precisa funcionar sem sinal,
 * e é o que deixa o técnico abrir a lista no Povoado Manuino.
 *
 * Datas ficam como texto ISO, do jeito que vieram do servidor: evita conversor
 * de tipo e não há consulta que precise ordenar por elas no SQL. A resposta do
 * item e a lista de opções ficam em JSON pelo mesmo motivo.
 */

@Entity(tableName = "ordem")
data class OrdemEntidade(
    @PrimaryKey val id: String,
    val numero: Int,
    val tipo: String,
    val status: String,
    val prioridade: String,
    val descricao: String,
    val laudo: String?,
    val agendadaPara: String?,
    val prazoSla: String?,
    val iniciadaEm: String?,
    val concluidaEm: String?,
    val criadoEm: String,
    @Embedded(prefix = "cliente_") val cliente: ClienteEmbutido,
    @Embedded(prefix = "usina_") val usina: UsinaEmbutida?,
    val responsavelId: String?,
    val responsavelNome: String?,
    val projetoId: String? = null,
    val projetoTitulo: String? = null,
    val resultado: String? = null,
    val assinaturaNome: String? = null,
    val instrucoes: String? = null,
    val exigeAssinatura: Boolean = false,
    /**
     * O carimbo do servidor da última mudança. A lista inteira pedida antes
     * de uma foto subir chega depois dela, com a OS velha: o carimbo diz qual
     * das duas cópias é a nova.
     */
    val atualizadoEm: String? = null,
    val relatorio: String? = null,
    /** O histórico como veio do servidor, em JSON — só leitura. */
    val historicoJson: String = "[]",
)

data class ClienteEmbutido(
    val id: String,
    val nome: String,
    val telefone: String?,
    val endereco: String?,
    val cidade: String?,
    val uf: String?,
)

data class UsinaEmbutida(
    val id: String,
    val nome: String,
    val potenciaKwp: Double?,
    val latitude: Double?,
    val longitude: Double?,
)

@Entity(
    tableName = "checklist_item",
    foreignKeys = [
        ForeignKey(
            entity = OrdemEntidade::class,
            parentColumns = ["id"],
            childColumns = ["ordemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ordemId")],
)
data class ChecklistItemEntidade(
    @PrimaryKey val id: String,
    val ordemId: String,
    val ordem: Int,
    val descricao: String,
    val obrigatorio: Boolean,
    val concluido: Boolean,
    val observacao: String?,
    val secao: String? = null,
    val ajuda: String? = null,
    val tipoResposta: String = "check",
    /** Lista de opções em JSON: `["Bipolar","Tripolar"]`. */
    val opcoesJson: String = "[]",
    val unidade: String? = null,
    val fotosMinimas: Int = 0,
    val apenasCamera: Boolean = false,
    /** `nao`, `sem_foto` ou `sempre` (banco versão 3). */
    @ColumnInfo(defaultValue = "'nao'") val exigeObservacao: String = "nao",
    @ColumnInfo(defaultValue = "0") val permiteAgendar: Boolean = false,
    @ColumnInfo(defaultValue = "0") val aceitaVideo: Boolean = false,
    @ColumnInfo(defaultValue = "0") val agendar: Boolean = false,
    /** A resposta em JSON (`true`, `40`, `"Growatt"`, `["A","B"]`), ou nulo. */
    val valorJson: String? = null,
    val respondidoEm: String? = null,
)

/**
 * Foto ou assinatura. As que vieram do servidor são substituídas a cada
 * sincronização; as `pendente` foram tiradas aqui e esperam sinal — essas a
 * sincronização não toca.
 *
 * Sem chave estrangeira para `ordem` de propósito: a sincronização apaga e
 * regrava as OS, e uma foto pendente não pode ir junto.
 */
@Entity(tableName = "anexo", indices = [Index("ordemId")])
data class AnexoEntidade(
    @PrimaryKey val id: String,
    val ordemId: String,
    val itemId: String?,
    val categoria: String,
    val capturadoEm: String?,
    val arquivoLocal: String? = null,
    val pendente: Boolean = false,
    val enviadoPorId: String? = null,
)

/** Nota da OS. As `pendente` foram escritas aqui e esperam sinal. Sem chave estrangeira, como os anexos. */
@Entity(tableName = "nota", indices = [Index("ordemId")])
data class NotaEntidade(
    @PrimaryKey val id: String,
    val ordemId: String,
    val texto: String,
    val autorId: String?,
    val autor: String?,
    val criadoEm: String?,
    val pendente: Boolean = false,
)

/**
 * O que o servidor avisou ao receber algo da fila — "o serial já está em
 * outro projeto", "o projeto avançou para Documentação". A fila sobe sem a
 * tela aberta; o aviso fica guardado até o técnico ver e dispensar.
 */
@Entity(tableName = "aviso", indices = [Index("ordemId")])
data class AvisoEntidade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ordemId: String,
    val texto: String,
    val criadoEm: Long,
)

/**
 * A fila do que o técnico fez e ainda não chegou ao servidor: ação, resposta
 * ou foto, na ordem em que aconteceu.
 *
 * `idCliente` é o mesmo id que vai para o servidor — reenviar depois de uma
 * queda de sinal não duplica nada lá. `erro` preenchido é recusa definitiva do
 * servidor (a OS foi cancelada pelo escritório, por exemplo): fica parada
 * até o técnico ver o motivo e descartar.
 *
 * Também sem chave estrangeira, pelo mesmo motivo dos anexos.
 */
@Entity(tableName = "pendente", indices = [Index("ordemId")])
data class PendenteEntidade(
    @PrimaryKey val idCliente: String,
    val ordemId: String,
    /**
     * De quem é. A sessão que cai não apaga a fila — o trabalho de campo é da
     * pessoa, e volta a subir quando ela entrar de novo. Se entrar outra
     * pessoa neste celular, o que era da anterior é descartado.
     */
    val usuarioId: String,
    /** `acao`, `resposta`, `anexo` ou `nota`. */
    val tipo: String,
    /** O pedido pronto para a API, em JSON. */
    val corpoJson: String,
    val itemId: String? = null,
    /** O arquivo da foto ou da assinatura, no caso de `anexo`. */
    val arquivo: String? = null,
    val criadoEm: Long,
    val tentativas: Int = 0,
    val erro: String? = null,
)

data class OrdemComChecklist(
    @Embedded val ordem: OrdemEntidade,
    @Relation(parentColumn = "id", entityColumn = "ordemId")
    val itens: List<ChecklistItemEntidade>,
    @Relation(parentColumn = "id", entityColumn = "ordemId")
    val anexos: List<AnexoEntidade>,
    @Relation(parentColumn = "id", entityColumn = "ordemId")
    val notas: List<NotaEntidade>,
)

/** O carimbo de cada OS guardada — para decidir qual cópia é a nova. */
data class VersaoOrdem(val id: String, val atualizadoEm: String?)

/**
 * Um ponto do rastreamento em campo, esperando subir.
 *
 * Fica aqui desde o instante em que o GPS responde: sem sinal, ou com o
 * Android matando o serviço, o ponto não se perde — sobe no próximo envio.
 * Separado da fila de envio de propósito: posição é volume (um ponto a cada
 * 5 s dirigindo) e não tem ordem a respeitar com as ações da OS.
 */
@Entity(tableName = "posicao", indices = [Index("usuarioId")])
data class PosicaoEntidade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val usuarioId: String,
    val ordemId: String,
    val latitude: Double,
    val longitude: Double,
    val precisao: Float?,
    val velocidade: Float?,
    val bateria: Int?,
    /** `deslocamento` ou `eco`. */
    val modo: String,
    /** Hora do celular, em milissegundos. */
    val capturadoEm: Long,
)
