package br.com.selebi.app.nucleo.dados

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** Tem internet agora? Vira a faixa "Sem conexão" no topo das telas. */
@Singleton
class Conectividade @Inject constructor(@ApplicationContext private val contexto: Context) {

    val online: Flow<Boolean> = callbackFlow {
        val gerente = contexto.getSystemService(ConnectivityManager::class.java)

        fun agora(): Boolean = gerente.activeNetwork
            ?.let(gerente::getNetworkCapabilities)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val retorno = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            // Perder uma rede não é ficar sem internet: pode ter trocado o
            // Wi-Fi pelo 4G. Confere a rede que ficou.
            override fun onLost(network: Network) {
                trySend(agora())
            }

            override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                trySend(capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        trySend(agora())
        gerente.registerDefaultNetworkCallback(retorno)
        awaitClose { gerente.unregisterNetworkCallback(retorno) }
    }.distinctUntilChanged().conflate()
}
