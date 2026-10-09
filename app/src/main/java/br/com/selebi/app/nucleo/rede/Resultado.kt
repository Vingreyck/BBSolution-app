package br.com.selebi.app.nucleo.rede

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

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

/** Transforma exceção de rede em [Resultado], para nenhuma tela precisar de try/catch. */
@Singleton
class ExecutorApi @Inject constructor(private val json: Json) {

    suspend fun <T> executar(bloco: suspend () -> T): Resultado<T> = try {
        Resultado.Ok(bloco())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Resultado.Falha(erroHttp(e))
    } catch (e: SocketTimeoutException) {
        Resultado.Falha(ErroApp.TempoEsgotado)
    } catch (e: IOException) {
        Resultado.Falha(ErroApp.SemConexao)
    } catch (e: Exception) {
        Resultado.Falha(ErroApp.Inesperado())
    }

    private fun erroHttp(e: HttpException): ErroApp {
        val corpo = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val dto = corpo?.let { runCatching { json.decodeFromString(ErroDto.serializer(), it) }.getOrNull() }
        return ErroApp.Servidor(
            status = e.code(),
            codigo = dto?.erro ?: "http_${e.code()}",
            mensagem = dto?.mensagem ?: "O servidor respondeu com erro (${e.code()}).",
            campos = dto?.campos.orEmpty(),
        )
    }
}

/** Para rotas sem corpo útil (204 e afins): transforma resposta de erro em exceção. */
fun Response<*>.exigirSucesso() {
    if (!isSuccessful) throw HttpException(this)
}
