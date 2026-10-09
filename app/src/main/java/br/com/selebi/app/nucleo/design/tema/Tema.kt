package br.com.selebi.app.nucleo.design.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tipografia: a Roboto do sistema, que é a mesma fonte que o Selebi web usa no
 * Android (`system-ui`). O que muda do padrão do Material é peso e tamanho do
 * corpo — texto lido ao sol, com o celular na mão, pede 16sp e títulos firmes.
 */
private val base = Typography()

val TipografiaSelebi = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold),
)

/** Cantos arredondados, mas sem virar bolha: cartão com 16dp, folha de baixo com 28dp. */
val FormasSelebi = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SelebiTema(
    escuro: Boolean = isSystemInDarkTheme(),
    conteudo: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCoresSelebi provides if (escuro) CoresEscuras else CoresClaras) {
        MaterialTheme(
            colorScheme = if (escuro) EsquemaEscuro else EsquemaClaro,
            typography = TipografiaSelebi,
            shapes = FormasSelebi,
            content = conteudo,
        )
    }
}

/** `SelebiTema.cores.perigo` — o mesmo jeito de `MaterialTheme.colorScheme.primary`. */
object SelebiTema {
    val cores: CoresSelebi
        @Composable
        @ReadOnlyComposable
        get() = LocalCoresSelebi.current
}
