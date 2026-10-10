package br.com.selebi.app.nucleo.arquivos

import androidx.compose.ui.graphics.ImageBitmap
import br.com.selebi.app.nucleo.modelo.AnexoOs
import br.com.selebi.app.nucleo.rede.SelebiApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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
 *
 * Usado pelas telas, sempre na thread principal: a memória não precisa de trava.
 */
class Miniaturas(
    private val api: SelebiApi,
    private val arquivos: ArquivosOs,
) : CarregadorMiniatura {
    private val memoria = MemoriaRecente(TETO_MEMORIA)

    /** No máximo três downloads ao mesmo tempo: a lista de fotos não entope o 3G. */
    private val downloads = Semaphore(3)

    fun daMemoria(anexoId: String, lado: Int): ImageBitmap? = memoria["$anexoId@$lado"]

    override suspend fun carregar(ordemId: String, anexo: AnexoOs, lado: Int): ImageBitmap? {
        val chave = "${anexo.id}@$lado"
        memoria[chave]?.let { return it }
        val arquivo = anexo.arquivoLocal?.takeIf { arquivos.temConteudo(it) }
            ?: if (anexo.pendente) null else baixar(ordemId, anexo.id)
        val imagem = arquivo?.let { arquivos.fotos.miniatura(it, lado) } ?: return null
        memoria[chave] = imagem
        return imagem
    }

    private suspend fun baixar(ordemId: String, anexoId: String): String? {
        val destino = arquivos.copiaDoServidor(anexoId)
        if (arquivos.temConteudo(destino)) return destino
        return try {
            downloads.withPermit {
                val bytes = api.baixarAnexo(ordemId, anexoId)
                withContext(Dispatchers.IO) { if (arquivos.gravarCopia(destino, bytes)) destino else null }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sem sinal ou sem Drive: a tela mostra o ícone no lugar da foto.
            null
        }
    }

    private companion object {
        const val TETO_MEMORIA = 24 * 1024 * 1024
    }
}

/** As imagens usadas por último, até [teto] bytes; a mais antiga sai primeiro. */
private class MemoriaRecente(private val teto: Int) {
    private val itens = LinkedHashMap<String, ImageBitmap>()
    private var total = 0

    operator fun get(chave: String): ImageBitmap? {
        val imagem = itens.remove(chave) ?: return null
        itens[chave] = imagem // volta para o fim: usada agora
        return imagem
    }

    operator fun set(chave: String, imagem: ImageBitmap) {
        itens.remove(chave)?.let { total -= tamanho(it) }
        itens[chave] = imagem
        total += tamanho(imagem)
        while (total > teto && itens.size > 1) {
            val maisVelha = itens.keys.first()
            itens.remove(maisVelha)?.let { total -= tamanho(it) }
        }
    }

    private fun tamanho(imagem: ImageBitmap): Int = imagem.width * imagem.height * 4
}
