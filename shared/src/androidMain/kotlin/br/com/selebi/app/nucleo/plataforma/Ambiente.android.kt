package br.com.selebi.app.nucleo.plataforma

import android.os.Build

actual val nomeDoSistema: String = "Android"

actual fun versaoDoSistema(): String = Build.VERSION.RELEASE.orEmpty()

actual fun descricaoDoDispositivo(): String {
    val fabricante = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
    val modelo = Build.MODEL.orEmpty()
    val nome = if (modelo.startsWith(fabricante, ignoreCase = true)) modelo else "$fabricante $modelo"
    return "${nome.trim()} · Android ${Build.VERSION.RELEASE}"
}
