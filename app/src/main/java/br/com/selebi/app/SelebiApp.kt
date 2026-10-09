package br.com.selebi.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Ponto de entrada do Hilt: daqui para baixo tudo recebe dependências injetadas.
 *
 * Também monta o WorkManager com a fábrica do Hilt — é o que deixa o envio da
 * fila receber o banco e a API injetados. Por isso o inicializador automático
 * do WorkManager sai do manifesto.
 */
@HiltAndroidApp
class SelebiApp : Application(), Configuration.Provider {

    @Inject
    lateinit var fabricaDeTrabalhos: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaDeTrabalhos).build()
}
