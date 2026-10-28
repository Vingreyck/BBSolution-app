package br.com.selebi.app.funcionalidade.execucao

import br.com.selebi.app.nucleo.modelo.AcaoOs
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.EsperaEnvio
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.MotivoEspera
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TipoEnvio

/*
 * O roteiro da execução — o wizard sem tela, para ser testado em
 * milissegundos.
 *
 *   Chegada → um passo por item do checklist → Conclusão
 *
 * Um item por tela, e não o formulário inteiro: no telhado, com uma mão no
 * celular, é o que cabe — a pergunta, o botão da câmera e o "próximo", todos
 * grandes. O técnico anda para a frente e para trás à vontade, pula o que vai
 * fazer depois, e a Conclusão lista o que ficou faltando. Nada trava no meio:
 * quem trava é a conclusão, como no servidor.
 */

sealed interface Passo {
    val chave: String

    data object Chegada : Passo {
        override val chave = "chegada"
    }

    /** `numero` conta de 1; `total` é o tamanho do checklist. */
    data class Item(val itemId: String, val numero: Int, val total: Int) : Passo {
        override val chave = "item-$itemId"
    }

    data object Conclusao : Passo {
        override val chave = "conclusao"
    }
}

object Roteiro {

    fun passos(ordem: Ordem): List<Passo> = buildList {
        add(Passo.Chegada)
        ordem.checklist.forEachIndexed { i, item -> add(Passo.Item(item.id, i + 1, ordem.checklist.size)) }
        add(Passo.Conclusao)
    }

    /** O checklist só recebe resposta com o atendimento em curso — é o que mantém o tempo em campo honesto. */
    fun podeResponder(ordem: Ordem): Boolean = ordem.status == StatusOs.EM_ANDAMENTO

    /**
     * Onde o wizard abre: no item pedido (o técnico tocou nele no detalhe);
     * em atendimento, no primeiro obrigatório que falta; sem nada faltando, na
     * conclusão; fora de atendimento, na chegada.
     */
    fun passoInicial(ordem: Ordem, itemId: String? = null): Int {
        val passos = passos(ordem)
        if (itemId != null) {
            val i = passos.indexOfFirst { it is Passo.Item && it.itemId == itemId }
            if (i >= 0) return i
        }
        if (!podeResponder(ordem)) return 0
        val falta = ordem.checklist.indexOfFirst { it.obrigatorio && !it.concluido }
            .takeIf { it >= 0 }
            ?: ordem.checklist.indexOfFirst { !it.concluido && it.valor == null && ordem.fotosDo(it.id).isEmpty() }
        return if (falta >= 0) falta + 1 else passos.lastIndex
    }

    /**
     * O próximo obrigatório pendente depois do passo `depoisDe`, dando a volta
     * no checklist. Nulo quando não falta nenhum.
     */
    fun proximoPendente(ordem: Ordem, depoisDe: Int): Int? {
        val itens = ordem.checklist
        if (itens.isEmpty()) return null
        val inicio = (depoisDe - 1).coerceIn(-1, itens.lastIndex) // passo → índice do item
        for (k in 1..itens.size) {
            val i = (inicio + k).mod(itens.size)
            if (itens[i].obrigatorio && !itens[i].concluido) return i + 1
        }
        return null
    }

    /** O checklist agrupado por seção, com o passo de cada item — o índice do wizard. */
    fun secoes(ordem: Ordem): List<Pair<String?, List<Pair<Int, ItemChecklist>>>> {
        val grupos = mutableListOf<Pair<String?, MutableList<Pair<Int, ItemChecklist>>>>()
        ordem.checklist.forEachIndexed { i, item ->
            val ultimo = grupos.lastOrNull()
            if (ultimo != null && ultimo.first == item.secao) {
                ultimo.second.add(i + 1 to item)
            } else {
                grupos.add(item.secao to mutableListOf(i + 1 to item))
            }
        }
        return grupos
    }

    /** "Item 5 de 17 · Padrão de entrada". */
    fun rotulo(passo: Passo, ordem: Ordem): String = when (passo) {
        Passo.Chegada -> "Chegada"
        Passo.Conclusao -> "Revisão e conclusão"
        is Passo.Item -> {
            val secao = ordem.checklist.firstOrNull { it.id == passo.itemId }?.secao
            "Item ${passo.numero} de ${passo.total}" + (secao?.let { " · $it" } ?: "")
        }
    }

