package br.com.selebi.app.nucleo.modelo

import br.com.selebi.app.nucleo.util.Numeros

/*
 * As regras da OS que o app antecipa, sem sinal — um espelho de `src/os/` do
 * servidor. Quem decide de verdade é o servidor: se os dois discordarem, a
 * próxima resposta dele corrige a tela. O espelho existe para o técnico no
 * telhado ver o item ficar verde na hora, e não só quando a foto subir.
 */

/** As ações que o técnico faz pelo app — as mesmas que a API aceita. */
enum class AcaoOs(val api: String) {
    DESLOCAMENTO("deslocamento"),
    INICIAR("iniciar"),
    PAUSAR("pausar"),
    RETOMAR("retomar"),
    CONCLUIR("concluir"),
    NAO_ATENDIDA("nao_atendida"),
}

object RegrasOs {

    val MOTIVOS_NAO_ATENDIDA = listOf(
        "Cliente ausente",
        "Sem acesso ao local",
        "Chuva ou tempo ruim",
        "Falta de material",
        "Endereço não encontrado",
        "Outro",
    )

    val MOTIVOS_PAUSA = listOf("Aguardando peça", "Aguardando o cliente", "Outro")

    // Os mesmos tetos do servidor: passar deles é recusa lá, e recusa na fila
    // offline só aparece horas depois, longe do cliente.
    const val TEXTO_MAXIMO = 2000
    const val LAUDO_MAXIMO = 8000
    const val MOTIVO_MAXIMO = 500
    const val NOME_MAXIMO = 200
    const val NOTA_MAXIMO = 4000

    /**
     * Motivo da lista + detalhe livre, escrito como o site escreve: "Cliente
     * ausente — ninguém atendeu o interfone". Em "Outro", só o detalhe.
     * Vazio quando falta o que dizer.
     */
    fun motivoCom(motivo: String?, detalhe: String?): String {
        val m = motivo?.trim().orEmpty()
        val d = detalhe?.trim().orEmpty()
        val texto = when {
            m.isEmpty() || m == "Outro" -> d
            d.isEmpty() -> m
            else -> "$m — $d"
        }
        return texto.take(MOTIVO_MAXIMO)
    }

    /** Resposta pronta para enviar, ou a frase que diz por que não serve. */
    sealed interface Normalizado {
        data class Ok(val valor: Resposta?) : Normalizado
        data class Recusa(val mensagem: String) : Normalizado
    }

    private val SERIAL_VALIDO = Regex("^[A-Z0-9._/-]+$")

