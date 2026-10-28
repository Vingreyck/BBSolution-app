package br.com.selebi.app.nucleo.banco

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OrdemDao {

    @Transaction
    @Query("SELECT * FROM ordem ORDER BY numero DESC")
    fun observarTodas(): Flow<List<OrdemComChecklist>>

    @Transaction
    @Query("SELECT * FROM ordem WHERE id = :id")
    fun observar(id: String): Flow<OrdemComChecklist?>

    @Transaction
    @Query("SELECT * FROM ordem WHERE id = :id")
    suspend fun buscar(id: String): OrdemComChecklist?

    @Query("SELECT id, atualizadoEm FROM ordem")
    suspend fun versoes(): List<VersaoOrdem>

    @Query("SELECT id FROM ordem")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun salvarOrdens(ordens: List<OrdemEntidade>)

    @Upsert
    suspend fun salvarItens(itens: List<ChecklistItemEntidade>)

    @Upsert
    suspend fun salvarAnexos(anexos: List<AnexoEntidade>)

    @Upsert
    suspend fun salvarNotas(notas: List<NotaEntidade>)

    @Query("DELETE FROM ordem")
    suspend fun apagarTodas()

    @Query("DELETE FROM ordem WHERE id = :id")
    suspend fun apagar(id: String)

    @Query("DELETE FROM checklist_item WHERE ordemId = :ordemId")
    suspend fun apagarItensDa(ordemId: String)

    /** O que veio do servidor. O que foi feito aqui e ainda não subiu fica. */
    @Query("DELETE FROM anexo WHERE pendente = 0")
    suspend fun apagarAnexosDoServidor()

    @Query("DELETE FROM anexo WHERE ordemId = :ordemId AND pendente = 0")
    suspend fun apagarAnexosDoServidorDa(ordemId: String)

    @Query("DELETE FROM nota WHERE pendente = 0")
    suspend fun apagarNotasDoServidor()

    @Query("DELETE FROM nota WHERE ordemId = :ordemId AND pendente = 0")
    suspend fun apagarNotasDoServidorDa(ordemId: String)

    @Query("DELETE FROM anexo WHERE id = :id")
    suspend fun apagarAnexo(id: String)

    @Query("DELETE FROM nota WHERE id = :id")
    suspend fun apagarNota(id: String)

    @Query("SELECT * FROM anexo WHERE id = :id")
    suspend fun buscarAnexo(id: String): AnexoEntidade?

    @Query("SELECT * FROM checklist_item WHERE id = :id")
    suspend fun buscarItem(id: String): ChecklistItemEntidade?

    /** Fotos (e vídeos) do item, as enviadas e as que esperam sinal. */
    @Query("SELECT COUNT(*) FROM anexo WHERE itemId = :itemId AND categoria IN ('foto', 'video')")
    suspend fun contarFotos(itemId: String): Int

    /** Os arquivos de foto guardados no celular — para a faxina não apagar nenhum. */
    @Query("SELECT arquivoLocal FROM anexo WHERE arquivoLocal IS NOT NULL")
    suspend fun arquivosLocais(): List<String>

    // --- o que o técnico fez e ainda não voltou do servidor ---------------

    @Query("UPDATE ordem SET status = :status WHERE id = :id")
    suspend fun mudarStatus(id: String, status: String)

    /** "Não atendida": volta para a fila, sem data. */
    @Query("UPDATE ordem SET status = :status, agendadaPara = NULL WHERE id = :id")
    suspend fun mudarStatusSemData(id: String, status: String)

    /** O início conta uma vez só: retomar não muda a hora da chegada. */
    @Query("UPDATE ordem SET status = :status, iniciadaEm = COALESCE(iniciadaEm, :quando) WHERE id = :id")
    suspend fun iniciarLocal(id: String, status: String, quando: String)

    @Query(
        "UPDATE ordem SET status = :status, resultado = :resultado, laudo = COALESCE(:laudo, laudo), " +
            "assinaturaNome = COALESCE(:assinaturaNome, assinaturaNome), concluidaEm = :quando WHERE id = :id",
    )
    suspend fun concluirLocal(
        id: String,
        status: String,
        resultado: String,
        laudo: String?,
        assinaturaNome: String?,
        quando: String,
    )

    @Query(
        "UPDATE checklist_item SET valorJson = :valorJson, observacao = :observacao, concluido = :concluido, " +
            "agendar = :agendar, respondidoEm = :respondidoEm WHERE id = :id",
    )
    suspend fun responderLocal(
        id: String,
        valorJson: String?,
        observacao: String?,
        concluido: Boolean,
        agendar: Boolean,
        respondidoEm: String,
    )

    @Query("UPDATE checklist_item SET concluido = :concluido WHERE id = :id")
    suspend fun marcarCumprido(id: String, concluido: Boolean)
}

