package br.com.selebi.app.nucleo.arquivos

import androidx.compose.ui.graphics.ImageBitmap
import br.com.selebi.app.nucleo.plataforma.OrigemFoto
import br.com.selebi.app.nucleo.plataforma.paraPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** As pastas do app: a de `dados` o sistema não esvazia; a de `cache`, sim. */
class PastasApp(val dados: String, val cache: String)

/** O que a própria foto conta (EXIF): quando e onde ela foi tirada. */
data class DadosFoto(val capturadoEm: Instant?, val latitude: Double?, val longitude: Double?)

/** O trabalho com imagem, que cada sistema faz com a sua biblioteca. */
interface ProcessadorFotos {
    /**
     * Reduz para [lado] px no lado maior, endireita e grava em JPEG em
     * `destino`. Nulo se a origem não for uma imagem que o sistema abra.
     */
    suspend fun preparar(origem: OrigemFoto, destino: String, lado: Int, qualidade: Int): DadosFoto?

    /** A foto guardada, pequena e de pé, para a tela. */
    suspend fun miniatura(caminho: String, lado: Int): ImageBitmap?

    /** As fotos cruas da câmera esquecidas há mais de um dia (o app fechou no meio). */
    fun limparCapturasAntigas()
}

/**
 * As fotos e assinaturas da OS no celular.
 *
 * A foto tirada fica em `<dados>/os/<id da OS>/` — e não no cache, que o
 * sistema esvazia quando falta espaço: até subir, ela é trabalho de campo que
 * não existe em nenhum outro lugar. Depois do envio o arquivo ganha o id do
 * servidor como nome, para a miniatura continuar aparecendo sem baixar de novo.
 *
 * Toda foto é reduzida antes de guardar: 1600 px no lado maior, JPEG 82. Uma
 * foto de 12 MP sai da câmera com 4 a 6 MB e fica com uns 350 KB — dá para
 * ler o número do disjuntor e sobe no 3G do povoado.
 */
class ArquivosOs(private val pastas: PastasApp, val fotos: ProcessadorFotos) {

    private val raiz: Path get() = Path(pastas.dados, "os")

    fun pastaDa(ordemId: String): String {
        val pasta = Path(raiz, ordemId)
        SystemFileSystem.createDirectories(pasta)
        return pasta.toString()
    }

    data class FotoPronta(
        val arquivo: String,
        /** Hora em que a foto foi tirada, pelo EXIF — nula se a foto não disser. */
        val capturadoEm: Instant?,
        /** Onde a foto foi tirada, pelo EXIF (fotos da galeria). */
        val latitude: Double?,
        val longitude: Double?,
    )

    /**
     * Reduz, endireita e guarda a foto como `<id>.jpg` na pasta da OS.
     * Nulo se o arquivo não for uma imagem que o celular saiba abrir.
     */
    suspend fun prepararFoto(origem: OrigemFoto, ordemId: String, id: String): FotoPronta? {
        val destino = Path(pastaDa(ordemId), "$id.jpg").toString()
        val dados = fotos.preparar(origem, destino, LADO_MAXIMO, QUALIDADE) ?: return null
        return FotoPronta(destino, dados.capturadoEm, dados.latitude, dados.longitude)
    }