    /**
     * Confere e arruma a resposta conforme o tipo do item — o
     * `normalizarValor` do servidor. Nulo apaga a resposta.
     */
    fun normalizar(item: ItemChecklist, valor: Resposta?): Normalizado {
        if (valor == null) return Normalizado.Ok(null)
        return when (item.tipoResposta) {
            TipoResposta.CHECK, TipoResposta.SIM_NAO ->
                if (valor is Resposta.Marcado) Normalizado.Ok(valor) else Normalizado.Recusa("Responda sim ou não.")
            TipoResposta.NUMERO -> {
                val n = (valor as? Resposta.Numero)?.valor
                    ?: (valor as? Resposta.Texto)?.let { lerNumero(it.valor) }
                when {
                    n == null -> if ((valor as? Resposta.Texto)?.valor?.isBlank() == true) {
                        Normalizado.Ok(null)
                    } else {
                        Normalizado.Recusa("Digite um número.")
                    }
                    kotlin.math.abs(n) >= 1e9 -> Normalizado.Recusa("Número grande demais.")
                    else -> Normalizado.Ok(Resposta.Numero(n))
                }
            }
            TipoResposta.TEXTO -> {
                val t = (valor as? Resposta.Texto)?.valor?.trim().orEmpty().take(TEXTO_MAXIMO)
                Normalizado.Ok(if (t.isEmpty()) null else Resposta.Texto(t))
            }
            TipoResposta.ESCOLHA -> {
                val t = (valor as? Resposta.Texto)?.valor?.trim().orEmpty()
                when {
                    t.isEmpty() -> Normalizado.Ok(null)
                    item.opcoes.isNotEmpty() && t !in item.opcoes -> Normalizado.Recusa("\"$t\" não está na lista.")
                    else -> Normalizado.Ok(Resposta.Texto(t))
                }
            }
            TipoResposta.MULTIPLA -> {
                val lista = (valor as? Resposta.Lista)?.valores.orEmpty()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && (item.opcoes.isEmpty() || it in item.opcoes) }
                    .distinct()
                Normalizado.Ok(if (lista.isEmpty()) null else Resposta.Lista(lista))
            }
            TipoResposta.SERIAL -> {
                val lista = (valor as? Resposta.Lista)?.valores.orEmpty().map(::normalizarSerial).filter { it.isNotEmpty() }
                val ruim = lista.firstOrNull { it.length < 4 || it.length > 60 || !SERIAL_VALIDO.matches(it) }
                if (ruim != null) {
                    Normalizado.Recusa("\"$ruim\" não parece um número de série.")
                } else {
                    Normalizado.Ok(lista.distinct().takeIf { it.isNotEmpty() }?.let { Resposta.Lista(it) })
                }
            }
            TipoResposta.FOTO -> Normalizado.Ok(null)
        }
    }

    /** O serial digitado ainda não serve? Nulo quando serve. */
    fun problemaNoSerial(bruto: String): String? {
        val s = normalizarSerial(bruto)
        return when {
            s.isEmpty() -> null
            s.length < 4 -> "Curto demais para um número de série."
            s.length > 60 -> "Longo demais para um número de série."
            !SERIAL_VALIDO.matches(s) -> "Só letras, números, ponto, hífen, barra e sublinhado."
            else -> null
        }
    }

    /**
     * O que ainda falta para o item ficar cumprido, em poucas palavras —
     * "Falta a resposta e 2 fotos". Nulo quando não falta nada.
     */
    fun faltaNoItem(item: ItemChecklist, fotos: Int): String? {
        val minimo = if (item.tipoResposta == TipoResposta.FOTO) maxOf(1, item.fotosMinimas) else item.fotosMinimas
        val faltamFotos = (minimo - fotos).coerceAtLeast(0)
        val semResposta = item.tipoResposta != TipoResposta.FOTO && !cumprido(item, item.valor, Int.MAX_VALUE)
        val textoFotos = when (faltamFotos) {
            0 -> null
            1 -> "1 foto"
            else -> "$faltamFotos fotos"
        }
        val resposta = if (item.tipoResposta == TipoResposta.CHECK) "marcar como feito" else "a resposta"
        val oQue = when {
            semResposta && textoFotos != null -> "$resposta e $textoFotos"
            semResposta -> resposta
            textoFotos != null -> textoFotos
            else -> return null
        }
        return "Falta $oQue"
    }

    /** O item está cumprido com esta resposta e esta quantidade de fotos? */
    fun cumprido(item: ItemChecklist, valor: Resposta?, fotos: Int): Boolean {
        val fotosOk = fotos >= item.fotosMinimas
        return when (item.tipoResposta) {
            TipoResposta.CHECK -> (valor as? Resposta.Marcado)?.sim == true && fotosOk
            TipoResposta.SIM_NAO -> valor is Resposta.Marcado && fotosOk
            TipoResposta.FOTO -> fotos >= maxOf(1, item.fotosMinimas)
            TipoResposta.MULTIPLA, TipoResposta.SERIAL ->
                (valor as? Resposta.Lista)?.valores?.isNotEmpty() == true && fotosOk
            TipoResposta.NUMERO -> valor is Resposta.Numero && fotosOk
            TipoResposta.TEXTO, TipoResposta.ESCOLHA ->
                (valor as? Resposta.Texto)?.valor?.isNotBlank() == true && fotosOk
        }
    }

    /** De quais situações cada ação parte — o `PERMITIDO` do servidor, só as do app. */
    fun permitida(acao: AcaoOs, status: StatusOs): Boolean = when (acao) {
        AcaoOs.DESLOCAMENTO -> status == StatusOs.ABERTA || status == StatusOs.AGENDADA
        AcaoOs.INICIAR -> status == StatusOs.ABERTA || status == StatusOs.AGENDADA || status == StatusOs.EM_DESLOCAMENTO
        AcaoOs.PAUSAR -> status == StatusOs.EM_ANDAMENTO
        AcaoOs.RETOMAR -> status == StatusOs.AGUARDANDO_PECA
        AcaoOs.CONCLUIR -> !status.encerrada && status != StatusOs.DESCONHECIDO
        AcaoOs.NAO_ATENDIDA ->
            status == StatusOs.AGENDADA || status == StatusOs.EM_DESLOCAMENTO || status == StatusOs.EM_ANDAMENTO
    }

    /** Para onde a ação leva a OS. */
    fun statusDepois(acao: AcaoOs): StatusOs = when (acao) {
        AcaoOs.DESLOCAMENTO -> StatusOs.EM_DESLOCAMENTO
        AcaoOs.INICIAR, AcaoOs.RETOMAR -> StatusOs.EM_ANDAMENTO
        AcaoOs.PAUSAR -> StatusOs.AGUARDANDO_PECA
        AcaoOs.CONCLUIR -> StatusOs.CONCLUIDA
        AcaoOs.NAO_ATENDIDA -> StatusOs.ABERTA
    }

    /**
     * O que impede concluir, em frases — vazio quando pode. As mesmas travas do
     * servidor: obrigatório pendente sempre; laudo no "não resolvido";
     * assinatura quando o modelo exige e o atendimento resolveu.
     */
    fun impedimentos(
        ordem: Ordem,
        resolvido: Boolean,
        laudo: String?,
        temAssinatura: Boolean,
    ): List<String> = buildList {
        val pendentes = ordem.obrigatoriosPendentes
        if (pendentes.isNotEmpty()) {
            add(
                (if (pendentes.size == 1) "Falta 1 item obrigatório: " else "Faltam ${pendentes.size} itens obrigatórios: ") +
                    pendentes.take(3).joinToString("; ") { it.descricao } + if (pendentes.size > 3) "…" else ".",
            )
        }
        if (!resolvido && laudo.isNullOrBlank()) add("Explique no laudo o que ficou pendente.")
        if (resolvido && ordem.exigeAssinatura && !temAssinatura) add("Falta a assinatura do cliente.")
    }

    /** A resposta em palavras — igual ao site e ao relatório. */
    fun textoDaResposta(item: ItemChecklist, fotos: Int): String {
        val v = item.valor
        return when (item.tipoResposta) {
            TipoResposta.CHECK -> if ((v as? Resposta.Marcado)?.sim == true) "Feito" else "Não feito"
            TipoResposta.SIM_NAO -> when ((v as? Resposta.Marcado)?.sim) {
                true -> "Sim"
                false -> "Não"
                null -> "Sem resposta"
            }
            TipoResposta.NUMERO -> (v as? Resposta.Numero)?.let {
                Numeros.brasileiro(it.valor) + (item.unidade?.let { u -> " $u" } ?: "")
            } ?: "Sem resposta"
            TipoResposta.MULTIPLA, TipoResposta.SERIAL ->
                (v as? Resposta.Lista)?.valores?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Sem resposta"
            TipoResposta.FOTO -> if (fotos == 0) "Sem foto" else if (fotos == 1) "1 foto" else "$fotos fotos"
            TipoResposta.TEXTO, TipoResposta.ESCOLHA ->
                (v as? Resposta.Texto)?.valor?.takeIf { it.isNotBlank() } ?: "Sem resposta"
        }
    }

    /** "1.234,5" ou "1234.5" → 1234.5; nulo se não for número. */
    fun lerNumero(texto: String): Double? {
        var s = texto.trim()
        if (s.isEmpty()) return null
        if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
        return s.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Serial como o portal escreve: maiúsculo e sem espaço. */
    fun normalizarSerial(bruto: String): String = bruto.uppercase().replace(Regex("\\s+"), "")

    /**
     * O serial dentro do que o leitor de código leu. A etiqueta do inversor às
     * vezes traz só o número; às vezes um endereço (`...?sn=ABC123`) ou um
     * texto (`SN:ABC123 PN:...`). Tira o serial de dentro e normaliza.
     */
    fun serialDoCodigo(lido: String): String {
        val texto = lido.trim()
        Regex("[?&](?:sn|serial|serialno|serial_number)=([^&#\\s]+)", RegexOption.IGNORE_CASE)
            .find(texto)?.let { return normalizarSerial(it.groupValues[1]) }
        Regex("\\b(?:S/?N|SERIAL(?:\\s*NO\\.?)?)\\s*[:=#]\\s*([A-Z0-9._/-]{4,60})", RegexOption.IGNORE_CASE)
            .find(texto)?.let { return normalizarSerial(it.groupValues[1]) }
        return normalizarSerial(texto.lineSequence().firstOrNull().orEmpty())
    }
}
