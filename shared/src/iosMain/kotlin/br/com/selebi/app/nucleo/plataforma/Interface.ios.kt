@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package br.com.selebi.app.nucleo.plataforma

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import br.com.selebi.app.nucleo.dados.autorizado
import br.com.selebi.app.nucleo.util.codificarUrl
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Code
import platform.AVFoundation.AVMetadataObjectTypeCode93Code
import platform.AVFoundation.AVMetadataObjectTypeDataMatrixCode
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeITF14Code
import platform.AVFoundation.AVMetadataObjectTypeInterleaved2of5Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerQualityTypeMedium
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UILabel
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CALayer

/** O que as pontes do iPhone fazem fora da thread principal (gravar foto, por exemplo). */
private val fundo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** A tela que está por cima de todas agora — de onde se abre câmera, leitor e compartilhar. */
internal fun telaDoTopo(): UIViewController? {
    val app = UIApplication.sharedApplication
    val janela = app.keyWindow ?: app.windows.firstOrNull() as? UIWindow
    var tela = janela?.rootViewController
    while (tela?.presentedViewController != null) tela = tela.presentedViewController
    return tela
}

/** A pasta das fotos cruas da câmera e da galeria, antes de serem preparadas. */
internal fun pastaCapturas(): String {
    val pasta = NSTemporaryDirectory().trimEnd('/') + "/camera"
    NSFileManager.defaultManager.createDirectoryAtPath(pasta, withIntermediateDirectories = true, attributes = null, error = null)
    return pasta
}

// --- sair do app -------------------------------------------------------------

@Composable
actual fun rememberAcoesExternas(): AcoesExternas = AcoesExternasIos

private object AcoesExternasIos : AcoesExternas {
    override fun abrirLink(url: String): Boolean = abrir(url)

    override fun discar(telefone: String): Boolean = abrir("tel:" + telefone.filter { it.isDigit() || it == '+' })

    /** O Mapas da Apple, com a rota até o destino (de lá o técnico pode passar para o Google Maps ou o Waze). */
    override fun abrirMapa(destino: String): Boolean = abrir("https://maps.apple.com/?daddr=" + codificarUrl(destino))

    override fun compartilhar(texto: String, titulo: String): Boolean {
        val topo = telaDoTopo() ?: return false
        val folha = UIActivityViewController(activityItems = listOf(texto), applicationActivities = null)
        // No iPad a folha abre como balão e precisa de um lugar de onde sair.
        folha.popoverPresentationController?.sourceView = topo.view
        topo.presentViewController(folha, animated = true, completion = null)
        return true
    }

    private fun abrir(url: String): Boolean {
        val endereco = NSURL.URLWithString(url) ?: return false
        UIApplication.sharedApplication.openURL(endereco, options = emptyMap<Any?, Any?>(), completionHandler = null)
        return true
    }
}

// --- câmera e galeria ----------------------------------------------------------

@Composable
actual fun rememberCamera(aoTirar: (marca: String, foto: OrigemFoto?) -> Unit): (marca: String) -> Unit {
    val tirar by rememberUpdatedState(aoTirar)
    val seletor = remember { SeletorDeFoto() }
    return remember(seletor) { { marca -> seletor.abrir(daCamera = true) { foto -> tirar(marca, foto) } } }
}

@Composable
actual fun rememberCameraVideo(aoGravar: (marca: String, video: OrigemFoto?) -> Unit): (marca: String) -> Unit {
    val gravou by rememberUpdatedState(aoGravar)
    val gravador = remember { GravadorDeVideo() }
    return remember(gravador) { { marca -> gravador.abrir { video -> gravou(marca, video) } } }
}

@Composable
actual fun rememberGaleria(aoEscolher: (marca: String, foto: OrigemFoto?) -> Unit): (marca: String) -> Unit {
    val escolher by rememberUpdatedState(aoEscolher)
    val seletor = remember { SeletorDeFoto() }
    return remember(seletor) { { marca -> seletor.abrir(daCamera = false) { foto -> escolher(marca, foto) } } }
}

/**
 * A câmera (ou a galeria) do próprio iOS. A foto escolhida é gravada como JPEG
 * numa pasta temporária, de onde o app a prepara e depois apaga — o mesmo
 * caminho da câmera do Android.
 *
 * Sem câmera (o simulador), abre a galeria: dá para testar o resto do fluxo.
 */
private class SeletorDeFoto {
    // O iOS guarda o delegado só de leve: a referência daqui o mantém vivo.
    private var delegado: DelegadoFoto? = null

