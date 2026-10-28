package br.com.selebi.app.nucleo.plataforma

import platform.UIKit.UIDevice

actual val nomeDoSistema: String = "iOS"

actual val enviaComOAppFechado: Boolean = false

actual fun versaoDoSistema(): String = UIDevice.currentDevice.systemVersion

/** "iPhone · iOS 18.2" — o iOS não conta o modelo exato sem permissão especial. */
actual fun descricaoDoDispositivo(): String =
    "${UIDevice.currentDevice.model} · iOS ${UIDevice.currentDevice.systemVersion}"
