package br.com.selebi.app.nucleo.modelo

data class Empresa(val id: String, val nome: String)

/**
 * Papéis de quem usa o app. O servidor pode mandar outros (estoque),
 * que viram [OUTRO] em vez de derrubar o app.
 */
enum class Papel(val api: String, val rotulo: String, val descricao: String) {
    TECNICO("tecnico", "Técnico", "Vê as ordens de serviço atribuídas a ele."),
    VENDEDOR("vendedor", "Vendedor", "Faz vistorias e vê as ordens de serviço atribuídas a ele."),
    ENGENHEIRO("engenheiro", "Engenheiro", "Vê todas as ordens de serviço da empresa."),
    ADM("adm", "Administrador", "Vê tudo e cuida da equipe: aprova cadastros e troca papéis."),
    OUTRO("", "Outro", "Usa o BBSolution pelo navegador.");

    companion object {
        fun daApi(valor: String): Papel = entries.firstOrNull { it.api == valor } ?: OUTRO

        /** Os que se escolhem ao aprovar alguém, na ordem em que aparecem. */
        val doApp = listOf(TECNICO, VENDEDOR, ENGENHEIRO, ADM)
    }
}

/**
 * O que a pessoa pode fazer, decidido pelo servidor.
 *
 * O app nunca pergunta "é adm?" para mostrar uma tela — pergunta se tem a
 * permissão. Assim, mudar o que um papel pode é mexer só no servidor.
 */
enum class Permissao(val api: String) {
    VER_TODAS_OS("os.ver_todas"),
    GERIR_EQUIPE("equipe.gerir");

    companion object {
        fun daApi(valor: String): Permissao? = entries.firstOrNull { it.api == valor }
    }
}

data class Usuario(
    val id: String,
    val nome: String,
    /** Já formatado pelo servidor: 123.456.789-09. */
    val cpf: String,
    val email: String?,
    val papel: Papel,
    val deveTrocarSenha: Boolean,
    val empresa: Empresa,
    val permissoes: Set<Permissao>,
) {
    val primeiroNome: String get() = nome.trim().substringBefore(' ')

    fun pode(permissao: Permissao): Boolean = permissao in permissoes
}
