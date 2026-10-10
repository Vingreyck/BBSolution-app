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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * A [Localizacao] do Android: o serviço de localização do próprio sistema,
 * sem depender do Google Play.
 */
class LocalizacaoAndroid(private val contexto: Context) : Localizacao {

    private val gerente: LocationManager? get() = contexto.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    override fun temPermissao(): Boolean =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override suspend fun atual(limiteMs: Long): Ponto? {
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
