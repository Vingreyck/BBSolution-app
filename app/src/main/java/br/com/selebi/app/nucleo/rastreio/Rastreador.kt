package br.com.selebi.app.nucleo.rastreio

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.selebi.app.nucleo.dados.Localizacao
import br.com.selebi.app.nucleo.dados.Ponto
import br.com.selebi.app.nucleo.modelo.ModoRastreio
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.Usuario
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Quem está sendo rastreado, em qual OS, e como. Sobrevive a o Android matar o app. */
data class EstadoRastreio(
    val usuarioId: String,
    val ordemId: String,
    val numero: Int,
    val cliente: String,
    val modo: ModoRastreio,
)

/**
 * Liga e desliga o GPS do técnico conforme a OS — a regra do SeeNet:
 *
 * - **"Estou a caminho"** liga, no modo forte (a cada 5 s).
 * - **"Cheguei"** (ou "Retomar") passa para o modo econômico: o escritório
 *   continua vendo o técnico no local até o fim, gastando pouca bateria.
 * - **Concluir, pausar ou "não consegui atender"** desliga.
 *
 * Fora disso, ninguém é rastreado. E rastreamento é sempre visível: o serviço
 * roda com notificação fixa ("Em campo · OS #0049") enquanto estiver ligado.
 */
@Singleton
class Rastreador @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val memoria: MemoriaRastreio,
    private val localizacao: Localizacao,
) {
    private val _ativo = MutableStateFlow(memoria.ler())
    val ativo: StateFlow<EstadoRastreio?> = _ativo.asStateFlow()

    /**
     * Liga (ou troca o modo). `primeiro` é o ponto que a ação acabou de pegar:
     * vai já, para o escritório não ficar em "esperando o GPS" — a lição do
     * SeeNet, onde o primeiro ponto do fluxo demorava uns 10 s.
     */
    fun iniciar(estado: EstadoRastreio, primeiro: Ponto? = null) {
        // Sem permissão o serviço nem sobe: o Android 14 derruba serviço de
        // localização sem ela. A ação da OS segue normal, só sem o mapa.
        if (!localizacao.temPermissao()) return
        memoria.salvar(estado)
        _ativo.value = estado
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

    /** Desliga. Com `ordemId`, só se for esta OS que está sendo rastreada. */
    fun parar(ordemId: String? = null) {
        val atual = memoria.ler() ?: return
        if (ordemId != null && atual.ordemId != ordemId) return
        memoria.apagar()
        _ativo.value = null
        try {
            contexto.startService(Intent(contexto, ServicoRastreamento::class.java).setAction(ServicoRastreamento.ACAO_PARAR))
        } catch (_: Exception) {
            // O serviço confere a memória a cada leitura do GPS e para sozinho.
        }
    }

    /**
     * Confere contra as OS do celular, depois de cada sincronização e ao abrir
     * o app: retoma o rastreamento que o Android interrompeu, troca o modo se a
     * OS mudou (o escritório pode ter mexido), e desliga se a OS saiu de campo
     * ou foi para outra pessoa.
     */
    fun conferir(ordens: List<Ordem>, usuario: Usuario?) {
        if (usuario == null) {
            parar()
            return
        }
        val atual = memoria.ler()
        if (atual != null && atual.usuarioId != usuario.id) parar()

        val emCampo = ordens.filter { it.responsavel?.id == usuario.id && ModoRastreio.para(it.status) != null }
        val rastreada = memoria.ler()?.let { r -> emCampo.firstOrNull { it.id == r.ordemId } }
        // A caminho vem antes de atendendo: é o deslocamento que o escritório quer ver.
        val escolhida = rastreada
            ?: emCampo.firstOrNull { it.status == StatusOs.EM_DESLOCAMENTO }
            ?: emCampo.firstOrNull()

        if (escolhida == null) {
            parar()
            return
        }
        iniciar(
            EstadoRastreio(
                usuarioId = usuario.id,
                ordemId = escolhida.id,
                numero = escolhida.numero,
                cliente = escolhida.cliente.nome,
                modo = ModoRastreio.para(escolhida.status)!!,
            ),
        )
    }
}
