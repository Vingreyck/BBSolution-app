@file:OptIn(ExperimentalForeignApi::class)

package br.com.selebi.app.nucleo.dados

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSinceNow
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * A [Localizacao] do iPhone: o CoreLocation, pedido uma vez na hora da ação.
 * O `CLLocationManager` só funciona na thread principal — tudo aqui vai para lá.
 */
class LocalizacaoIos : Localizacao {

    override fun temPermissao(): Boolean = autorizado(CLLocationManager().authorizationStatus)

    override suspend fun atual(limiteMs: Long): Ponto? = withContext(Dispatchers.Main) {
        if (!temPermissao()) return@withContext null
        withTimeoutOrNull(limiteMs) { pedirUma() } ?: ultima()
    }

    private suspend fun pedirUma(): Ponto? = suspendCancellableCoroutine { cont ->
        val gerente = CLLocationManager()
        var respondeu = false
        val delegado = DelegadoUmaVez { local ->
            if (!respondeu) {
                respondeu = true
                gerente.stopUpdatingLocation()
                if (cont.isActive) cont.resume(local?.let(::ponto))
            }
        }
        gerente.delegate = delegado
        gerente.desiredAccuracy = kCLLocationAccuracyBest
        gerente.requestLocation()
        // O gerente guarda o delegado só de leve: a referência daqui o mantém vivo.
        cont.invokeOnCancellation {
            respondeu = true
            gerente.stopUpdatingLocation()
            gerente.delegate = null
            delegado.hashCode()
        }
    }

    /** A última posição conhecida, se for de até 10 minutos atrás. */
    private fun ultima(): Ponto? {
        val local = CLLocationManager().location ?: return null
        if (-local.timestamp.timeIntervalSinceNow() > 600.0) return null
        return ponto(local)
    }
}

internal fun autorizado(situacao: CLAuthorizationStatus): Boolean =
    situacao == kCLAuthorizationStatusAuthorizedWhenInUse || situacao == kCLAuthorizationStatusAuthorizedAlways

internal fun ponto(local: CLLocation): Ponto {
    val (latitude, longitude) = local.coordinate.useContents { latitude to longitude }
    return Ponto(latitude, longitude, local.horizontalAccuracy.takeIf { it > 0 })
}

private class DelegadoUmaVez(private val aoTerminar: (CLLocation?) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        aoTerminar(didUpdateLocations.lastOrNull() as? CLLocation)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        aoTerminar(null)
    }
}
