package br.com.selebi.app.nucleo.banco

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Da versão 2 para a 3: o item do checklist ganha quatro colunas.
 *
 * `exigeObservacao` (`nao`, `sem_foto` ou `sempre`), `permiteAgendar`,
 * `aceitaVideo` e `agendar`. As linhas que já estão no celular ficam com os
 * valores de antes (observação opcional, sem vídeo, sem agendar) — a próxima
 * sincronização traz as regras novas do servidor. A fila de envio não é tocada.
 */
val MIGRACAO_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `checklist_item` ADD COLUMN `exigeObservacao` TEXT NOT NULL DEFAULT 'nao'")
        connection.execSQL("ALTER TABLE `checklist_item` ADD COLUMN `permiteAgendar` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `checklist_item` ADD COLUMN `aceitaVideo` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `checklist_item` ADD COLUMN `agendar` INTEGER NOT NULL DEFAULT 0")
    }
}
