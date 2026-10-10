package br.com.selebi.app.nucleo.arquivos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import br.com.selebi.app.nucleo.plataforma.OrigemFoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * O [ProcessadorFotos] do Android: `BitmapFactory` e o EXIF.
 *
 * A câmera grava "deitado" e anota no EXIF quanto girar; aqui a foto já sai
 * de pé. E decodifica já pequeno: uma foto de 48 MP inteira não cabe na
 * memória de um celular simples.
 */
class ProcessadorFotosAndroid(private val contexto: Context) : ProcessadorFotos {

    /** Onde a câmera grava a foto crua (ver `rememberCamera`). */
    val capturas: File get() = File(contexto.cacheDir, "camera")

    override suspend fun preparar(origem: OrigemFoto, destino: String, lado: Int, qualidade: Int): DadosFoto? =
        withContext(Dispatchers.IO) {
            runCatching {
                val exif = abrir(origem)?.use { ExifInterface(it) }
                val bitmap = abrir(origem)?.use { decodificar(it, amostra = amostraPara(origem, lado)) }
                    ?: return@runCatching null
                val pronto = endireitar(reduzir(bitmap, lado), exif?.rotationDegrees ?: 0, exif?.isFlipped == true)
                File(destino).outputStream().use { pronto.compress(Bitmap.CompressFormat.JPEG, qualidade, it) }
                pronto.recycle()
                val latLong = exif?.latLong
                DadosFoto(dataDoExif(exif), latLong?.getOrNull(0), latLong?.getOrNull(1))
            }.getOrElse {
                Log.w("ProcessadorFotos", "Não deu para preparar a foto.", it)
                null
            }
        }

    override suspend fun miniatura(caminho: String, lado: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(caminho, limites)
            val amostra = amostra(limites.outWidth, limites.outHeight, lado)
            val bitmap: Bitmap = BitmapFactory.decodeFile(caminho, BitmapFactory.Options().apply { inSampleSize = amostra })
                ?: return@runCatching null
            // Foto anexada pelo site pode vir "deitada", com o giro só no EXIF.
            val exif = runCatching { ExifInterface(caminho) }.getOrNull()
            endireitar(reduzir(bitmap, lado), exif?.rotationDegrees ?: 0, exif?.isFlipped == true).asImageBitmap()
        }.getOrNull()
    }

    override fun limparCapturasAntigas() {
        val ontem = System.currentTimeMillis() - 24 * 3_600_000L
        runCatching { capturas.listFiles()?.filter { it.lastModified() < ontem }?.forEach { it.delete() } }
    }

    // -------------------------------------------------------------------------

    /** `content://` e `file://` pelo sistema; caminho puro, direto do arquivo. */
    private fun abrir(origem: OrigemFoto): InputStream? {
        val e = origem.endereco
        return if (e.startsWith("content://") || e.startsWith("file://")) {
            contexto.contentResolver.openInputStream(Uri.parse(e))
        } else {
            File(e).takeIf { it.exists() }?.inputStream()
        }
    }

    private fun amostraPara(origem: OrigemFoto, lado: Int): Int {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir(origem)?.use { BitmapFactory.decodeStream(it, null, limites) }
        return amostra(limites.outWidth, limites.outHeight, lado)
    }

    companion object {
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

        fun decodificar(entrada: InputStream, amostra: Int): Bitmap? =
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
                // O EXIF escreve "2026:09:25 14:30:00"; o ISO quer "2026-09-25T14:30:00".
                val iso = texto.trim().replaceFirst(' ', 'T').replaceFirst(':', '-').replaceFirst(':', '-')
                LocalDateTime.parse(iso).toInstant(TimeZone.currentSystemDefault())
            }.getOrNull()
        }
    }
}