    /**
     * O vídeo gravado, guardado como `<id>.mp4` (ou `.mov`, no iPhone) na pasta da
     * OS. Vídeo não se reduz aqui — a câmera já grava curto e em qualidade média
     * (30 s, uns 10 a 25 MB). Nulo se o arquivo não existe ou veio vazio.
     */
    suspend fun guardarVideo(origem: OrigemFoto, ordemId: String, id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val de = Path(origem.endereco.removePrefix("file://"))
            if ((SystemFileSystem.metadataOrNull(de)?.size ?: 0L) <= 0L) return@runCatching null
            val extensao = de.name.substringAfterLast('.', "mp4").lowercase().takeIf { it == "mov" } ?: "mp4"
            val destino = Path(pastaDa(ordemId), "$id.$extensao")
            try {
                SystemFileSystem.atomicMove(de, destino)
            } catch (_: Exception) {
                // Outro volume (cartão, pasta do sistema): copia e apaga a origem.
                SystemFileSystem.source(de).buffered().use { entrada ->
                    SystemFileSystem.sink(destino).buffered().use { saida -> saida.transferFrom(entrada) }
                }
                SystemFileSystem.delete(de, mustExist = false)
            }
            destino.toString()
        }.getOrNull()
    }

    /** A foto crua da câmera é do app e sai depois de preparada; a da galeria é da pessoa e fica. */
    fun descartarOrigem(origem: OrigemFoto) {
        if (origem.temporaria) apagar(origem.endereco)
    }

    /** A assinatura em PNG: traço preto no branco, sem perda — é o que vai no relatório. */
    suspend fun salvarAssinatura(desenho: ImageBitmap, ordemId: String, id: String): String = withContext(Dispatchers.IO) {
        val destino = Path(pastaDa(ordemId), "$id.png")
        escrever(destino.toString(), desenho.paraPng())
        destino.toString()
    }

    /** O arquivo passa a ter o id do servidor como nome. Devolve o novo caminho. */
    fun renomear(caminho: String, novoId: String): String? {
        val atual = Path(caminho)
        if (!SystemFileSystem.exists(atual)) return null
        val pasta = atual.parent ?: return caminho
        val novo = Path(pasta, novoId + "." + atual.name.substringAfterLast('.', ""))
        return runCatching {
            SystemFileSystem.atomicMove(atual, novo)
            novo.toString()
        }.getOrDefault(caminho)
    }

    /** A foto guardada deste anexo, se ela foi tirada neste celular. */
    fun arquivoDe(ordemId: String, anexoId: String): String? {
        val pasta = Path(raiz, ordemId)
        return sequenceOf("jpg", "png", "mp4", "mov").map { Path(pasta, "$anexoId.$it") }.firstOrNull { SystemFileSystem.exists(it) }?.toString()
    }

    /** O arquivo existe e não está vazio. */
    fun temConteudo(caminho: String?): Boolean =
        caminho != null && (SystemFileSystem.metadataOrNull(Path(caminho))?.size ?: 0L) > 0L

    fun apagar(caminho: String?) {
        if (caminho != null) runCatching { SystemFileSystem.delete(Path(caminho), mustExist = false) }
    }

    /** Onde fica a cópia baixada de uma foto que não foi tirada aqui. */
    fun copiaDoServidor(anexoId: String): String {
        val pasta = Path(pastas.cache, "anexos")
        SystemFileSystem.createDirectories(pasta)
        return Path(pasta, anexoId).toString()
    }

    /** Grava inteiro num `.parcial` e só então troca de nome: arquivo pela metade nunca vira miniatura. */
    fun gravarCopia(destino: String, bytes: ByteArray): Boolean = runCatching {
        val parcial = "$destino.parcial"
        escrever(parcial, bytes)
        SystemFileSystem.atomicMove(Path(parcial), Path(destino))
        true
    }.getOrDefault(false)

    /**
     * Faxina: apaga as pastas de OS que saíram do celular (concluída há mais
     * de 30 dias, passada para outra pessoa) e as capturas cruas esquecidas.
     * Arquivo que ainda está em uso — foto esperando sinal — nunca sai.
     */
    suspend fun faxina(ordensVivas: Set<String>, emUso: Set<String>) = withContext(Dispatchers.IO) {
        runCatching {
            if (SystemFileSystem.exists(raiz)) {
                SystemFileSystem.list(raiz)
                    .filter { SystemFileSystem.metadataOrNull(it)?.isDirectory == true && it.name !in ordensVivas }
                    .forEach { pasta ->
                        SystemFileSystem.list(pasta).filter { it.toString() !in emUso }.forEach { SystemFileSystem.delete(it, mustExist = false) }
                        if (SystemFileSystem.list(pasta).isEmpty()) SystemFileSystem.delete(pasta, mustExist = false)
                    }
            }
            fotos.limparCapturasAntigas()
        }
        Unit
    }

    private fun escrever(caminho: String, bytes: ByteArray) {
        SystemFileSystem.sink(Path(caminho)).buffered().use { it.write(bytes) }
    }

    companion object {
        const val LADO_MAXIMO = 1600
        const val QUALIDADE = 82
    }
}
