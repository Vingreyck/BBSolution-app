package br.com.selebi.app.funcionalidade.ordens

import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.datetime.LocalDateTime

/*
 * O que a tela inicial mostra, montado a partir da lista de OS — sem Android,
 * sem tela, só regra. Fica aqui para poder ser testado em milissegundos.
 *
 * A pergunta que a tela responde é a do técnico às 7h da manhã: "o que eu
 * faço agora?". Por isso a ordem é:
 *
 *   1. Destaque — a OS em campo (a caminho ou em atendimento); senão a
 *      próxima de hoje; senão a atrasada mais urgente; senão a próxima da
 *      semana.
 *   2. Em campo, Atrasadas, Ainda hoje, os próximos dias (um grupo por dia),
 *      e as sem data marcada.
 *
 * Na lista, cada OS aparece uma vez só. Os números do topo são outra coisa:
 * são filtros, e podem se sobrepor — a instalação que começou às 8h conta em
 * "Em campo" e também em "Hoje", porque é trabalho de hoje (e a agenda diz o
 * mesmo).
 *
 * Concluídas ficam fora da lista principal — estão a um toque, no filtro.
 */

enum class FiltroOrdens(val rotulo: String) {
    TODAS("Abertas"),
    HOJE("Hoje"),
    ATRASADAS("Atrasadas"),
    EM_CAMPO("Em campo"),
    CONCLUIDAS("Concluídas"),
}

/** Só para quem vê a empresa inteira (adm, engenheiro). */
enum class EscopoOrdens { EQUIPE, MINHAS }

data class Contagem(
    val hoje: Int = 0,
    val atrasadas: Int = 0,
    val emCampo: Int = 0,
    val abertas: Int = 0,
    val concluidas: Int = 0,
)

enum class TipoDestaque { AGORA, PROXIMA, ATRASADA }

data class Destaque(val ordem: Ordem, val tipo: TipoDestaque)

enum class TomSecao { NORMAL, PERIGO, PRIMARIO }

data class Secao(val chave: String, val titulo: String, val ordens: List<Ordem>, val tom: TomSecao = TomSecao.NORMAL)

data class Painel(
    val contagem: Contagem = Contagem(),
    val destaque: Destaque? = null,
    val secoes: List<Secao> = emptyList(),
) {
    val vazio: Boolean get() = destaque == null && secoes.all { it.ordens.isEmpty() }
}

/** "Depois de tudo": a OS sem data vai para o fim da fila. */
private val DEPOIS_DE_TUDO = LocalDateTime(9999, 12, 31, 23, 59, 59)

private val porHora = compareBy<Ordem>({ it.agendadaPara ?: DEPOIS_DE_TUDO }, { it.prioridade.peso }, { -it.numero })
private val porUrgencia = compareBy<Ordem>({ it.prioridade.peso }, { it.prazoSla ?: it.agendadaPara ?: DEPOIS_DE_TUDO }, { -it.numero })

/** "São Cristóvão" → "sao cristovao": busca que não depende de acento nem de maiúscula. */
fun normalizar(texto: String): String =
    texto.lowercase().map { c -> COM_ACENTO.indexOf(c).let { i -> if (i >= 0) SEM_ACENTO[i] else c } }.joinToString("")

// Feito à mão, e não com `Normalizer`, porque o iPhone não tem a classe do Java.
private const val COM_ACENTO = "áàâãäéèêëíìîïóòôõöúùûüçñ"
private const val SEM_ACENTO = "aaaaaeeeeiiiiooooouuuucn"

private fun combinaCom(ordem: Ordem, termo: String): Boolean {
    val numero = termo.trimStart('#').trimStart('0')
    if (numero.isNotEmpty() && numero.all(Char::isDigit) && ordem.numero.toString() == numero) return true
    val campos = listOfNotNull(
        ordem.cliente.nome,
        ordem.cliente.endereco,
        ordem.cliente.cidade,
        ordem.descricao,
        ordem.usina?.nome,
        ordem.responsavel?.nome,
        ordem.tipo.rotulo,
    )
    return campos.any { normalizar(it).contains(termo) }
}

