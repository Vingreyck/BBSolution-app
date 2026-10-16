package br.com.selebi.app.nucleo.rede

import kotlin.random.Random

/**
 * O corpo de um formulário `multipart/form-data`, escrito à mão.
 *
 * Feito aqui, e não pelo montador do Ktor, porque o leitor de formulário do
 * servidor (`req.formData()` do Node) só aceita o nome do campo **entre aspas**
 * (`name="arquivo"`). O Ktor escreve `name=arquivo`, sem aspas, e põe um
 * `Content-Length` dentro de cada parte: o servidor responde "esperava um
 * formulário multipart" e nenhuma foto sobe. O `AnexoMultipartTest` confere o
 * que sai daqui com o mesmo leitor.
 */
class CorpoMultipart(val limite: String, val bytes: ByteArray) {
    val tipo: String get() = "multipart/form-data; boundary=$limite"
}

private const val FIM_DE_LINHA = "\r\n"

fun montarMultipart(
    campos: List<Pair<String, String>>,
    nomeCampoArquivo: String,
    nomeArquivo: String,
    tipoArquivo: String,
    conteudo: ByteArray,
    limite: String = "selebi" + Random.nextLong().toULong().toString(16) + Random.nextLong().toULong().toString(16),
): CorpoMultipart {
    val saida = ByteBuilder()
    for ((nome, valor) in campos) {
        saida.texto("--$limite$FIM_DE_LINHA")
        saida.texto("Content-Disposition: form-data; name=\"${escapar(nome)}\"$FIM_DE_LINHA$FIM_DE_LINHA")
        saida.texto(valor)
        saida.texto(FIM_DE_LINHA)
    }
    saida.texto("--$limite$FIM_DE_LINHA")
    saida.texto(
        "Content-Disposition: form-data; name=\"${escapar(nomeCampoArquivo)}\"; filename=\"${escapar(nomeArquivo)}\"$FIM_DE_LINHA",
    )
    saida.texto("Content-Type: $tipoArquivo$FIM_DE_LINHA$FIM_DE_LINHA")
    saida.bytes(conteudo)
    saida.texto(FIM_DE_LINHA)
    saida.texto("--$limite--$FIM_DE_LINHA")
    return CorpoMultipart(limite, saida.paraArray())
}

/** Aspas e quebra de linha dentro do nome quebrariam o cabeçalho. */
private fun escapar(texto: String): String =
    texto.replace("\\", "\\\\").replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A")

private class ByteBuilder {
    private var dados = ByteArray(8192)
    private var tamanho = 0

    fun texto(valor: String) = bytes(valor.encodeToByteArray())

    fun bytes(novos: ByteArray) {
        if (tamanho + novos.size > dados.size) dados = dados.copyOf(maxOf(dados.size * 2, tamanho + novos.size))
        novos.copyInto(dados, tamanho)
        tamanho += novos.size
    }

    fun paraArray(): ByteArray = dados.copyOf(tamanho)
}
