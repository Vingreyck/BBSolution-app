package br.com.selebi.app.nucleo.dados

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Um ponto no mapa, com a margem de erro em metros. */
data class Ponto(val latitude: Double, val longitude: Double, val precisao: Double?)

/**
 * Onde o técnico está, na hora de uma ação — é o "no mapa" do histórico da OS
 * no site, como o IXC faz com o check-in.
 *
 * Nunca trava o trabalho: sem permissão, com o GPS desligado ou sem resposta
 * em alguns segundos, a ação segue sem o ponto. Usa o serviço de localização
 * do próprio Android, sem depender do Google Play.
 */
@Singleton
class Localizacao @Inject constructor(@ApplicationContext private val contexto: Context) {

    private val gerente: LocationManager? get() = contexto.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun temPermissao(): Boolean =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** O ponto de agora, ou o último conhecido de até 10 minutos atrás. */
    suspend fun atual(limiteMs: Long = 8_000): Ponto? {
        if (!temPermissao()) return null
        val gerente = gerente ?: return null
        val provedor = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { gerente.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        val agora = withTimeoutOrNull(limiteMs) { pedir(gerente, provedor) }
        return (agora ?: ultima(gerente))?.let { Ponto(it.latitude, it.longitude, it.accuracy.toDouble().takeIf { a -> a > 0 }) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun pedir(gerente: LocationManager, provedor: String): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancelar = CancellationSignal()
                    cont.invokeOnCancellation { cancelar.cancel() }
                    gerente.getCurrentLocation(provedor, cancelar, executor) { local ->
                        if (cont.isActive) cont.resume(local)
                    }
                } else {
                    // Antes do Android 11 não há "localização de agora" sem o Google Play.
                    cont.resume(null)
                }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (_: IllegalArgumentException) {
                if (cont.isActive) cont.resume(null)
            }
        }

    @SuppressLint("MissingPermission")
    private fun ultima(gerente: LocationManager): Location? = runCatching {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { gerente.getLastKnownLocation(it) }
            .filter { System.currentTimeMillis() - it.time < 10 * 60_000 }
            .minByOrNull { it.accuracy }
    }.getOrNull()
}