fun montarPainel(
    ordens: List<Ordem>,
    usuarioId: String?,
    escopo: EscopoOrdens,
    filtro: FiltroOrdens,
    busca: String,
    agora: LocalDateTime,
): Painel {
    val hoje = agora.date
    val visiveis = if (escopo == EscopoOrdens.MINHAS && usuarioId != null) {
        ordens.filter { it.responsavel?.id == usuarioId }
    } else {
        ordens
    }

    val abertas = visiveis.filter { it.aberta }
    // "Em campo" é a caminho ou atendendo: o técnico já está com a OS, e ela
    // não pode sumir numa seção de "atrasadas" só porque o prazo passou.
    val emCampo = abertas.filter { it.status.emCampo }
    val paradas = abertas.filterNot { it.status.emCampo }
    val atrasadas = paradas.filter { it.atrasada(agora) }
    val emDia = paradas.filterNot { it.atrasada(agora) }
    val deHoje = emDia.filter { it.agendadaPara?.date == hoje }
    val futuras = emDia.filter { it.agendadaPara?.date?.let { d -> d > hoje } == true }
    val semData = emDia.filter { it.agendadaPara == null }
    val concluidas = visiveis.filterNot { it.aberta }.sortedByDescending { it.concluidaEm ?: it.criadoEm }
    val doDia = abertas.filter { it.agendadaPara?.date == hoje }

    val contagem = Contagem(
        hoje = doDia.size,
        atrasadas = atrasadas.size,
        emCampo = emCampo.size,
        abertas = abertas.size,
        concluidas = concluidas.size,
    )

    val termo = normalizar(busca.trim())
    if (termo.isNotEmpty()) {
        val achadas = visiveis.filter { combinaCom(it, termo) }
            .sortedWith(compareBy<Ordem>({ !it.aberta }).then(porUrgencia))
        return Painel(contagem, null, listOf(Secao("busca", "Resultados", achadas)))
    }

    when (filtro) {
        FiltroOrdens.HOJE -> return Painel(contagem, null, listOf(Secao("hoje", "Hoje", doDia.sortedWith(porHora))))
        FiltroOrdens.ATRASADAS -> return Painel(
            contagem,
            null,
            listOf(Secao("atrasadas", "Atrasadas", atrasadas.sortedWith(porUrgencia), TomSecao.PERIGO)),
        )
        FiltroOrdens.EM_CAMPO -> return Painel(
            contagem,
            null,
            listOf(Secao("campo", "Em campo", emCampo.sortedWith(porHora), TomSecao.PRIMARIO)),
        )
        FiltroOrdens.CONCLUIDAS -> return Painel(
            contagem,
            null,
            listOf(Secao("concluidas", "Concluídas nos últimos 30 dias", concluidas)),
        )
        FiltroOrdens.TODAS -> Unit
    }

    val destaque = emCampo.sortedWith(porHora).firstOrNull()?.let { Destaque(it, TipoDestaque.AGORA) }
        ?: deHoje.sortedWith(porHora).firstOrNull()?.let { Destaque(it, TipoDestaque.PROXIMA) }
        ?: atrasadas.sortedWith(porUrgencia).firstOrNull()?.let { Destaque(it, TipoDestaque.ATRASADA) }
        ?: futuras.sortedWith(porHora).firstOrNull()?.let { Destaque(it, TipoDestaque.PROXIMA) }

    fun semDestaque(lista: List<Ordem>) = lista.filter { it.id != destaque?.ordem?.id }

    val secoes = buildList {
        semDestaque(emCampo.sortedWith(porHora))
            .takeIf { it.isNotEmpty() }?.let { add(Secao("campo", "Em campo", it, TomSecao.PRIMARIO)) }
        semDestaque(atrasadas.sortedWith(porUrgencia))
            .takeIf { it.isNotEmpty() }?.let { add(Secao("atrasadas", "Atrasadas", it, TomSecao.PERIGO)) }
        // "Ainda hoje", e não "Hoje": a OS da vez já está no destaque, aqui é o
        // que vem depois dela.
        semDestaque(deHoje.sortedWith(porHora))
            .takeIf { it.isNotEmpty() }?.let { add(Secao("hoje", "Ainda hoje", it)) }
        semDestaque(futuras)
            .groupBy { it.agendadaPara!!.date }
            .toList()
            .sortedBy { (dia, _) -> dia }
            .forEach { (dia, lista) ->
                add(Secao("dia-$dia", Datas.rotuloDia(dia, hoje), lista.sortedWith(porHora)))
            }
        semDestaque(semData.sortedWith(porUrgencia))
            .takeIf { it.isNotEmpty() }?.let { add(Secao("sem-data", "Sem data marcada", it)) }
    }

    return Painel(contagem, destaque, secoes)
}
