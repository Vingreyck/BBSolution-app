package br.com.selebi.app.nucleo.banco

import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** No iPhone o Room não tem o `withTransaction` do Android: a transação é pela conexão de escrita. */
actual suspend fun <R> SelebiBanco.emTransacao(bloco: suspend () -> R): R =
    useWriterConnection { conexao -> conexao.immediateTransaction { bloco() } }

/** O banco do iPhone, com o SQLite que vai dentro do app. */
fun criarBanco(caminho: String): SelebiBanco =
    Room.databaseBuilder<SelebiBanco>(name = caminho)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRACAO_2_3)
        .build()
