package br.com.selebi.app.nucleo.plataforma

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
actual fun rememberAcoesExternas(): AcoesExternas {
    val contexto = LocalContext.current
    return remember(contexto) { AcoesExternasAndroid(contexto) }
}

/**
 * Nada de checar antes se existe app que abre (`resolveActivity`): no Android
 * 11+ essa checagem falha mesmo quando o app existe, e foi assim que o SeeNet
 * ficou com "Não foi possível abrir o discador". Tenta abrir e trata a falha.
 */
private class AcoesExternasAndroid(private val contexto: Context) : AcoesExternas {
    override fun abrirLink(url: String): Boolean = abrir(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun discar(telefone: String): Boolean = abrir(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefone")))

    override fun abrirMapa(destino: String): Boolean {
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destino)))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(destino)),
        )
        return abrir(geo) || abrir(web)
    }

    override fun compartilhar(texto: String, titulo: String): Boolean {
        val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
        return abrir(Intent.createChooser(envio, titulo))
    }

    private fun abrir(intent: Intent): Boolean = try {
        contexto.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Sem permissão de câmera de propósito: a foto é tirada pelo app de câmera do
 * celular, que grava num endereço que o app entrega (o `FileProvider` do
 * manifesto). O caminho e a marca ficam no estado salvo da tela: o Android
 * pode matar o app enquanto a câmera está aberta.
 */
@Composable
actual fun rememberCamera(aoTirar: (marca: String, foto: OrigemFoto?) -> Unit): (marca: String) -> Unit {
    val contexto = LocalContext.current
    var caminho by rememberSaveable { mutableStateOf<String?>(null) }
    var marcaAtual by rememberSaveable { mutableStateOf("") }
    val tirar by rememberUpdatedState(aoTirar)
    val lancador = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val arquivo = caminho ?: return@rememberLauncherForActivityResult
        caminho = null
        if (ok) {
            tirar(marcaAtual, OrigemFoto(arquivo, temporaria = true))
        } else {
            File(arquivo).delete()
            tirar(marcaAtual, null)
        }
    }
    return remember(lancador, contexto) {
        { marca ->
            val pasta = File(contexto.cacheDir, "camera").apply { mkdirs() }
            val arquivo = File(pasta, "${UUID.randomUUID()}.jpg")
            caminho = arquivo.path
            marcaAtual = marca
            try {
                lancador.launch(FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo))
            } catch (_: ActivityNotFoundException) {
                caminho = null
                tirar(marca, null)
            }
        }
    }
}

/**
 * O vídeo, como a foto: pelo app de câmera do celular, que grava no endereço que
 * o app entrega. Sem permissão de câmera nem de microfone — a câmera do sistema
 * é quem grava. 30 s e qualidade média, para o arquivo subir pelo 4G do telhado.
 */
private class GravarVideo : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .putExtra(MediaStore.EXTRA_DURATION_LIMIT, DURACAO_MAXIMA_S)
            .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            .putExtra(MediaStore.EXTRA_SIZE_LIMIT, TAMANHO_MAXIMO_BYTES)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("", input) }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK

    private companion object {
        const val DURACAO_MAXIMA_S = 30
        const val TAMANHO_MAXIMO_BYTES = 45L * 1024 * 1024
    }
}

@Composable
actual fun rememberCameraVideo(aoGravar: (marca: String, video: OrigemFoto?) -> Unit): (marca: String) -> Unit {
    val contexto = LocalContext.current
    var caminho by rememberSaveable { mutableStateOf<String?>(null) }
    var marcaAtual by rememberSaveable { mutableStateOf("") }
    val gravou by rememberUpdatedState(aoGravar)
    val lancador = rememberLauncherForActivityResult(GravarVideo()) { ok ->
        val arquivo = caminho ?: return@rememberLauncherForActivityResult
        caminho = null
        if (ok && File(arquivo).length() > 0L) {
            gravou(marcaAtual, OrigemFoto(arquivo, temporaria = true))
        } else {
            File(arquivo).delete()
            gravou(marcaAtual, null)
        }
    }
    return remember(lancador, contexto) {
        { marca ->
            val pasta = File(contexto.cacheDir, "camera").apply { mkdirs() }
            val arquivo = File(pasta, "${UUID.randomUUID()}.mp4")
            caminho = arquivo.path
            marcaAtual = marca
            try {
                lancador.launch(FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo))
            } catch (_: ActivityNotFoundException) {
                caminho = null
                gravou(marca, null)
            }
        }
    }
}

@Composable
actual fun rememberGaleria(aoEscolher: (marca: String, foto: OrigemFoto?) -> Unit): (marca: String) -> Unit {
    var marcaAtual by rememberSaveable { mutableStateOf("") }
    val escolher by rememberUpdatedState(aoEscolher)
    val lancador = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        escolher(marcaAtual, uri?.let { OrigemFoto(it.toString(), temporaria = false) })
    }
    return remember(lancador) {
        { marca ->
            marcaAtual = marca
            lancador.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** A tela de leitura do Google Play: o serial certo sem pedir permissão de câmera. */
@Composable
actual fun rememberLeitorCodigo(aoFalhar: () -> Unit): (aoLer: (String?) -> Unit) -> Unit {
    val contexto = LocalContext.current
    val falhar by rememberUpdatedState(aoFalhar)
    return remember(contexto) {
        { aoLer ->
            val opcoes = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93,
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_ITF,
                )
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(contexto, opcoes).startScan()
                .addOnSuccessListener { aoLer(it.rawValue) }
                .addOnCanceledListener { aoLer(null) }
                .addOnFailureListener {
                    aoLer(null)
                    falhar()
                }
        }
    }
}

private val PERMISSOES_LOCALIZACAO = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
actual fun rememberPedidoLocalizacao(aoResponder: (liberada: Boolean) -> Unit): PedidoLocalizacao {
    val contexto = LocalContext.current
    val responder by rememberUpdatedState(aoResponder)
    val liberadaAgora = {
        PERMISSOES_LOCALIZACAO.any { ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED }
    }
    val lancador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        responder(r.filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }.values.any { it } || liberadaAgora())
    }
    return remember(lancador, contexto) {
        object : PedidoLocalizacao {
            override fun liberada(): Boolean = liberadaAgora()

            override fun pedir() {
                // A notificação é o aviso de que o rastreamento está ligado: pede junto.
                val notificacao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }
                lancador.launch((PERMISSOES_LOCALIZACAO + notificacao).toTypedArray())
            }
        }
    }
}

@Composable
actual fun VoltarDoSistema(ativo: Boolean, aoVoltar: () -> Unit) {
    BackHandler(enabled = ativo, onBack = aoVoltar)
}

actual fun ImageBitmap.paraPng(): ByteArray {
    val saida = ByteArrayOutputStream()
    asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, saida)
    return saida.toByteArray()
}
