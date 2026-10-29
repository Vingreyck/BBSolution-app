package br.com.selebi.app

import android.app.Application
import androidx.work.Configuration
import br.com.selebi.app.nucleo.Nucleo
import br.com.selebi.app.nucleo.dados.AgendadorWorkManager
import br.com.selebi.app.nucleo.diagnostico.RegistroDeFalhas
import br.com.selebi.app.nucleo.montarNucleoAndroid
import br.com.selebi.app.nucleo.rede.ModuloRede
import br.com.selebi.app.nucleo.plataforma.Ambiente
import br.com.selebi.app.nucleo.rastreio.MotorRastreioAndroid
import br.com.selebi.app.push.criarCanalDeOrdens

/**
 * O app no Android: monta o [Nucleo] — o mesmo do iPhone — com as peças
 * daqui: o WorkManager para subir a fila e o serviço de GPS em primeiro plano.
 *
 * Montado na primeira vez que alguém pede (a tela, o envio em segundo plano, o
 * serviço de GPS), e não na abertura: nos testes, o app sobe sem abrir banco.
 *
 * O WorkManager é iniciado sob demanda por aqui (`Configuration.Provider`); o
 * inicializador automático sai do manifesto.
 */
class SelebiApp : Application(), Configuration.Provider {

    val nucleo: Nucleo by lazy {
        Ambiente.apiUrl = BuildConfig.API_URL
        Ambiente.versao = BuildConfig.VERSION_NAME
        Ambiente.build = BuildConfig.VERSION_CODE.toString()
        montarNucleoAndroid(this, agendador = { AgendadorWorkManager(this, it.escopo) }, motor = MotorRastreioAndroid(this))
    }

    override fun onCreate() {
        super.onCreate()
        Ambiente.versao = BuildConfig.VERSION_NAME
        Ambiente.build = BuildConfig.VERSION_CODE.toString()
        criarCanalDeOrdens(this)
        // Travou? Grava a falha e deixa o Android fazer o que faria — a próxima
        // abertura conta ao servidor (ver `RegistroDeFalhas`).
        val registro = RegistroDeFalhas(filesDir.path, ModuloRede.json())
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            registro.gravar(erro)
            anterior?.uncaughtException(thread, erro)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
