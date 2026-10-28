package br.com.selebi.app.nucleo.diagnostico

import br.com.selebi.app.nucleo.dados.ApiFalsa
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.ModuloRede
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** O travamento fica guardado até a próxima abertura, sobe uma vez e só a mais recente fica. */
class RegistroDeFalhasTest {
    private val json = ModuloRede.json()
    private val pasta = Files.createTempDirectory("falhas").toFile()

    @Test
    fun `grava a falha, envia na abertura seguinte e apaga`() = runBlocking {
        val registro = RegistroDeFalhas(pasta.path, json)
        assertFalse(registro.temPendente())

        registro.gravar(IllegalStateException("sem item"))
        assertTrue(registro.temPendente())

        val api = ApiFalsa()
        registro.enviarPendente(api, ExecutorApi(json))
        assertEquals(1, api.falhasEnviadas.size)
        assertEquals("IllegalStateException", api.falhasEnviadas[0].tipo)
        assertEquals("sem item", api.falhasEnviadas[0].mensagem)
        assertTrue(api.falhasEnviadas[0].pilha.contains("RegistroDeFalhasTest"))
        assertFalse(registro.temPendente())

        // Sem nada guardado, não manda nada.
        registro.enviarPendente(api, ExecutorApi(json))
        assertEquals(1, api.falhasEnviadas.size)
    }

    @Test
    fun `travando em laco guarda so a mais recente`() = runBlocking {
        val registro = RegistroDeFalhas(pasta.path, json)
        registro.gravar(RuntimeException("primeira"))
        registro.gravar(RuntimeException("segunda"))
        val api = ApiFalsa()
        registro.enviarPendente(api, ExecutorApi(json))
        assertEquals(listOf("segunda"), api.falhasEnviadas.map { it.mensagem })
        assertEquals("depois de enviar não sobra arquivo", 0, File(pasta.path).listFiles()!!.size)
    }
}
