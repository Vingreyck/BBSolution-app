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

    /** O número do build (o `versionCode` no Android, o `CFBundleVersion` no iPhone): é o que diz qual envio está instalado. */
    var build: String = ""

    /** "0.1.0 (3)" — ou só "0.1.0" quando o build não é conhecido. */
    val versaoCompleta: String get() = if (build.isBlank()) versao else "$versao ($build)"
}

/**
 * O que foi feito sem sinal sobe sozinho mesmo com o app fechado?
 *
 * No Android sim: o WorkManager espera a rede e envia. No iPhone não: o app só
 * envia enquanto está aberto (ou logo depois de ir para o segundo plano). As
 * telas dizem a verdade de cada um — não prometem o que o sistema não deixa.
 */
expect val enviaComOAppFechado: Boolean

/** "Android" ou "iOS" — aparece no perfil e no cabeçalho de cada pedido. */
expect val nomeDoSistema: String

/** A versão do sistema do celular: "14", "18.2". */
expect fun versaoDoSistema(): String

/** "Xiaomi Redmi Note 12 · Android 14" — para a pessoa reconhecer a sessão no site. */
expect fun descricaoDoDispositivo(): String
