package br.com.selebi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.selebi.app.navegacao.RaizApp
import br.com.selebi.app.navegacao.RaizViewModel
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val raiz: RaizViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val abertura = installSplashScreen()
        super.onCreate(savedInstanceState)

        // A tela de abertura segura só enquanto a sessão guardada é lida do
        // disco — dezenas de milissegundos, sem rede nenhuma.
        abertura.setKeepOnScreenCondition { raiz.estado.value is EstadoSessao.Carregando }

        enableEdgeToEdge()
        setContent {
            SelebiTema {
                val estado by raiz.estado.collectAsStateWithLifecycle()
                RaizApp(estado)
            }
        }
    }
}
