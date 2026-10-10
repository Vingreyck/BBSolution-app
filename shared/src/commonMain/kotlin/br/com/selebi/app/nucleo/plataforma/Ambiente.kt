package br.com.selebi.app.nucleo.plataforma

/**
 * O que muda de um build para outro: o endereço do servidor e a versão.
 *
 * Quem abre o app preenche (o `SelebiApp` no Android, com o `BuildConfig`; o
 * `MainViewController` no iPhone). O padrão é o servidor de produção — é o que
 * os testes e as capturas de tela enxergam.
 */
object Ambiente {
    /** O endereço da API, terminado em "/". */
    var apiUrl: String = "https://app.selebi.com.br/api/app/v1/"

    var versao: String = "0.1.0"
}

/** "Android" ou "iOS" — aparece no perfil e no cabeçalho de cada pedido. */
expect val nomeDoSistema: String

/** A versão do sistema do celular: "14", "18.2". */
expect fun versaoDoSistema(): String

/** "Xiaomi Redmi Note 12 · Android 14" — para a pessoa reconhecer a sessão no site. */
expect fun descricaoDoDispositivo(): String
