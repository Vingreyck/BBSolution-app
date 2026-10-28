package br.com.selebi.app.nucleo.banco

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction

actual suspend fun <R> SelebiBanco.emTransacao(bloco: suspend () -> R): R = withTransaction { bloco() }

/**
 * O banco do Android, no mesmo arquivo de sempre (`selebi.db`). Da versão 1
 * para a 2 ainda pode recriar; da 2 em diante, só com `Migration` (ver
 * [SelebiBanco]).
 */
fun criarBanco(contexto: Context): SelebiBanco =
    Room.databaseBuilder(contexto, SelebiBanco::class.java, "selebi.db")
        .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
        .addMigrations(MIGRACAO_2_3)
        .build()
