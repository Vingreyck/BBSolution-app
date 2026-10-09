package br.com.selebi.app.funcionalidade.ordens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import br.com.selebi.app.nucleo.modelo.Ordem

/**
 * Ligar, traçar rota, chamar no WhatsApp — sai do app para o app certo.
 *
 * Nada de checar antes se existe app que abre (`resolveActivity`): no Android
 * 11+ essa checagem falha mesmo quando o app existe, e foi assim que o SeeNet
 * ficou com "Não foi possível abrir o discador". Tenta abrir e trata a falha.
 */
object AcoesOs {

    /** Coordenada da usina quando existe (chega na porta); senão, o endereço. */
    fun abrirRota(contexto: Context, ordem: Ordem): Boolean {
        val usina = ordem.usina
        val destino = if (usina?.latitude != null && usina.longitude != null) {
            "${usina.latitude},${usina.longitude}"
        } else {
            // Texto puro para o mapa: "Rua X, 12, Centro, Itabaiana, SE".
            listOfNotNull(ordem.cliente.endereco?.replace(" · ", ", "), ordem.cliente.cidade, ordem.cliente.uf)
                .joinToString(", ")
        }
        if (destino.isBlank()) return false

        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destino)))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(destino)),
        )
        return abrir(contexto, geo) || abrir(contexto, web)
    }

    /** Abre o discador com o número pronto — sem permissão de ligação. */
    fun ligar(contexto: Context, telefone: String): Boolean =
        abrir(contexto, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefone")))

    /** Abre a conversa com o cliente; com `texto`, a mensagem já vem escrita — é só enviar. */
    fun whatsapp(contexto: Context, telefone: String, texto: String? = null): Boolean {
        val numero = if (telefone.length in 10..11) "55$telefone" else telefone
        val endereco = "https://wa.me/$numero" + (texto?.let { "?text=" + Uri.encode(it) } ?: "")
        return abrir(contexto, Intent(Intent.ACTION_VIEW, Uri.parse(endereco)))
    }

    /** A folha de compartilhar do Android: WhatsApp de outra pessoa, e-mail, o que for. */
    fun compartilhar(contexto: Context, texto: String, titulo: String): Boolean {
        val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
        return abrir(contexto, Intent.createChooser(envio, titulo))
    }

    fun abrirLink(contexto: Context, url: String): Boolean =
        abrir(contexto, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun abrir(contexto: Context, intent: Intent): Boolean = try {
        contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
