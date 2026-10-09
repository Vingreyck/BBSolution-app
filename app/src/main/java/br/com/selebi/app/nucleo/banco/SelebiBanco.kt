package br.com.selebi.app.nucleo.banco

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
abstract class SelebiBanco : RoomDatabase() {
    abstract fun ordens(): OrdemDao
    abstract fun pendentes(): PendenteDao
    abstract fun avisos(): AvisoDao
    abstract fun posicoes(): PosicaoDao
}

@Module
@InstallIn(SingletonComponent::class)
object ModuloBanco {

    /**
     * Da versão 1 para a 2 o banco ainda pode ser apagado e recriado: até aqui
     * ele era só cópia do servidor, e nenhum celular tinha a fila de envio.
     *
     * **Da versão 2 em diante, não.** A tabela `pendente` guarda o que o
     * técnico fez sem sinal — foto de vistoria, resposta, conclusão — e apagar
     * o banco numa atualização do app perderia trabalho de campo que não existe
     * em nenhum outro lugar. Toda mudança daqui para a frente precisa de uma
     * `Migration` escrita a partir dos esquemas em `app/schemas`; por isso o
     * recriar só vale saindo da versão 1.
     */
    @Provides
    @Singleton
    fun banco(@ApplicationContext contexto: Context): SelebiBanco =
        Room.databaseBuilder(contexto, SelebiBanco::class.java, "selebi.db")
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
            .build()

    @Provides
    fun ordens(banco: SelebiBanco): OrdemDao = banco.ordens()

    @Provides
    fun pendentes(banco: SelebiBanco): PendenteDao = banco.pendentes()

    @Provides
    fun avisos(banco: SelebiBanco): AvisoDao = banco.avisos()

    @Provides
    fun posicoes(banco: SelebiBanco): PosicaoDao = banco.posicoes()
}
