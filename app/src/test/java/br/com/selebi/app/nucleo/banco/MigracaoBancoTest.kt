package br.com.selebi.app.nucleo.banco

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A atualização do app não pode apagar o que o técnico fez sem sinal: a fila de
 * envio (`pendente`) guarda foto, resposta e conclusão que não existem em outro
 * lugar. Este teste cria o banco **como a versão 2 do app o deixou** — a partir do
 * esquema exportado em `shared/schemas/…/2.json` —, abre com a versão 3 e confere
 * que a fila e os itens continuam, com as colunas novas nos valores de antes.
 *
 * O próprio Room confere, ao abrir, que o banco migrado bate com as entidades;
 * se a migração esquecesse uma coluna, abrir já falharia aqui.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracaoBancoTest {

    private val esquemas = "../shared/schemas/br.com.selebi.app.nucleo.banco.SelebiBanco"

    @Test
    fun `da versao 2 para a 3 a fila e os itens ficam, com as colunas novas nos valores de antes`() {
        val contexto = RuntimeEnvironment.getApplication()
        val arquivo = contexto.getDatabasePath("migracao.db").apply { parentFile?.mkdirs(); delete() }

        // O banco de antes, do jeito que o Room da versão 2 o criou.
        val v2 = JSONObject(File("$esquemas/2.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(arquivo, null).use { db ->
            val entidades = v2.getJSONArray("entities")
            for (i in 0 until entidades.length()) {
                val e = entidades.getJSONObject(i)
                val tabela = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}", tabela))
                val indices = e.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) {
                    db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", tabela))
                }
            }
            val preparo = v2.getJSONArray("setupQueries")
            for (i in 0 until preparo.length()) db.execSQL(preparo.getString(i))

            db.execSQL(
                "INSERT INTO checklist_item (id, ordemId, ordem, descricao, obrigatorio, concluido, tipoResposta, " +
                    "opcoesJson, fotosMinimas, apenasCamera) VALUES ('i1', 'os1', 1, 'Aterramento', 1, 0, 'escolha', '[]', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO pendente (idCliente, ordemId, usuarioId, tipo, corpoJson, criadoEm, tentativas) " +
                    "VALUES ('p1', 'os1', 'u1', 'resposta', '{}', 1000, 0)",
            )
            db.version = 2
        }

        val banco = Room.databaseBuilder(contexto, SelebiBanco::class.java, "migracao.db")
            .addMigrations(MIGRACAO_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val fila = banco.pendentes().todos()
                assertEquals("a fila de envio não pode perder nada", listOf("p1"), fila.map { it.idCliente })

                val item = banco.ordens().buscarItem("i1")!!
                assertEquals("Aterramento", item.descricao)
                assertEquals("nao", item.exigeObservacao)
                assertFalse(item.permiteAgendar)
                assertFalse(item.aceitaVideo)
                assertFalse(item.agendar)
            }
        } finally {
            banco.close()
        }
    }
}
