package br.com.selebi.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import br.com.selebi.app.MainActivity
import br.com.selebi.app.R
import br.com.selebi.app.SelebiApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/** O canal das notificações de OS: importância alta, para aparecer na tela e tocar. */
const val CANAL_ORDENS = "ordens"

/** A chave que leva o id da OS do aviso até a [MainActivity]. */
const val EXTRA_OS = "osId"

fun criarCanalDeOrdens(contexto: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val canal = NotificationChannel(CANAL_ORDENS, "Ordens de serviço", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Avisa quando uma OS é atribuída, reagendada ou cancelada."
    }
    contexto.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
}

/**
 * O Firebase entrega aqui o token do celular e os avisos que chegam com o app
 * aberto. Com o app fechado quem mostra o aviso é o próprio sistema (a mensagem
 * traz título e texto), e tocar nele abre a [MainActivity] com o `osId`.
 */
class ServicoAvisos : FirebaseMessagingService() {

    private val nucleo get() = (application as SelebiApp).nucleo

    override fun onNewToken(token: String) {
        nucleo.push.definirToken(token)
    }

    override fun onMessageReceived(mensagem: RemoteMessage) {
        val osId = mensagem.data[EXTRA_OS]
        // A lista já atualizada: o aviso e a tela dizem a mesma coisa.
        nucleo.escopo.launch { nucleo.push.aoReceberComAppAberto() }

        val titulo = mensagem.notification?.title ?: return
        val permitido = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permitido) return

        val abrir = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OS, osId)
        val toque = PendingIntent.getActivity(
            this,
            osId.hashCode(),
            abrir,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val aviso = NotificationCompat.Builder(this, CANAL_ORDENS)
            .setSmallIcon(R.drawable.ic_notificacao_campo)
            .setContentTitle(titulo)
            .setContentText(mensagem.notification?.body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(toque)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(osId.hashCode(), aviso)
    }
}
