@file:OptIn(ExperimentalForeignApi::class)

package br.com.selebi.app.nucleo.arquivos

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import br.com.selebi.app.nucleo.plataforma.OrigemFoto
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.timeIntervalSinceNow
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.max

/**
 * O [ProcessadorFotos] do iPhone: o `UIImage`, que já desenha a foto de pé
 * (o giro que a câmera anota ele aplica sozinho).
 *
 * A foto sai reduzida como no Android — 1600 px no lado maior, JPEG 82 — para
 * subir no 3G do povoado.
 */
class ProcessadorFotosIos(private val capturas: String) : ProcessadorFotos {

    override suspend fun preparar(origem: OrigemFoto, destino: String, lado: Int, qualidade: Int): DadosFoto? =
        withContext(Dispatchers.Default) {
            val imagem = UIImage.imageWithContentsOfFile(origem.endereco) ?: return@withContext null
            val dados = UIImageJPEGRepresentation(redimensionar(imagem, lado), qualidade / 100.0)
                ?: return@withContext null
            if (!dados.writeToFile(destino, atomically = true)) return@withContext null
            // A hora e o lugar da foto da galeria ficam para depois: o servidor usa a hora do envio.
            DadosFoto(capturadoEm = null, latitude = null, longitude = null)
        }

    override suspend fun miniatura(caminho: String, lado: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        runCatching {
            val imagem = UIImage.imageWithContentsOfFile(caminho) ?: return@runCatching null
            val dados = UIImageJPEGRepresentation(redimensionar(imagem, lado), 0.9) ?: return@runCatching null
            Image.makeFromEncoded(dados.paraBytes()).toComposeImageBitmap()
        }.getOrNull()
    }

    override fun limparCapturasAntigas() {
        val gerente = NSFileManager.defaultManager
        val nomes = gerente.contentsOfDirectoryAtPath(capturas, null) ?: return
        for (nome in nomes) {
            val caminho = "$capturas/$nome"
            val data = gerente.attributesOfItemAtPath(caminho, null)?.get(NSFileModificationDate) as? NSDate ?: continue
            if (data.timeIntervalSinceNow() < -86_400.0) gerente.removeItemAtPath(caminho, null)
        }
    }

    /** No máximo [lado] px no lado maior, já de pé, um pixel por ponto. */
    private fun redimensionar(imagem: UIImage, lado: Int): UIImage {
        val (largura, altura) = imagem.size.useContents { width to height }
        val maior = max(largura, altura)
        val escala = if (maior > lado) lado / maior else 1.0
        val w = (largura * escala).coerceAtLeast(1.0)
        val h = (altura * escala).coerceAtLeast(1.0)
        val formato = UIGraphicsImageRendererFormat.defaultFormat()
        formato.scale = 1.0
        formato.opaque = true
        val desenhista = UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = formato)
        return desenhista.imageWithActions { imagem.drawInRect(CGRectMake(0.0, 0.0, w, h)) }
    }
}

internal fun NSData.paraBytes(): ByteArray {
    val tamanho = length.toInt()
    val bytes = ByteArray(tamanho)
    if (tamanho > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
