package br.com.selebi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import br.com.selebi.app.navegacao.AppSelebi
import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.push.EXTRA_OS
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pedirPermissaoDeAviso = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        val abertura = installSplashScreen()
        super.onCreate(savedInstanceState)
        val nucleo = (application as SelebiApp).nucleo

        // A tela de abertura segura só enquanto a sessão guardada é lida do
        // disco — dezenas de milissegundos, sem rede nenhuma.
        abertura.setKeepOnScreenCondition { nucleo.sessao.estado.value is EstadoSessao.Carregando }

        enableEdgeToEdge()
        setContent { AppSelebi(nucleo) }

        abrirOsDoAviso(intent)
        prepararAvisos()
    }

    /** Tocou no aviso com o app já aberto: a tela atual vira a da OS. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        abrirOsDoAviso(intent)
    }

    /** O aviso leva o id da OS: o do sistema (app fechado) e o nosso (app aberto) usam a mesma chave. */
    private fun abrirOsDoAviso(intent: Intent?) {
        (application as SelebiApp).nucleo.push.abrirOs(intent?.getStringExtra(EXTRA_OS))
    }

    /**
     * Pega o token do Firebase e, depois do login, pede a permissão de aviso
     * (Android 13+). Pedir antes do login seria pedir a quem ainda nem sabe o
     * que o app faz.
     */
    private fun prepararAvisos() {
        val nucleo = (application as SelebiApp).nucleo
        FirebaseMessaging.getInstance().token.addOnSuccessListener { nucleo.push.definirToken(it) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            nucleo.sessao.estado.first { it is EstadoSessao.Logado }
            val ja = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
            if (ja != PackageManager.PERMISSION_GRANTED) pedirPermissaoDeAviso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
