package br.com.selebi.app.nucleo.rastreio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.selebi.app.nucleo.dados.Ponto

/** O [MotorRastreio] do Android: liga e desliga o [ServicoRastreamento]. */
class MotorRastreioAndroid(private val contexto: Context) : MotorRastreio {

    override fun ligar(estado: EstadoRastreio, primeiro: Ponto?) {
        val intent = Intent(contexto, ServicoRastreamento::class.java).setAction(ServicoRastreamento.ACAO_INICIAR)
        if (primeiro != null) {
            intent.putExtra(ServicoRastreamento.EXTRA_LATITUDE, primeiro.latitude)
            intent.putExtra(ServicoRastreamento.EXTRA_LONGITUDE, primeiro.longitude)
            primeiro.precisao?.let { intent.putExtra(ServicoRastreamento.EXTRA_PRECISAO, it) }
        }
        try {
            ContextCompat.startForegroundService(contexto, intent)
        } catch (e: Exception) {
            // Android 12+ não deixa iniciar de segundo plano. Sempre chamamos com
            // o app aberto; se não der, a próxima conferência tenta de novo.
            Log.w("Rastreador", "Não deu para iniciar o rastreamento agora.", e)
        }
    }

    override fun desligar() {
        try {
            contexto.startService(Intent(contexto, ServicoRastreamento::class.java).setAction(ServicoRastreamento.ACAO_PARAR))
        } catch (_: Exception) {
            // O serviço confere a memória a cada leitura do GPS e para sozinho.
        }
    }
}