    fun abrir(daCamera: Boolean, aoTerminar: (OrigemFoto?) -> Unit) {
        val topo = telaDoTopo() ?: return aoTerminar(null)
        val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        val fonte = if (daCamera && UIImagePickerController.isSourceTypeAvailable(camera)) {
            camera
        } else {
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        }
        val novo = DelegadoFoto { imagem ->
            delegado = null
            if (imagem == null) {
                aoTerminar(null)
            } else {
                fundo.launch {
                    val origem = gravarTemporaria(imagem)
                    withContext(Dispatchers.Main) { aoTerminar(origem) }
                }
            }
        }
        delegado = novo
        val seletor = UIImagePickerController()
        seletor.sourceType = fonte
        seletor.delegate = novo
        topo.presentViewController(seletor, animated = true, completion = null)
    }

    private fun gravarTemporaria(imagem: UIImage): OrigemFoto? {
        val caminho = pastaCapturas() + "/" + NSUUID().UUIDString + ".jpg"
        val dados = UIImageJPEGRepresentation(imagem, 0.92) ?: return null
        return if (dados.writeToFile(caminho, atomically = true)) OrigemFoto(caminho, temporaria = true) else null
    }
}

/**
 * O vídeo do próprio iOS: a câmera do sistema, até 30 s em qualidade média. O
 * arquivo (`.MOV`) fica numa pasta temporária do sistema; quem recebe o move
 * para a pasta da OS. Precisa do `NSMicrophoneUsageDescription` no `Info.plist` —
 * sem ele o iOS derruba o app ao abrir a gravação.
 *
 * Sem câmera (o simulador), não há o que gravar: devolve nulo.
 */
private class GravadorDeVideo {
    private var delegado: DelegadoVideo? = null

    fun abrir(aoTerminar: (OrigemFoto?) -> Unit) {
        val topo = telaDoTopo() ?: return aoTerminar(null)
        val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(camera)) return aoTerminar(null)
        val novo = DelegadoVideo { endereco ->
            delegado = null
            aoTerminar(endereco?.let { OrigemFoto(it, temporaria = true) })
        }
        delegado = novo
        val seletor = UIImagePickerController()
        seletor.sourceType = camera
        seletor.mediaTypes = listOf("public.movie")
        seletor.videoMaximumDuration = 30.0
        seletor.videoQuality = UIImagePickerControllerQualityTypeMedium
        seletor.delegate = novo
        topo.presentViewController(seletor, animated = true, completion = null)
    }
}

private class DelegadoVideo(private val aoTerminar: (String?) -> Unit) :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val endereco = (didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL)?.path
        picker.dismissViewControllerAnimated(true) { aoTerminar(endereco) }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) { aoTerminar(null) }
    }
}

private class DelegadoFoto(private val aoTerminar: (UIImage?) -> Unit) :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val imagem = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true) { aoTerminar(imagem) }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) { aoTerminar(null) }
    }
}

// --- leitor de código de barras ---------------------------------------------------

@Composable
actual fun rememberLeitorCodigo(aoFalhar: () -> Unit): (aoLer: (String?) -> Unit) -> Unit {
    val falhar by rememberUpdatedState(aoFalhar)
    return remember {
        { aoLer ->
            fun abrir() {
                val topo = telaDoTopo()
                if (topo == null || AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) == null) {
                    aoLer(null)
                    falhar()
                } else {
                    topo.presentViewController(LeitorViewController(aoLer), animated = true, completion = null)
                }
            }
            when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
                AVAuthorizationStatusAuthorized -> abrir()
                AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { liberou ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (liberou) {
                            abrir()
                        } else {
                            aoLer(null)
                            falhar()
                        }
                    }
                }
                // Negada antes: o iOS não pergunta de novo — vale digitar a etiqueta.
                else -> {
                    aoLer(null)
                    falhar()
                }
            }
        }
    }
}

/**
 * A câmera apontada para a etiqueta do inversor; o primeiro código lido volta
 * e a tela fecha. Fechar arrastando para baixo ou no "Cancelar" volta nulo.
 */