@Dao
interface PendenteDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserir(pendente: PendenteEntidade)

    /** A fila na ordem em que as coisas aconteceram — é a ordem que o servidor precisa. */
    @Query("SELECT * FROM pendente ORDER BY criadoEm, idCliente")
    suspend fun todos(): List<PendenteEntidade>

    @Query("SELECT * FROM pendente WHERE ordemId = :ordemId ORDER BY criadoEm, idCliente")
    suspend fun daOrdem(ordemId: String): List<PendenteEntidade>

    /** O próximo a subir desta pessoa. Os recusados ficam parados, esperando ela ver. */
    @Query("SELECT * FROM pendente WHERE usuarioId = :usuarioId AND erro IS NULL ORDER BY criadoEm, idCliente LIMIT 1")
    suspend fun proximo(usuarioId: String): PendenteEntidade?

    @Query("SELECT * FROM pendente ORDER BY criadoEm, idCliente")
    fun observarTodos(): Flow<List<PendenteEntidade>>

    @Query("SELECT * FROM pendente WHERE ordemId = :ordemId ORDER BY criadoEm, idCliente")
    fun observarDa(ordemId: String): Flow<List<PendenteEntidade>>

    @Query("SELECT * FROM pendente WHERE idCliente = :id")
    suspend fun buscar(id: String): PendenteEntidade?

    /** A resposta deste item que ainda não subiu — a nova toma o lugar dela. */
    @Query("SELECT * FROM pendente WHERE tipo = 'resposta' AND itemId = :itemId AND erro IS NULL")
    suspend fun respostasNaoEnviadas(itemId: String): List<PendenteEntidade>

    @Query("DELETE FROM pendente WHERE idCliente = :id")
    suspend fun apagar(id: String)

    @Query("UPDATE pendente SET erro = :erro, tentativas = tentativas + 1 WHERE idCliente = :id")
    suspend fun marcarErro(id: String, erro: String)

    @Query("UPDATE pendente SET tentativas = tentativas + 1 WHERE idCliente = :id")
    suspend fun contarTentativa(id: String)

    @Query("SELECT * FROM pendente WHERE usuarioId <> :usuarioId")
    suspend fun deOutros(usuarioId: String): List<PendenteEntidade>

    @Query("SELECT COUNT(*) FROM pendente")
    fun contar(): Flow<Int>
}

@Dao
interface AvisoDao {
    @Insert
    suspend fun inserir(avisos: List<AvisoEntidade>)

    @Query("SELECT * FROM aviso WHERE ordemId = :ordemId ORDER BY criadoEm")
    fun observarDa(ordemId: String): Flow<List<AvisoEntidade>>

    @Query("DELETE FROM aviso WHERE id = :id")
    suspend fun apagar(id: Long)

    @Query("DELETE FROM aviso")
    suspend fun apagarTodos()
}

@Dao
interface PosicaoDao {
    @Insert
    suspend fun inserir(posicao: PosicaoEntidade): Long

    /** Os mais velhos primeiro: o trajeto chega ao servidor na ordem em que foi feito. */
    @Query("SELECT * FROM posicao WHERE usuarioId = :usuarioId ORDER BY capturadoEm, id LIMIT :limite")
    suspend fun lote(usuarioId: String, limite: Int): List<PosicaoEntidade>

    @Query("DELETE FROM posicao WHERE id IN (:ids)")
    suspend fun apagar(ids: List<Long>)

    /** Ponto de mais de uma semana o servidor recusa — não adianta guardar. */
    @Query("DELETE FROM posicao WHERE capturadoEm < :antesDe")
    suspend fun apagarVelhas(antesDe: Long)

    @Query("DELETE FROM posicao WHERE usuarioId <> :usuarioId")
    suspend fun apagarDeOutros(usuarioId: String)

    @Query("SELECT COUNT(*) FROM posicao WHERE usuarioId = :usuarioId")
    suspend fun contar(usuarioId: String): Int
}
