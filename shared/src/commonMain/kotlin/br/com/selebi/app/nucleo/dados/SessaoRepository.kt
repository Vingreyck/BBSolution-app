package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.banco.emTransacao
import br.com.selebi.app.nucleo.banco.AvisoDao
import br.com.selebi.app.nucleo.banco.OrdemDao
import br.com.selebi.app.nucleo.banco.SelebiBanco
import br.com.selebi.app.nucleo.modelo.Empresa
import br.com.selebi.app.nucleo.rastreio.EnvioPosicoes
import br.com.selebi.app.nucleo.rastreio.Rastreador
import br.com.selebi.app.nucleo.modelo.Usuario
import br.com.selebi.app.nucleo.rede.CadastroPedido
import br.com.selebi.app.nucleo.rede.CodigoEmpresaPedido
import br.com.selebi.app.nucleo.plataforma.descricaoDoDispositivo
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.EventosSessao
import br.com.selebi.app.nucleo.rede.ExcluirContaPedido
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.LoginPedido
import br.com.selebi.app.nucleo.rede.PortadorToken
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import br.com.selebi.app.nucleo.rede.TrocarSenhaPedido
import br.com.selebi.app.nucleo.rede.UsuarioDto
import br.com.selebi.app.nucleo.rede.paraModelo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface EstadoSessao {
    /** Lendo a sessão guardada — a tela de abertura fica na frente. */
    data object Carregando : EstadoSessao

    /** Sem sessão. `empresa` nula = primeira vez neste celular: pede o código. */
    data class Deslogado(val empresa: EmpresaLembrada?) : EstadoSessao

    data class Logado(val usuario: Usuario) : EstadoSessao
}

/** Id que não é de usuário nenhum: "descartar de outros que não ele" descarta tudo. */
private const val NINGUEM = ""

/**
 * Quem está usando o app, e as portas de entrada e saída.
 *
 * Abrir o app com sessão guardada **entra na hora**, sem esperar a rede, e só
 * depois confere no servidor. Essa ordem é a correção do "pede login toda vez"
 * do SeeNet: esperar a rede para decidir fazia o técnico sem sinal ser jogado
 * para fora. O único motivo para voltar ao login é o servidor dizer 401.
 */