private class LeitorViewController(private val aoLer: (String?) -> Unit) :
    UIViewController(nibName = null, bundle = null),
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    private val sessao = AVCaptureSession()
    private var previa: CALayer? = null
    private val dica = UILabel()
    private val cancelar = UIButton.buttonWithType(UIButtonTypeSystem)
    private var entregue = false

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor

        val dispositivo = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val entrada = dispositivo?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
        if (entrada != null && sessao.canAddInput(entrada)) {
            sessao.addInput(entrada)
            val saida = AVCaptureMetadataOutput()
            if (sessao.canAddOutput(saida)) {
                sessao.addOutput(saida)
                saida.setMetadataObjectsDelegate(this, dispatch_get_main_queue())
                val disponiveis = saida.availableMetadataObjectTypes
                saida.metadataObjectTypes = TIPOS.filter { tipo -> disponiveis.any { it == tipo } }
            }
        }

        val camada = AVCaptureVideoPreviewLayer(session = sessao)
        camada.videoGravity = AVLayerVideoGravityResizeAspectFill
        view.layer.addSublayer(camada)
        previa = camada

        dica.text = "Aponte para o código de barras da etiqueta"
        dica.textColor = UIColor.whiteColor
        dica.textAlignment = NSTextAlignmentCenter
        dica.numberOfLines = 2
        dica.font = UIFont.systemFontOfSize(17.0)
        view.addSubview(dica)

        cancelar.setTitle("Cancelar", forState = UIControlStateNormal)
        cancelar.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        cancelar.titleLabel?.font = UIFont.systemFontOfSize(18.0)
        cancelar.addTarget(this, NSSelectorFromString("aoCancelar"), UIControlEventTouchUpInside)
        view.addSubview(cancelar)

        // Ligar a câmera demora: fora da thread principal, como a Apple pede.
        dispatch_async(dispatch_get_global_queue(0, 0u)) { sessao.startRunning() }
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previa?.frame = view.layer.bounds
        val (largura, altura) = view.bounds.useContents { size.width to size.height }
        val baixo = view.safeAreaInsets.useContents { bottom }
        val cima = view.safeAreaInsets.useContents { top }
        dica.setFrame(CGRectMake(24.0, cima + 32.0, largura - 48.0, 60.0))
        cancelar.setFrame(CGRectMake(24.0, altura - baixo - 72.0, largura - 48.0, 52.0))
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        dispatch_async(dispatch_get_global_queue(0, 0u)) { sessao.stopRunning() }
        entregar(null)
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val texto = (didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject)?.stringValue ?: return
        if (entregue) return
        entregar(texto)
        dismissViewControllerAnimated(true, completion = null)
    }

    @ObjCAction
    fun aoCancelar() {
        entregar(null)
        dismissViewControllerAnimated(true, completion = null)
    }

    private fun entregar(texto: String?) {
        if (entregue) return
        entregue = true
        aoLer(texto)
    }
}

/** As etiquetas de inversor usam Code 128 quase sempre; os outros vão por garantia. */
private val TIPOS = listOf(
    AVMetadataObjectTypeCode128Code,
    AVMetadataObjectTypeCode39Code,
    AVMetadataObjectTypeCode93Code,
    AVMetadataObjectTypeQRCode,
    AVMetadataObjectTypeDataMatrixCode,
    AVMetadataObjectTypeEAN13Code,
    AVMetadataObjectTypeITF14Code,
    AVMetadataObjectTypeInterleaved2of5Code,
)

// --- localização ---------------------------------------------------------------

@Composable
actual fun rememberPedidoLocalizacao(aoResponder: (liberada: Boolean) -> Unit): PedidoLocalizacao {
    val responder by rememberUpdatedState(aoResponder)
    return remember { PedidoLocalizacaoIos { responder(it) } }
}

/**
 * "Durante o uso", como no Android. O iOS só pergunta uma vez: depois de
 * negada, a ação segue sem GPS e a pessoa libera nos Ajustes se quiser.
 */
private class PedidoLocalizacaoIos(private val aoResponder: (Boolean) -> Unit) : PedidoLocalizacao {
    private val gerente = CLLocationManager()
    private var esperando = false
    private val delegado = DelegadoAutorizacao {
        if (esperando && gerente.authorizationStatus != kCLAuthorizationStatusNotDetermined) {
            esperando = false
            aoResponder(liberada())
        }
    }

    init {
        gerente.delegate = delegado
    }

    override fun liberada(): Boolean = autorizado(gerente.authorizationStatus)

    override fun pedir() {
        if (gerente.authorizationStatus == kCLAuthorizationStatusNotDetermined) {
            esperando = true
            gerente.requestWhenInUseAuthorization()
        } else {
            aoResponder(liberada())
        }
    }
}

private class DelegadoAutorizacao(private val aoMudar: () -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        aoMudar()
    }
}

// --- o resto ---------------------------------------------------------------------

/** No iPhone, o "voltar" é o gesto de arrastar da borda esquerda (ligado no `MainViewController`). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun VoltarDoSistema(ativo: Boolean, aoVoltar: () -> Unit) {
    BackHandler(enabled = ativo, onBack = aoVoltar)
}

actual fun ImageBitmap.paraPng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
