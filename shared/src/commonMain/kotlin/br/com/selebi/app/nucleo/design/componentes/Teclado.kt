package br.com.selebi.app.nucleo.design.componentes

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Tocar fora da caixa de texto fecha o teclado.
 *
 * No Android o botão "voltar" já fecha o teclado. No iPhone não existe botão
 * "voltar", e o teclado numérico nem tem tecla de "OK": sem isto, quem abriu a
 * caixa não tinha como fechar. Vale para o espaço vazio da tela; tocar em outro
 * campo ou botão continua funcionando normalmente, porque eles tratam o toque
 * primeiro.
 */
@Composable
fun Modifier.fecharTecladoAoTocarFora(): Modifier {
    val foco = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    return pointerInput(foco, teclado) {
        detectTapGestures(
            onTap = {
                foco.clearFocus()
                teclado?.hide()
            },
        )
    }
}
