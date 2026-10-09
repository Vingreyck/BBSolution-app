package br.com.selebi.app.nucleo.design.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.arquivos.CarregadorMiniatura
import br.com.selebi.app.nucleo.modelo.AnexoOs

/** O carregador de fotos da tela. Sem ninguém fornecer (Preview, captura), mostra o ícone. */
val LocalCarregadorMiniatura = staticCompositionLocalOf { CarregadorMiniatura { _, _, _ -> null } }

/**
 * Uma foto da OS, recortada no espaço que tiver. Enquanto carrega, ou sem
 * sinal para baixar, um ícone no lugar — nunca um buraco.
 */
@Composable
fun MiniaturaAnexo(
    ordemId: String,
    anexo: AnexoOs,
    modifier: Modifier = Modifier,
    lado: Int = 360,
    ajuste: ContentScale = ContentScale.Crop,
) {
    val carregador = LocalCarregadorMiniatura.current
    val imagem: ImageBitmap? by produceState<ImageBitmap?>(null, anexo.id, anexo.arquivoLocal, lado) {
        value = carregador.carregar(ordemId, anexo, lado)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val atual = imagem
        if (atual != null) {
            Image(atual, contentDescription = null, contentScale = ajuste, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                if (anexo.categoria == "assinatura") Icons.Outlined.Draw else Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
