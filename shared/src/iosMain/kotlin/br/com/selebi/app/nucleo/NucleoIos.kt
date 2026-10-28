package br.com.selebi.app.nucleo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.selebi.app.nucleo.arquivos.ArquivosOs
import br.com.selebi.app.nucleo.arquivos.PastasApp
import br.com.selebi.app.nucleo.arquivos.ProcessadorFotosIos
import br.com.selebi.app.nucleo.banco.criarBanco
import br.com.selebi.app.nucleo.dados.AgendadorIos
import br.com.selebi.app.nucleo.dados.CofreSessaoIos
import br.com.selebi.app.nucleo.dados.ConectividadeIos
import br.com.selebi.app.nucleo.dados.LocalizacaoIos
import br.com.selebi.app.nucleo.dados.PreferenciasApp
import br.com.selebi.app.nucleo.plataforma.Ambiente
import br.com.selebi.app.nucleo.plataforma.pastaCapturas
import br.com.selebi.app.nucleo.rastreio.MemoriaRastreioIos
import br.com.selebi.app.nucleo.rastreio.MotorRastreioIos
import br.com.selebi.app.nucleo.rede.ModuloRede
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * O núcleo com as peças do iPhone: Keychain, CoreLocation, `NWPathMonitor`,
 * o SQLite que vai dentro do app, e a fila subindo pelo próprio app.
 *
 * Os dados ficam em "Application Support" — a pasta que o iOS guarda no
 * backup e não apaga sozinho; as cópias baixadas, em "Caches".
 */
fun montarNucleoIos(): Nucleo {
    Ambiente.versao = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: Ambiente.versao
    Ambiente.build = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: ""

    val dados = pasta(NSApplicationSupportDirectory) + "/selebi"
    val cache = pasta(NSCachesDirectory) + "/selebi"
    listOf(dados, cache).forEach {
        NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
    }

    val memoria = MemoriaRastreioIos()
    val motor = MotorRastreioIos(memoria)
    var agendador: AgendadorIos? = null
    val nucleo = Nucleo(
        banco = criarBanco("$dados/selebi.db"),
        cofre = CofreSessaoIos(ModuloRede.json()),
        preferencias = PreferenciasApp(
            PreferenceDataStoreFactory.createWithPath(produceFile = { "$dados/preferencias.preferences_pb".toPath() }),
        ),
        conectividade = ConectividadeIos(),
        localizacao = LocalizacaoIos(),
        arquivos = ArquivosOs(PastasApp(dados, cache), ProcessadorFotosIos(pastaCapturas())),
        memoriaRastreio = memoria,
        motorRastreio = motor,
        agendador = { n -> AgendadorIos(n).also { agendador = it } },
    )
    motor.conectar(nucleo)
    agendador?.iniciar()

    // Voltou para o app ou saiu dele: o que estiver na fila tenta subir.
    val avisos = NSNotificationCenter.defaultCenter
    listOf(UIApplicationDidBecomeActiveNotification, UIApplicationDidEnterBackgroundNotification).forEach { nome ->
        avisos.addObserverForName(nome, `object` = null, queue = NSOperationQueue.mainQueue) { _ -> nucleo.agendador.agendar() }
    }
    return nucleo
}

private fun pasta(qual: ULong): String =
    (NSSearchPathForDirectoriesInDomains(qual, NSUserDomainMask, true).firstOrNull() as? String)
        ?: error("O iOS não deu a pasta $qual do app.")