    /**
     * O que dizer no alto da folha da fila: o que está acontecendo e o que fazer.
     *
     * O texto muda com o sistema: só o Android sobe com o app fechado, então só
     * ele pode dizer "não precisa esperar". No iPhone o técnico precisa saber
     * que o app tem que estar aberto.
     */
    fun resumoDaFila(envios: List<Envio>, online: Boolean, espera: EsperaEnvio?, sobeComAppFechado: Boolean): String {
        val esperando = envios.count { !it.recusado }
        val guardado = "Está tudo guardado aqui"
        val sozinho = if (sobeComAppFechado) "" else " Deixe o BBSolution aberto."
        return when {
            envios.isEmpty() -> "Tudo o que você fez nesta OS já está no BBSolution."
            esperando == 0 -> "O servidor recusou o que está abaixo. Veja o motivo e descarte."
            !online -> if (sobeComAppFechado) {
                "Sem sinal agora. $guardado e sobe sozinho quando a conexão voltar — mesmo com o app fechado."
            } else {
                "Sem sinal agora. $guardado e sobe quando a conexão voltar. Abra o BBSolution quando tiver sinal."
            }
            espera?.motivo == MotivoEspera.APP_DESATUALIZADO ->
                "Esta versão do BBSolution está desatualizada. Atualize o aplicativo na loja: $guardado e sobe depois da atualização."
            espera?.motivo == MotivoEspera.SERVIDOR ->
                "O servidor está com problema" + (espera.codigo?.let { " (erro $it)" } ?: "") +
                    ". $guardado e o app tenta de novo sozinho.$sozinho"
            espera?.motivo == MotivoEspera.SEM_SINAL ->
                "O sinal está fraco e o envio não completou. $guardado e o app tenta de novo sozinho.$sozinho"
            else -> if (sobeComAppFechado) "Subindo. Não precisa esperar com o app aberto." else "Subindo. Deixe o BBSolution aberto até terminar."
        }
    }

    /** Uma linha da fila em palavras: "Foto · Disjuntor do padrão — amperagem". */
    fun descrever(envio: Envio, ordem: Ordem?): String {
        val item = envio.itemId?.let { id -> ordem?.checklist?.firstOrNull { it.id == id }?.descricao }
        return when (envio.tipo) {
            TipoEnvio.ACAO -> when (envio.acao) {
                AcaoOs.DESLOCAMENTO -> "Saiu para o local"
                AcaoOs.INICIAR -> "Atendimento iniciado"
                AcaoOs.PAUSAR -> "Pausa"
                AcaoOs.RETOMAR -> "Atendimento retomado"
                AcaoOs.CONCLUIR -> "Conclusão da OS"
                AcaoOs.NAO_ATENDIDA -> "Não atendida"
                null -> "Ação"
            }
            TipoEnvio.RESPOSTA -> "Resposta" + (item?.let { " · $it" } ?: "")
            TipoEnvio.FOTO -> "Foto" + (item?.let { " · $it" } ?: "")
            TipoEnvio.VIDEO -> "Vídeo" + (item?.let { " · $it" } ?: "")
            TipoEnvio.ASSINATURA -> "Assinatura do cliente"
            TipoEnvio.NOTA -> "Nota para o escritório"
        }
    }

    /**
     * A mensagem pronta para avisar o cliente pelo WhatsApp que o técnico está
     * a caminho — o "já estou indo" que hoje sai digitado no sinal vermelho.
     */
    fun mensagemACaminho(ordem: Ordem, tecnico: String?, empresa: String?): String {
        val cliente = ordem.cliente.nome.trim().substringBefore(' ')
        val quem = listOfNotNull(tecnico?.trim()?.substringBefore(' '), empresa?.let { "da $it" })
            .joinToString(", ")
            .ifBlank { null }
        return buildString {
            append("Olá, $cliente! ")
            if (quem != null) append("Aqui é $quem. ")
            append("Estou a caminho para a ${ordem.tipo.rotulo.lowercase()} (OS ${ordem.numeroFormatado}).")
        }
    }

    /** O texto que acompanha o link do relatório. */
    fun mensagemRelatorio(ordem: Ordem, link: String, empresa: String?): String {
        val cliente = ordem.cliente.nome.trim().substringBefore(' ')
        return "Olá, $cliente! Segue o relatório da ${ordem.tipo.rotulo.lowercase()} " +
            "(OS ${ordem.numeroFormatado})" + (empresa?.let { ", da $it" } ?: "") + ":\n$link"
    }
}
