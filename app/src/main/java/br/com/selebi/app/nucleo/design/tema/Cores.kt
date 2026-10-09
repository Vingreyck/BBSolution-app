package br.com.selebi.app.nucleo.design.tema

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * As cores do Selebi, tiradas do `globals.css` do sistema web — o app e o
 * navegador são o mesmo produto e têm que parecer o mesmo produto.
 *
 * Cobre (#a8521f no claro, #e08a4a no escuro) é a cor de ação. O resto é
 * cinza-azulado neutro, para o cobre e as cores de situação (verde, âmbar,
 * vermelho) serem as únicas coisas que chamam o olho. Sem cor dinâmica do
 * Android 12: com ela, o app mudaria de cor conforme o papel de parede de
 * cada técnico.
 */

val EsquemaClaro = lightColorScheme(
    primary = Color(0xFFA8521F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF3A1703),
    secondary = Color(0xFF6E5A4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E1D6),
    onSecondaryContainer = Color(0xFF271910),
    tertiary = Color(0xFF2F5D8A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF001C38),
    error = Color(0xFF9B2C2C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDCDA),
    onErrorContainer = Color(0xFF3D0707),
    background = Color(0xFFF2F4F6),
    onBackground = Color(0xFF12171C),
    surface = Color(0xFFF2F4F6),
    onSurface = Color(0xFF12171C),
    surfaceVariant = Color(0xFFE3E7EB),
    onSurfaceVariant = Color(0xFF4B5560),
    outline = Color(0xFF6E7A85),
    outlineVariant = Color(0xFFC6CDD4),
    inverseSurface = Color(0xFF2B3137),
    inverseOnSurface = Color(0xFFEEF1F4),
    inversePrimary = Color(0xFFFFB68D),
    surfaceBright = Color(0xFFF7F8FA),
    surfaceDim = Color(0xFFD8DDE2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFEDF0F3),
    surfaceContainerHigh = Color(0xFFE7EAEE),
    surfaceContainerHighest = Color(0xFFE1E5E9),
)

val EsquemaEscuro = darkColorScheme(
    primary = Color(0xFFE08A4A),
    onPrimary = Color(0xFF3A1703),
    primaryContainer = Color(0xFF6E3413),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFDBC1B3),
    onSecondary = Color(0xFF3D2C22),
    secondaryContainer = Color(0xFF4E3C31),
    onSecondaryContainer = Color(0xFFF4E1D6),
    tertiary = Color(0xFF9FC9F5),
    onTertiary = Color(0xFF00325A),
    tertiaryContainer = Color(0xFF15466F),
    onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFE5736F),
    onError = Color(0xFF4A0808),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFBDCDA),
    background = Color(0xFF11151A),
    onBackground = Color(0xFFE7EBEF),
    surface = Color(0xFF11151A),
    onSurface = Color(0xFFE7EBEF),
    surfaceVariant = Color(0xFF2A323B),
    onSurfaceVariant = Color(0xFFA5B0BA),
    outline = Color(0xFF7C8894),
    outlineVariant = Color(0xFF323B45),
    inverseSurface = Color(0xFFE7EBEF),
    inverseOnSurface = Color(0xFF2B3137),
    inversePrimary = Color(0xFFA8521F),
    surfaceBright = Color(0xFF30373F),
    surfaceDim = Color(0xFF11151A),
    surfaceContainerLowest = Color(0xFF0C1014),
    surfaceContainerLow = Color(0xFF161B21),
    surfaceContainer = Color(0xFF191E25),
    surfaceContainerHigh = Color(0xFF20272F),
    surfaceContainerHighest = Color(0xFF2A323B),
)

/**
 * Cores que o Material 3 não tem: as de situação.
 *
 * Cada uma em três tons — a cor forte (ícone, número), o fundo suave
 * ("container") e o texto que vai em cima do fundo — para chip, faixa e card
 * usarem sempre a mesma combinação, com contraste garantido.
 */
@Immutable
data class CoresSelebi(
    val sucesso: Color,
    val sucessoFundo: Color,
    val sobreSucesso: Color,
    val alerta: Color,
    val alertaFundo: Color,
    val sobreAlerta: Color,
    val perigo: Color,
    val perigoFundo: Color,
    val sobrePerigo: Color,
    val info: Color,
    val infoFundo: Color,
    val sobreInfo: Color,
    val neutroFundo: Color,
    val sobreNeutro: Color,
)

val CoresClaras = CoresSelebi(
    sucesso = Color(0xFF2C6E49),
    sucessoFundo = Color(0xFFD5EEDD),
    sobreSucesso = Color(0xFF0B2A18),
    alerta = Color(0xFF7E6000),
    alertaFundo = Color(0xFFFBEAB5),
    sobreAlerta = Color(0xFF2A2000),
    perigo = Color(0xFF9B2C2C),
    perigoFundo = Color(0xFFFBDCDA),
    sobrePerigo = Color(0xFF3D0707),
    info = Color(0xFF2F5D8A),
    infoFundo = Color(0xFFDCE8F6),
    sobreInfo = Color(0xFF0A2440),
    neutroFundo = Color(0xFFE3E7EB),
    sobreNeutro = Color(0xFF33404C),
)

val CoresEscuras = CoresSelebi(
    sucesso = Color(0xFF5FBF8B),
    sucessoFundo = Color(0xFF16402A),
    sobreSucesso = Color(0xFFCDEFD9),
    alerta = Color(0xFFD6AC3F),
    alertaFundo = Color(0xFF4A3A07),
    sobreAlerta = Color(0xFFF7E6B5),
    perigo = Color(0xFFE5736F),
    perigoFundo = Color(0xFF5A1A1A),
    sobrePerigo = Color(0xFFFBDCDA),
    info = Color(0xFF7FB0E0),
    infoFundo = Color(0xFF16324F),
    sobreInfo = Color(0xFFDCE8F6),
    neutroFundo = Color(0xFF2A323B),
    sobreNeutro = Color(0xFFC9D1D9),
)

val LocalCoresSelebi = staticCompositionLocalOf { CoresClaras }
