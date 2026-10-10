package br.com.selebi.app.nucleo.banco

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * O banco do celular.
 *
 * Da versão 1 para a 2 ele ainda podia ser apagado e recriado: até ali era só
 * cópia do servidor, e nenhum celular tinha a fila de envio.
 *
 * **Da versão 2 em diante, não.** A tabela `pendente` guarda o que o técnico
 * fez sem sinal — foto de vistoria, resposta, conclusão — e apagar o banco numa
 * atualização do app perderia trabalho de campo que não existe em nenhum outro
 * lugar. Toda mudança daqui para a frente precisa de uma `Migration` escrita a
 * partir dos esquemas em `shared/schemas`.
 */
@Database(
    entities = [
        OrdemEntidade::class,
        ChecklistItemEntidade::class,
        AnexoEntidade::class,
        PendenteEntidade::class,
        NotaEntidade::class,
        AvisoEntidade::class,
        PosicaoEntidade::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(SelebiBancoConstrutor::class)
abstract class SelebiBanco : RoomDatabase() {
    abstract fun ordens(): OrdemDao
    abstract fun pendentes(): PendenteDao
    abstract fun avisos(): AvisoDao
    abstract fun posicoes(): PosicaoDao
}

/** Gerado pelo Room para cada sistema. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object SelebiBancoConstrutor : RoomDatabaseConstructor<SelebiBanco> {
    override fun initialize(): SelebiBanco
}

/**
 * Tudo dentro de `bloco` grava junto ou nada grava: a fila e a cópia local
 * nunca ficam pela metade.
 */
expect suspend fun <R> SelebiBanco.emTransacao(bloco: suspend () -> R): R
