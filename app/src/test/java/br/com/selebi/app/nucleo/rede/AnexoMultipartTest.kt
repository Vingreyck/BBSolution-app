package br.com.selebi.app.nucleo.rede

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * O envio da foto, conferido do jeito que importa: manda pelo `SelebiApiKtor`
 * para um servidor local e guarda o que chegou em `build/captura-anexo/`. Daí o
 * leitor de formulário do servidor (`req.formData()` do Node, que é quem recusava
 * a foto com "esperava um formulário multipart") lê o mesmo arquivo — ver
 * `conferir-anexo.mjs`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnexoMultipartTest {

    private fun linha(entrada: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = entrada.read()
            if (c < 0) return if (b.size() == 0) null else b.toString(Charsets.ISO_8859_1)
            if (c == '\n'.code) return b.toString(Charsets.ISO_8859_1).trimEnd('\r')
            b.write(c)
        }
    }

    @Test
    fun `o envio da foto chega como multipart que o servidor sabe ler`() {
        val pasta = File("build/captura-anexo").apply { mkdirs() }
        val corpo = ByteArrayOutputStream()
        val cabecalhos = StringBuilder()

        val servidor = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val ouvindo = thread {
            servidor.accept().use { s ->
                val entrada = s.getInputStream()
                cabecalhos.appendLine(linha(entrada))
                var tamanho = -1
                var picotado = false
                while (true) {
                    val l = linha(entrada) ?: break
                    if (l.isEmpty()) break
                    cabecalhos.appendLine(l)
                    if (l.startsWith("Content-Length:", ignoreCase = true)) tamanho = l.substringAfter(':').trim().toInt()
                    if (l.startsWith("Transfer-Encoding:", ignoreCase = true) && l.contains("chunked", true)) picotado = true
                }
                if (picotado) {
                    while (true) {
                        val n = (linha(entrada) ?: break).trim().toInt(16)
                        if (n == 0) break
                        corpo.write(entrada.readNBytes(n))
                        linha(entrada)
                    }
                } else if (tamanho >= 0) {
                    corpo.write(entrada.readNBytes(tamanho))
                }
                val resposta = """{"erro":"x","mensagem":"capturado"}""".toByteArray()
                s.getOutputStream().apply {
                    write("HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nContent-Length: ${resposta.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(resposta)
                    flush()
                }
            }
        }

        val foto = File(pasta, "foto.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(2000) { (it % 251).toByte() })
        }
        try {
            val api = SelebiApiKtor(
                base = "http://127.0.0.1:${servidor.localPort}/",
                portador = PortadorToken().apply { token = "token-de-teste" },
                eventos = EventosSessao(),
                json = ModuloRede.json(),
            )
            runBlocking {
                runCatching {
                    api.anexar(
                        id = "11111111-1111-4111-8111-111111111111",
                        arquivo = ArquivoEnvio(foto.path, "foto.jpg", "image/jpeg"),
                        idCliente = "22222222-2222-4222-8222-222222222222",
                        categoria = "foto",
                        itemId = "33333333-3333-4333-8333-333333333333",
                        capturadoEm = "2026-10-16T12:00:00Z",
                        latitude = "-10.68",
                        longitude = "-37.42",
                    )
                }
            }
            ouvindo.join(5_000)
        } finally {
            servidor.close()
        }

        File(pasta, "cabecalhos.txt").writeText(cabecalhos.toString())
        File(pasta, "corpo.bin").writeBytes(corpo.toByteArray())
        assertTrue("O servidor de teste não recebeu nada.", corpo.size() > 0)

        val texto = corpo.toString(Charsets.ISO_8859_1)
        assertTrue(texto.contains("name=\"arquivo\"; filename=\"foto.jpg\""))
        assertTrue(texto.contains("name=\"idCliente\""))
        assertFalse("Sem Content-Length dentro das partes.", texto.contains("Content-Length:"))
    }

    @Test
    fun `o corpo escrito a mao tem os campos certos`() {
        val c = montarMultipart(
            campos = listOf("idCliente" to "abc", "categoria" to "foto"),
            nomeCampoArquivo = "arquivo",
            nomeArquivo = "a\"b.jpg",
            tipoArquivo = "image/jpeg",
            conteudo = byteArrayOf(1, 2, 3),
            limite = "L",
        )
        val t = c.bytes.toString(Charsets.ISO_8859_1)
        assertTrue(t.startsWith("--L\r\nContent-Disposition: form-data; name=\"idCliente\"\r\n\r\nabc\r\n"))
        assertTrue(t.contains("filename=\"a%22b.jpg\""))
        assertTrue(t.endsWith("\r\n--L--\r\n"))
        assertEquals("multipart/form-data; boundary=L", c.tipo)
    }
}
