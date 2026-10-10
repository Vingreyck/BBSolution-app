package br.com.selebi.app.nucleo.rede

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * O formato exato do que trafega com o Selebi (`/api/app/v1`). Espelha
 * os arquivos de `src/app-movel` do servidor. Campo novo no servidor não quebra o app
 * (ignoreUnknownKeys); campo a menos, sim — por isso tudo o que o servidor pode
 * omitir tem valor padrão aqui.
 */

@Serializable
data class CodigoEmpresaPedido(val codigo: String)

@Serializable
data class EmpresaDto(val id: String, val nome: String)

@Serializable
data class CadastroPedido(
    val codigo: String,
    val nome: String,
    val cpf: String,
    val email: String?,
    val senha: String,
)

@Serializable
data class CadastroResposta(val situacao: String, val mensagem: String? = null)

@Serializable
data class LoginPedido(
    val empresaId: String,
    val cpf: String,
    val senha: String,
    val dispositivo: String?,
)

@Serializable
data class UsuarioDto(
    val id: String,
    val nome: String,
    val cpf: String,
    val email: String? = null,
    val papel: String,
    val deveTrocarSenha: Boolean = false,
    val empresa: EmpresaDto,
    val permissoes: List<String> = emptyList(),
)

@Serializable
data class LoginResposta(val token: String, val expiraEm: String, val usuario: UsuarioDto)

@Serializable
data class EuResposta(val usuario: UsuarioDto)

@Serializable
data class TrocarSenhaPedido(val atual: String, val nova: String)

@Serializable
data class ExcluirContaPedido(val senha: String)

@Serializable
data class ExclusaoResposta(val excluida: Boolean)

@Serializable
data class ClienteDto(
    val id: String,
    val nome: String,
    val telefone: String? = null,
    val endereco: String? = null,
    val cidade: String? = null,
    val uf: String? = null,
)

@Serializable
data class UsinaDto(
    val id: String,
    val nome: String,
    val potenciaKwp: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class PessoaDto(val id: String, val nome: String)

@Serializable
data class ItemChecklistDto(
    val id: String,
    val ordem: Int = 0,
    val descricao: String,
    val obrigatorio: Boolean = false,
    val concluido: Boolean = false,
    val observacao: String? = null,
    val secao: String? = null,
    val ajuda: String? = null,
    val tipoResposta: String = "check",
    val opcoes: List<String> = emptyList(),
    val unidade: String? = null,
    val fotosMinimas: Int = 0,
    val apenasCamera: Boolean = false,
    /** true/false, número, texto ou lista — depende do tipo do item. */
    val valor: JsonElement? = null,
    val respondidoEm: String? = null,
)

@Serializable
data class ProjetoDto(val id: String, val titulo: String)

@Serializable
data class AnexoDto(
    val id: String,
    val itemId: String? = null,
    val categoria: String = "foto",
    val capturadoEm: String? = null,
    val enviadoPorId: String? = null,
)

/** Uma linha do histórico da OS, já escrita pelo servidor. */
@Serializable
data class EventoDto(
    val id: String,
    val tipo: String,
    val titulo: String,
    val detalhe: String? = null,
    val quem: String? = null,
    val origem: String = "web",
    val ocorridoEm: String? = null,
)

/** Uma nota da OS (os comentários do site). */
@Serializable
data class NotaDto(
    val id: String,
    val texto: String,
    val autorId: String? = null,
    val autor: String? = null,
    val criadoEm: String? = null,
)

@Serializable
data class OrdemDto(
    val id: String,
    val numero: Int,
    val tipo: String,
    val status: String,
    val prioridade: String = "normal",
    val origem: String = "manual",
    val descricao: String,
    val laudo: String? = null,
    val agendadaPara: String? = null,
    val prazoSla: String? = null,
    val iniciadaEm: String? = null,
    val concluidaEm: String? = null,
    val criadoEm: String,
    val cliente: ClienteDto,
    val usina: UsinaDto? = null,
    val responsavel: PessoaDto? = null,
    val checklist: List<ItemChecklistDto> = emptyList(),
    val projeto: ProjetoDto? = null,
    val resultado: String? = null,
    val assinaturaNome: String? = null,
    val atualizadoEm: String? = null,
    val instrucoes: String? = null,
    val exigeAssinatura: Boolean = false,
    val anexos: List<AnexoDto> = emptyList(),
    /** Caminho do relatório público, `/r/…`, depois de concluída. */
    val relatorio: String? = null,
    val historico: List<EventoDto> = emptyList(),
    val notas: List<NotaDto> = emptyList(),
)

@Serializable
data class OrdensResposta(val ordens: List<OrdemDto>, val geradoEm: String? = null)

@Serializable
data class OrdemResposta(val ordem: OrdemDto)

/** Resposta que traz a OS se ela ainda estiver no escopo de quem pediu. */
@Serializable
data class OrdemTalvezResposta(val ordem: OrdemDto? = null)

@Serializable
data class MembroDto(
    val id: String,
    val nome: String,
    val cpf: String? = null,
    val cpfOculto: String? = null,
    val email: String? = null,
    val papel: String,
    val ativo: Boolean,
    val senhaProvisoria: Boolean = false,
    val usaApp: Boolean = false,
    val criadoEm: String,
    val aprovadoEm: String? = null,
)

@Serializable
data class EquipeResposta(val pendentes: List<MembroDto>, val membros: List<MembroDto>)

@Serializable
data class AprovarPedido(val papel: String)

@Serializable
data class AlterarMembroPedido(val papel: String? = null, val ativo: Boolean? = null)

@Serializable
data class SenhaProvisoriaResposta(val senhaProvisoria: String)

/**
 * Uma ação do técnico: saiu, iniciou, pausou, retomou, concluiu, não atendeu.
 * O `idCliente` é gerado no celular e torna o reenvio seguro.
 */
@Serializable
data class AcaoPedido(
    val idCliente: String,
    val acao: String,
    val ocorridoEm: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val precisao: Double? = null,
    val motivo: String? = null,
    val resultado: String? = null,
    val laudo: String? = null,
    val assinaturaNome: String? = null,
)

@Serializable
data class AcaoResposta(
    val ordem: OrdemDto? = null,
    val repetida: Boolean = false,
    val avisos: List<String> = emptyList(),
)

/** A resposta de um item. `observacao` vazia apaga; ausente mantém. */
@Serializable
data class RespostaPedido(
    val valor: JsonElement? = null,
    val observacao: String? = null,
    val respondidoEm: String? = null,
)

@Serializable
data class RespostaItemResposta(
    val ordem: OrdemDto? = null,
    val ignorado: Boolean = false,
    val avisos: List<String> = emptyList(),
)

/** Uma nota escrita no celular. */
@Serializable
data class NotaPedido(
    val idCliente: String,
    val texto: String,
    val criadoEm: String? = null,
)

@Serializable
data class NotaResposta(val ordem: OrdemDto? = null, val repetida: Boolean = false)

@Serializable
data class AnexoCriadoDto(val id: String, val repetido: Boolean = false)

@Serializable
data class AnexoResposta(val anexo: AnexoCriadoDto, val ordem: OrdemDto? = null)

/** O corpo de todo erro do servidor: `{ erro, mensagem, campos? }`. */
@Serializable
data class ErroDto(
    val erro: String,
    val mensagem: String,
    val campos: Map<String, String>? = null,
)
