package br.com.selebi.app.nucleo.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/*
 * O que as telas pedem ao sistema e que cada um faz do seu jeito. As telas são
 * as mesmas no Android e no iPhone; isto é a ponte, escrita em `androidMain` e
 * em `iosMain`.
 */

/**
 * Sair do app para o app certo: discador, mapa, WhatsApp, compartilhar.
 *
 * Cada função diz se conseguiu abrir — falso quando nada no celular abre
 * aquilo (um tablet sem discador, por exemplo).
 */
interface AcoesExternas {
    /** Um endereço da web (`https://`, `https://wa.me/...`). */
    fun abrirLink(url: String): Boolean

    /** O discador com o número pronto — sem permissão de ligação. */
    fun discar(telefone: String): Boolean

    /** O app de mapas do celular, em `destino` ("-10.9,-37.0" ou um endereço). */
    fun abrirMapa(destino: String): Boolean

    /** A folha de compartilhar do sistema: WhatsApp de outra pessoa, e-mail, o que for. */
    fun compartilhar(texto: String, titulo: String): Boolean
}

@Composable
expect fun rememberAcoesExternas(): AcoesExternas

/**
 * A foto que chegou da câmera ou da galeria, ainda crua: no Android um
 * endereço `content://` ou `file://`; no iPhone, um arquivo temporário.
 * `temporaria`: o arquivo é do app e sai depois de a foto ser preparada.
 */
data class OrigemFoto(val endereco: String, val temporaria: Boolean)

/**
 * Abre a câmera. A `marca` (o item do checklist) volta junto com a foto —
 * guardada à parte, porque o Android pode matar o app com a câmera aberta.
 * A foto volta nula quando a pessoa desiste.
 */
@Composable
expect fun rememberCamera(aoTirar: (marca: String, foto: OrigemFoto?) -> Unit): (marca: String) -> Unit

/**
 * Grava um vídeo curto — até 30 s, em qualidade média, uns 10 a 25 MB. Como a
 * câmera, devolve a `marca` (o item) junto; nulo quando a pessoa desiste. O
 * arquivo (`.mp4` no Android, `.mov` no iPhone) é temporário: quem recebe o move
 * para a pasta da OS.
 */
@Composable
expect fun rememberCameraVideo(aoGravar: (marca: String, video: OrigemFoto?) -> Unit): (marca: String) -> Unit

/** Escolhe uma foto já tirada. Como a câmera, devolve a `marca` junto. */
@Composable
expect fun rememberGaleria(aoEscolher: (marca: String, foto: OrigemFoto?) -> Unit): (marca: String) -> Unit

/**
 * O leitor de código de barras da etiqueta do inversor. Quem chama passa o
 * que fazer com o texto lido (nulo: a pessoa desistiu). `aoFalhar`: o leitor
 * nem abriu neste celular.
 */
@Composable
expect fun rememberLeitorCodigo(aoFalhar: () -> Unit): (aoLer: (String?) -> Unit) -> Unit

/** A localização "durante o uso", pedida só na hora de sair para o local. */
interface PedidoLocalizacao {
    fun liberada(): Boolean

    fun pedir()
}

/**
 * `aoResponder` vem depois da resposta da pessoa — com ou sem permissão, a
 * ação segue: o GPS é bom de ter e não trava nada.
 */
@Composable
expect fun rememberPedidoLocalizacao(aoResponder: (liberada: Boolean) -> Unit): PedidoLocalizacao

/** O "voltar" do sistema (o botão e o gesto do Android). No iPhone não há. */
@Composable
expect fun VoltarDoSistema(ativo: Boolean, aoVoltar: () -> Unit)

/** A imagem em PNG, sem perda — a assinatura que vai no relatório. */
expect fun ImageBitmap.paraPng(): ByteArray
