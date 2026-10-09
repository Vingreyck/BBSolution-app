package br.com.selebi.app.nucleo.arquivos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * As fotos e assinaturas da OS no celular.
 *
 * A foto tirada fica em `files/os/<id da OS>/` — e não no cache, que o
 * Android esvazia quando falta espaço: até subir, ela é trabalho de campo que
 * não existe em nenhum outro lugar. Depois do envio o arquivo ganha o id do
 * servidor como nome, para a miniatura continuar aparecendo sem baixar de novo.
 *
 * Toda foto é reduzida antes de guardar: 1600 px no lado maior, JPEG 82. Uma
 * foto de 12 MP sai da câmera com 4 a 6 MB e fica com uns 350 KB — dá para
 * ler o número do disjuntor e sobe no 3G do povoado.
 */
@Singleton
class ArquivosOs @Inject constructor(@ApplicationContext private val contexto: Context) {

    private val raiz: File get() = File(contexto.filesDir, "os")
    private val capturas: File get() = File(contexto.cacheDir, "camera")

    fun pastaDa(ordemId: String): File = File(raiz, ordemId).apply { mkdirs() }

    /**
     * Onde a câmera grava a foto crua. O endereço `content://` é o que o app
     * da câmera recebe — ele não tem acesso direto às pastas do Selebi.
     */
    fun novaCaptura(): Pair<File, Uri> {
        capturas.mkdirs()
        val arquivo = File(capturas, "${UUID.randomUUID()}.jpg")
        return arquivo to FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)
    }

    data class FotoPronta(
        val arquivo: File,
        /** Hora em que a foto foi tirada, pelo EXIF — nula se a foto não disser. */
        val capturadoEm: Instant?,
        /** Onde a foto foi tirada, pelo EXIF (fotos da galeria). */
        val latitude: Double?,
        val longitude: Double?,
    )

    /**
     * Reduz, endireita e guarda a foto como `<id>.jpg` na pasta da OS.
     * Nulo se o arquivo não for uma imagem que o Android saiba abrir.
     */
    suspend fun prepararFoto(origem: Uri, ordemId: String, id: String): FotoPronta? = withContext(Dispatchers.IO) {
        runCatching {
            val exif = abrir(origem)?.use { ExifInterface(it) }
            val bitmap = abrir(origem)?.use { decodificar(it, LADO_MAXIMO, amostra = amostraPara(origem, LADO_MAXIMO)) }
                ?: return@runCatching null
            val pronto = endireitar(reduzir(bitmap, LADO_MAXIMO), exif?.rotationDegrees ?: 0, exif?.isFlipped == true)
            val destino = File(pastaDa(ordemId), "$id.jpg")
            destino.outputStream().use { pronto.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, it) }
            pronto.recycle()
            val latLong = exif?.latLong
            FotoPronta(destino, dataDoExif(exif), latLong?.getOrNull(0), latLong?.getOrNull(1))
        }.getOrElse {
            Log.w("ArquivosOs", "Não deu para preparar a foto.", it)
            null
        }
    }

    /** A assinatura em PNG: traço preto no branco, sem perda — é o que vai no relatório. */
    suspend fun salvarAssinatura(bitmap: Bitmap, ordemId: String, id: String): File = withContext(Dispatchers.IO) {
        val destino = File(pastaDa(ordemId), "$id.png")
        destino.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        destino
    }

    /** O arquivo passa a ter o id do servidor como nome. Devolve o novo caminho. */
    fun renomear(caminho: String, novoId: String): String? {
        val atual = File(caminho)
        if (!atual.exists()) return null
        val novo = File(atual.parentFile, novoId + "." + atual.extension)
        return if (atual.renameTo(novo)) novo.path else atual.path
    }

    /** A foto guardada deste anexo, se ela foi tirada neste celular. */
    fun arquivoDe(ordemId: String, anexoId: String): String? {
        val pasta = File(raiz, ordemId)
        return sequenceOf("jpg", "png").map { File(pasta, "$anexoId.$it") }.firstOrNull { it.exists() }?.path
    }

    fun apagar(caminho: String?) {
        if (caminho != null) runCatching { File(caminho).delete() }
    }

    /** Onde fica a cópia baixada de uma foto que não foi tirada aqui. */
    fun copiaDoServidor(anexoId: String): File = File(File(contexto.cacheDir, "anexos").apply { mkdirs() }, anexoId)

    /**
     * Faxina: apaga as pastas de OS que saíram do celular (concluída há mais
     * de 30 dias, passada para outra pessoa) e as capturas cruas esquecidas.
     * Arquivo que ainda está em uso — foto esperando sinal — nunca sai.
     */
    suspend fun faxina(ordensVivas: Set<String>, emUso: Set<String>) = withContext(Dispatchers.IO) {
        runCatching {
            raiz.listFiles()?.filter { it.isDirectory && it.name !in ordensVivas }?.forEach { pasta ->
                pasta.listFiles()?.filter { it.path !in emUso }?.forEach { it.delete() }
                if (pasta.listFiles().isNullOrEmpty()) pasta.delete()
            }
            val ontem = System.currentTimeMillis() - 24 * 3_600_000L
            capturas.listFiles()?.filter { it.lastModified() < ontem }?.forEach { it.delete() }
        }
        Unit
    }

    // -------------------------------------------------------------------------

    private fun abrir(uri: Uri): InputStream? = contexto.contentResolver.openInputStream(uri)

    private fun amostraPara(uri: Uri, lado: Int): Int {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        return amostra(limites.outWidth, limites.outHeight, lado)
    }

    companion object {
        const val LADO_MAXIMO = 1600
        const val QUALIDADE = 82

        private val formatoExif = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        /**
         * A maior potência de 2 que ainda deixa a imagem com pelo menos `lado`
         * pixels no lado maior — decodificar já pequeno poupa a memória que um
         * celular simples não tem para uma foto de 48 MP inteira.
         */
        fun amostra(largura: Int, altura: Int, lado: Int): Int {
            var n = 1
            val maior = max(largura, altura)
            if (maior <= 0) return 1
            while (maior / (n * 2) >= lado) n *= 2
            return n
        }

        fun decodificar(entrada: InputStream, lado: Int, amostra: Int): Bitmap? =
            BitmapFactory.decodeStream(entrada, null, BitmapFactory.Options().apply { inSampleSize = amostra })

        fun reduzir(bitmap: Bitmap, lado: Int): Bitmap {
            val maior = max(bitmap.width, bitmap.height)
            if (maior <= lado) return bitmap
            val escala = lado.toFloat() / maior
            val reduzido = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * escala).roundToInt().coerceAtLeast(1),
                (bitmap.height * escala).roundToInt().coerceAtLeast(1),
                true,
            )
            if (reduzido !== bitmap) bitmap.recycle()
            return reduzido
        }

        /** A câmera grava "deitado" e anota no EXIF quanto girar; aqui a foto já sai de pé. */
        fun endireitar(bitmap: Bitmap, graus: Int, espelhada: Boolean): Bitmap {
            if (graus == 0 && !espelhada) return bitmap
            val matriz = Matrix().apply {
                if (espelhada) postScale(-1f, 1f)
                postRotate(graus.toFloat())
            }
            val girado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
            if (girado !== bitmap) bitmap.recycle()
            return girado
        }

        fun dataDoExif(exif: ExifInterface?): Instant? {
            val texto = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif?.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return null
            return runCatching {
                LocalDateTime.parse(texto.trim(), formatoExif).atZone(ZoneId.systemDefault()).toInstant()
            }.getOrNull()
        }
    }
}
