package br.com.selebi.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import br.com.selebi.app.navegacao.AppSelebi
import br.com.selebi.app.nucleo.Nucleo
import br.com.selebi.app.nucleo.montarNucleoIos
import platform.UIKit.UIViewController

/** Um só por app, como o `SelebiApp` do Android. A tela e os avisos (`AvisosIos`) usam o mesmo. */
internal val nucleoDoApp: Nucleo by lazy { montarNucleoIos() }

/**
 * A tela do app no iPhone — o mesmo `AppSelebi` do Android, dentro de uma
 * tela do UIKit. É o que o Swift do projeto do Xcode chama
 * (`MainViewControllerKt.MainViewController()`).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = {
        // Arrastar da borda esquerda volta, como em todo app do iPhone.
        enableBackGesture = true
    },
) {
    AppSelebi(nucleoDoApp)
}
