package br.com.selebi.app.navegacao

import kotlinx.serialization.Serializable

/*
 * Os destinos do app, com navegação tipada: o compilador confere que cada tela
 * recebe o que precisa (o `id` da OS, a mensagem do cadastro), em vez de
 * montar rota com texto e descobrir o erro com o app aberto.
 */

// Acesso
@Serializable
data object RotaLogin

@Serializable
data object RotaCadastro

@Serializable
data class RotaCadastroEnviado(val mensagem: String)

// Principal
@Serializable
data object RotaOrdens

@Serializable
data object RotaAgenda

@Serializable
data object RotaEquipe

@Serializable
data object RotaCampo

@Serializable
data object RotaPerfil

@Serializable
data class RotaOrdem(val id: String)

/** O wizard de execução da OS; `itemId` abre direto num item do checklist. */
@Serializable
data class RotaExecucao(val id: String, val itemId: String? = null)

@Serializable
data object RotaTrocarSenha
