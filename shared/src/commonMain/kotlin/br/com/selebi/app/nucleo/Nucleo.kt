package br.com.selebi.app.nucleo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.selebi.app.nucleo.arquivos.ArquivosOs
import br.com.selebi.app.nucleo.arquivos.Miniaturas
import br.com.selebi.app.nucleo.banco.SelebiBanco
import br.com.selebi.app.nucleo.dados.AgendadorEnvio
import br.com.selebi.app.nucleo.dados.CampoRepository
import br.com.selebi.app.nucleo.dados.CofreSessao
import br.com.selebi.app.nucleo.dados.Conectividade
import br.com.selebi.app.nucleo.dados.CopiaLocal
import br.com.selebi.app.nucleo.dados.EquipeRepository
import br.com.selebi.app.nucleo.dados.ExecucaoRepository
import br.com.selebi.app.nucleo.dados.FilaEnvio
import br.com.selebi.app.nucleo.dados.Localizacao
import br.com.selebi.app.nucleo.dados.OrdensRepository
import br.com.selebi.app.nucleo.diagnostico.RegistroDeFalhas
import br.com.selebi.app.nucleo.dados.PreferenciasApp
import br.com.selebi.app.nucleo.dados.SessaoRepository
import br.com.selebi.app.nucleo.plataforma.Ambiente
import br.com.selebi.app.nucleo.rastreio.EnvioPosicoes
import br.com.selebi.app.nucleo.rastreio.MemoriaRastreio
import br.com.selebi.app.nucleo.rastreio.MotorRastreio
import br.com.selebi.app.nucleo.rastreio.Rastreador
import br.com.selebi.app.nucleo.rede.EventosSessao
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.ModuloRede
import br.com.selebi.app.nucleo.rede.PortadorToken
import br.com.selebi.app.nucleo.rede.SelebiApi
import br.com.selebi.app.nucleo.rede.SelebiApiKtor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * O app montado: uma peça de cada, criada uma vez só, na ordem em que uma
 * depende da outra.
 *
 * Montado à mão, sem biblioteca de injeção: o Hilt só existe no Android, e uma
 * dependência esquecida aqui é erro de compilação — não um app que fecha
 * sozinho no celular do técnico.
 *
 * O que cada sistema faz do seu jeito chega pronto de fora: o Android passa o
 * cofre com Tink e o GPS em serviço; o iPhone, o Keychain e o CoreLocation.
 *
 * @param agendador recebe o próprio núcleo porque, no iPhone, quem sobe a fila
 *   é ele mesmo (não há WorkManager). Só pode guardar a referência, nunca usar
 *   durante a montagem.
 */
class Nucleo(
    val banco: SelebiBanco,
    val cofre: CofreSessao,
    val preferencias: PreferenciasApp,
    val conectividade: Conectividade,
    val localizacao: Localizacao,
    val arquivos: ArquivosOs,
    val memoriaRastreio: MemoriaRastreio,
    val motorRastreio: MotorRastreio,
    agendador: (Nucleo) -> AgendadorEnvio,
    api: SelebiApi? = null,
    /** Pasta onde o app guarda o travamento para contar na abertura seguinte. Nulo: não conta. */
    pastaDeFalhas: String? = null,
) {
    /** Vive enquanto o app vive — para o que não pode morrer com uma tela. */
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = ModuloRede.json()
    val portador = PortadorToken()
    val eventos = EventosSessao()
    val api: SelebiApi = api ?: SelebiApiKtor(Ambiente.apiUrl, portador, eventos, json)
    val executor = ExecutorApi(json)

    val agendador: AgendadorEnvio = agendador(this)
    val copia = CopiaLocal(banco, banco.ordens(), banco.pendentes(), arquivos, json)
    val fila = FilaEnvio(
        banco, banco.pendentes(), banco.ordens(), banco.avisos(), copia, this.api, executor, arquivos, json, this.agendador,
    )
    val posicoes = EnvioPosicoes(banco.posicoes(), this.api, executor)
    val rastreador = Rastreador(memoriaRastreio, localizacao, motorRastreio)
    val sessao = SessaoRepository(
        this.api, executor, cofre, preferencias, portador, banco, banco.ordens(), banco.avisos(),
        fila, posicoes, rastreador, eventos, escopo,
    )
    val ordens = OrdensRepository(this.api, executor, banco.ordens(), copia, fila, preferencias)
    val execucao = ExecucaoRepository(
        fila, copia, sessao, arquivos, localizacao, banco.avisos(), this.api, executor, json, rastreador,
    )
    val equipe = EquipeRepository(this.api, executor)
    val campo = CampoRepository(this.api, executor, json)
    val miniaturas = Miniaturas(this.api, arquivos)

    init {
        // O travamento da última vez, se houve, vai agora — com ou sem login.
        pastaDeFalhas?.let { pasta ->
            val falhas = RegistroDeFalhas(pasta, json)
            escopo.launch { falhas.enviarPendente(this@Nucleo.api, executor) }
        }
    }
}

/** O núcleo à disposição das telas. Quem monta a raiz do app fornece. */
val LocalNucleo = staticCompositionLocalOf<Nucleo> { error("O núcleo do app não foi montado.") }

/**
 * O ViewModel da tela, criado com as peças do núcleo. Na navegação, cada tela
 * tem o seu e ele sobrevive enquanto ela estiver na pilha.
 */
@Composable
inline fun <reified VM : ViewModel> vmDoNucleo(noinline criar: CreationExtras.(Nucleo) -> VM): VM {
    val nucleo = LocalNucleo.current
    return viewModel { criar(nucleo) }
}
