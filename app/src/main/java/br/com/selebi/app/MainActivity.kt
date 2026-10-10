package br.com.selebi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import br.com.selebi.app.navegacao.AppSelebi
import br.com.selebi.app.nucleo.dados.EstadoSessao

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val abertura = installSplashScreen()
        super.onCreate(savedInstanceState)
        val nucleo = (application as SelebiApp).nucleo

        // A tela de abertura segura só enquanto a sessão guardada é lida do
        // disco — dezenas de milissegundos, sem rede nenhuma.
        abertura.setKeepOnScreenCondition { nucleo.sessao.estado.value is EstadoSessao.Carregando }

        enableEdgeToEdge()
        setContent { AppSelebi(nucleo) }
    }
}