class SessaoRepository(
    private val api: SelebiApi,
    private val executor: ExecutorApi,
    private val cofre: CofreSessao,
    private val preferencias: PreferenciasApp,
    private val portador: PortadorToken,
    private val banco: SelebiBanco,
    private val ordens: OrdemDao,
    private val avisos: AvisoDao,
    private val fila: FilaEnvio,
    private val posicoes: EnvioPosicoes,
    private val rastreador: Rastreador,
    eventos: EventosSessao,
    private val escopo: CoroutineScope,
) {
    private val _estado = MutableStateFlow<EstadoSessao>(EstadoSessao.Carregando)
    val estado: StateFlow<EstadoSessao> = _estado.asStateFlow()

    val usuario: Usuario? get() = (estado.value as? EstadoSessao.Logado)?.usuario

    /**
     * Quem está logado, esperando a sessão guardada ser lida — o envio da fila
     * pode acordar o app antes de qualquer tela.
     */
    suspend fun usuarioAtual(): Usuario? =
        (estado.first { it !is EstadoSessao.Carregando } as? EstadoSessao.Logado)?.usuario

    init {
        escopo.launch { iniciar() }
        escopo.launch { eventos.expirou.collect { encerrarLocal() } }
    }

    private suspend fun iniciar() {
        val salva = cofre.ler()
        if (salva == null) {
            _estado.value = EstadoSessao.Deslogado(preferencias.empresa.first())
            return
        }
        portador.token = salva.token
        _estado.value = EstadoSessao.Logado(salva.usuario.paraModelo())
        // O que ficou na fila da última vez volta a subir.
        fila.agendar()
        atualizarPerfil()
    }

    /** Código da empresa → nome dela, guardado neste celular. */
    suspend fun resolverEmpresa(codigo: String): Resultado<Empresa> {
        val limpo = codigo.trim()
        val resultado = executor.executar { api.resolverEmpresa(CodigoEmpresaPedido(limpo)) }
        if (resultado is Resultado.Ok) {
            val lembrada = EmpresaLembrada(resultado.valor.id, resultado.valor.nome, limpo)
            preferencias.lembrarEmpresa(lembrada)
            _estado.value = EstadoSessao.Deslogado(lembrada)
            return Resultado.Ok(Empresa(lembrada.id, lembrada.nome))
        }
        return resultado as Resultado.Falha
    }

    suspend fun trocarEmpresa() {
        preferencias.esquecerEmpresa()
        _estado.value = EstadoSessao.Deslogado(null)
    }

    suspend fun entrar(cpf: String, senha: String): Resultado<Usuario> {
        val empresa = preferencias.empresa.first()
            ?: return Resultado.Falha(ErroApp.Inesperado("Escolha a empresa antes de entrar."))

        val resultado = executor.executar {
            api.entrar(LoginPedido(empresa.id, cpf, senha, descricaoDoDispositivo()))
        }
        if (resultado !is Resultado.Ok) return resultado as Resultado.Falha

        val resposta = resultado.valor
        portador.token = resposta.token
        cofre.salvar(SessaoSalva(resposta.token, resposta.usuario))
        preferencias.lembrarCpf(cpf)
        val usuario = resposta.usuario.paraModelo()
        // Outra pessoa entrou neste celular: o que era da anterior não sobe em
        // nome dela. A mesma pessoa voltando encontra a fila como deixou.
        fila.descartarDeOutros(usuario.id)
        posicoes.descartarDeOutros(usuario.id)
        _estado.value = EstadoSessao.Logado(usuario)
        fila.agendar()
        return Resultado.Ok(usuario)
    }

    /** Devolve a mensagem de "cadastro enviado" que o servidor escreveu. */
    suspend fun cadastrar(nome: String, cpf: String, email: String?, senha: String): Resultado<String> {
        val empresa = preferencias.empresa.first()
            ?: return Resultado.Falha(ErroApp.Inesperado("Escolha a empresa antes de se cadastrar."))
        val resultado = executor.executar {
            api.cadastrar(CadastroPedido(empresa.codigo, nome.trim(), cpf, email?.trim()?.ifBlank { null }, senha))
        }
        return when (resultado) {
            is Resultado.Ok -> {
                preferencias.lembrarCpf(cpf)
                Resultado.Ok(
                    resultado.valor.mensagem
                        ?: "Agora falta um administrador da ${empresa.nome} liberar o seu acesso.",
                )
            }
            is Resultado.Falha -> resultado
        }
    }

    /** Nome, papel e permissões atualizados — o papel pode ter mudado desde o login. */
    suspend fun atualizarPerfil() {
        val resultado = executor.executar { api.eu() }
        if (resultado is Resultado.Ok) guardarUsuario(resultado.valor.usuario)
    }

    suspend fun trocarSenha(atual: String, nova: String): Resultado<Unit> {
        val resultado = executor.executar { api.trocarSenha(TrocarSenhaPedido(atual, nova)) }
        if (resultado !is Resultado.Ok) return resultado as Resultado.Falha
        guardarUsuario(resultado.valor.usuario)
        return Resultado.Ok(Unit)
    }

    /**
     * "Excluir minha conta" — exigência da Apple e da Google.
     *
     * Ao contrário do sair, precisa do servidor: sem sinal, nada é apagado e a
     * pessoa continua dentro, com a mensagem de erro. Depois de apagada lá, este
     * celular esquece tudo dela, inclusive a fila: a conta não existe mais, e o
     * que estava esperando sinal não tem em nome de quem subir.
     */
    suspend fun excluirConta(senha: String): Resultado<Unit> {
        val resultado = executor.executar { api.excluirConta(ExcluirContaPedido(senha)) }
        if (resultado !is Resultado.Ok) return resultado as Resultado.Falha
        // "De outros que não ninguém" = de todo mundo.
        fila.descartarDeOutros(NINGUEM)
        posicoes.descartarDeOutros(NINGUEM)
        preferencias.esquecerCpf()
        encerrarLocal()
        return Resultado.Ok(Unit)
    }

    /** Sair avisa o servidor quando dá; sem sinal, sai do mesmo jeito. */
    suspend fun sair() {
        executor.executar { api.sair() }
        encerrarLocal()
    }

    private suspend fun guardarUsuario(dto: UsuarioDto) {
        val token = portador.token ?: return
        cofre.salvar(SessaoSalva(token, dto))
        _estado.value = EstadoSessao.Logado(dto.paraModelo())
    }

    /**
     * Limpa o que era da pessoa neste celular.
     *
     * A cópia das OS vai embora: se outra pessoa entrar neste aparelho, ela
     * não pode ver os clientes, endereços e telefones de quem saiu.
     *
     * A fila de envio fica. Sessão que vence no meio do dia (senha redefinida,
     * 60 dias sem uso) não pode levar junto as fotos da vistoria que ainda não
     * subiram: elas voltam a subir quando a mesma pessoa entrar. Se entrar
     * outra, são descartadas no login dela.
     */
    private suspend fun encerrarLocal() {
        // Ninguém logado, ninguém rastreado. Os pontos que não subiram ficam
        // guardados, como a fila; ao entrar de novo, o rastreio volta sozinho
        // se a OS ainda estiver em campo.
        rastreador.parar()
        portador.token = null
        cofre.apagar()
        banco.emTransacao {
            ordens.apagarTodas()
            ordens.apagarAnexosDoServidor()
            ordens.apagarNotasDoServidor()
            avisos.apagarTodos()
        }
        preferencias.esquecerSincronizacao()
        _estado.value = EstadoSessao.Deslogado(preferencias.empresa.first())
    }
}
