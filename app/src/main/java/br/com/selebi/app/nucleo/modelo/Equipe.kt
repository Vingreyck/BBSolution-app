package br.com.selebi.app.nucleo.modelo

import java.time.Instant

data class Membro(
    val id: String,
    val nome: String,
    /** Completo e formatado — só para a ficha da pessoa. */
    val cpf: String?,
    /** 123.***.***-09 — para as listas. */
    val cpfOculto: String?,
    val email: String?,
    val papel: Papel,
    val ativo: Boolean,
    val senhaProvisoria: Boolean,
    /** Tem CPF e papel de app. Os outros (vendas, estoque) usam só o navegador. */
    val usaApp: Boolean,
    val criadoEm: Instant,
    val aprovadoEm: Instant?,
)

data class Equipe(val pendentes: List<Membro>, val membros: List<Membro>)
