package br.com.selebi.app.nucleo.rede

/**
 * Por que algo não deu certo — em termos que a tela sabe tratar.
 *
 * A distinção que mais importa é entre [SemConexao] e [Servidor]: o SeeNet
 * dizia "empresa não encontrada" quando o técnico só estava sem sinal, e ele
 * achava que tinha digitado errado. Aqui "não existe" só vem do servidor.
 */
sealed interface ErroApp {
    val mensagem: String

    data object SemConexao : ErroApp {
        override val mensagem = "Sem conexão com a internet. Confira o sinal e tente de novo."
    }

    data object TempoEsgotado : ErroApp {
        override val mensagem = "O servidor demorou para responder. Tente de novo."
    }

    /** O servidor respondeu, e respondeu que não. `codigo` é o `erro` do corpo. */
    data class Servidor(
        val status: Int,
        val codigo: String,
        override val mensagem: String,
        val campos: Map<String, String> = emptyMap(),
    ) : ErroApp

    data class Inesperado(
        override val mensagem: String = "Algo deu errado. Tente de novo.",
    ) : ErroApp
}

sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T) : Resultado<T>
    data class Falha(val erro: ErroApp) : Resultado<Nothing>
}
