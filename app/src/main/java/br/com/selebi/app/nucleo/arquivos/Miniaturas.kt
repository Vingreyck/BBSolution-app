package br.com.selebi.app.nucleo.arquivos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.rede.SelebiApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Quem entrega a imagem de uma foto da OS. Nas capturas de tela, ninguém. */
fun interface CarregadorMiniatura {
    suspend fun carregar(ordemId: String, anexo: AnexoOs, lado: Int): ImageBitmap?
}

/**
 * As fotos da OS na tela, pequenas.
 *
 * A foto tirada neste celular sai do arquivo guardado; a que veio de outro
 * lugar (o escritório anexou pelo site, o técnico trocou de aparelho) é
 * baixada uma vez e fica no cache. Na memória, só as miniaturas recentes —
 * uma vistoria tem 20 fotos, e 20 fotos inteiras derrubam celular simples.
 */
@Singleton
class Miniaturas @Inject constructor(
    private val api: SelebiApi,
    private val arquivos: ArquivosOs,
) : CarregadorMiniatura {
    private val memoria = object : LruCache<String, ImageBitmap>(TETO_MEMORIA) {
        override fun sizeOf(chave: String, valor: ImageBitmap): Int = valor.asAndroidBitmap().byteCount
    }

    /** No máximo três downloads ao mesmo tempo: a lista de fotos não entope o 3G. */
    private val downloads = Semaphore(3)

    fun daMemoria(anexoId: String, lado: Int): ImageBitmap? = memoria.get("$anexoId@$lado")

    override suspend fun carregar(ordemId: String, anexo: AnexoOs, lado: Int): ImageBitmap? {
        val chave = "${anexo.id}@$lado"
        memoria.get(chave)?.let { return it }
        val arquivo = anexo.arquivoLocal?.let(::File)?.takeIf { it.exists() }
            ?: if (anexo.pendente) null else baixar(ordemId, anexo.id)
        val imagem = arquivo?.let { decodificar(it, lado) } ?: return null
        memoria.put(chave, imagem)
        return imagem
    }

    private suspend fun baixar(ordemId: String, anexoId: String): File? {
        val destino = arquivos.copiaDoServidor(anexoId)
        if (destino.exists() && destino.length() > 0) return destino
        return try {
            downloads.withPermit {
                val corpo = api.baixarAnexo(ordemId, anexoId)
                withContext(Dispatchers.IO) {
                    val parcial = File(destino.parentFile, "${destino.name}.parcial")
                    corpo.use { r -> parcial.outputStream().use { saida -> r.byteStream().copyTo(saida) } }
                    if (parcial.renameTo(destino)) destino else null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sem sinal ou sem Drive: a tela mostra o ícone no lugar da foto.
            null
        }
    }

    private suspend fun decodificar(arquivo: File, lado: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(arquivo.path, limites)
            val amostra = ArquivosOs.amostra(limites.outWidth, limites.outHeight, lado)
            val bitmap: Bitmap = BitmapFactory.decodeFile(arquivo.path, BitmapFactory.Options().apply { inSampleSize = amostra })
                ?: return@runCatching null
            // Foto anexada pelo site pode vir "deitada", com o giro só no EXIF.
            val exif = runCatching { ExifInterface(arquivo.path) }.getOrNull()
            ArquivosOs.endireitar(
                ArquivosOs.reduzir(bitmap, lado),
                exif?.rotationDegrees ?: 0,
                exif?.isFlipped == true,
            ).asImageBitmap()
        }.getOrNull()
    }

    private companion object {
        const val TETO_MEMORIA = 24 * 1024 * 1024
    }
}
