@file:OptIn(ExperimentalForeignApi::class)

package br.com.selebi.app.funcionalidade.campo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import br.com.selebi.app.nucleo.modelo.Coordenada
import br.com.selebi.app.nucleo.modelo.TecnicoEmCampo
import br.com.selebi.app.nucleo.modelo.TrilhaCampo
import br.com.selebi.app.nucleo.util.iniciais
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.datetime.Instant
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPolyline
import platform.MapKit.MKPolylineRenderer
import platform.MapKit.addOverlay
import platform.MapKit.overlays
import platform.MapKit.removeOverlays
import platform.UIKit.UIColor
import platform.darwin.NSObject
import kotlin.math.abs
import kotlin.math.max

/**
 * O mapa do campo no iPhone: o mapa da Apple (MapKit), que vem no sistema —
 * sem chave e sem cobrança, como o OpenStreetMap do Android. Os pinos são
 * marcadores com as iniciais do técnico, na cor da situação.
 */
@Composable
internal actual fun MapaNativo(
    tecnicos: List<TecnicoEmCampo>,
    selecionado: String?,
    trilha: TrilhaCampo?,
    agora: Instant,
    enquadrar: Int,
    aoEscolher: (String) -> Unit,
    modifier: Modifier,
) {
    val escolher by rememberUpdatedState(aoEscolher)
    val controle = remember { ControleMapa { escolher(it) } }

    LaunchedEffect(tecnicos, selecionado, agora) { controle.atualizarPinos(tecnicos, selecionado, agora) }
    LaunchedEffect(enquadrar) { if (enquadrar > 0) controle.verTodos(tecnicos) }
    // O trajeto do escolhido; ao chegar, o mapa enquadra o caminho inteiro.
    LaunchedEffect(trilha, selecionado) { controle.mostrarTrilha(if (selecionado != null) trilha else null) }

    UIKitView(factory = { controle.mapa }, modifier = modifier)
}

/** Um pino: o técnico de uma OS. */
private class Pino(val ordemId: String) : MKPointAnnotation() {
    var tom: Tom = Tom.SEM_SINAL
    var sigla: String = "?"
}

private class ControleMapa(aoEscolher: (String) -> Unit) {
    val mapa = MKMapView()
    private val delegado = DelegadoMapa(aoEscolher)
    private val pinos = mutableMapOf<String, Pino>()
    private var destino: MKPointAnnotation? = null
    private var jaEnquadrou = false

    init {
        mapa.delegate = delegado
        val sergipe = CLLocationCoordinate2DMake(LATITUDE_SERGIPE, LONGITUDE_SERGIPE)
        mapa.setRegion(MKCoordinateRegionMakeWithDistance(sergipe, 180_000.0, 180_000.0), animated = false)
    }

    fun atualizarPinos(tecnicos: List<TecnicoEmCampo>, selecionado: String?, agora: Instant) {
        val vistos = mutableSetOf<String>()
        for (t in tecnicos) {
            val p = t.posicao ?: continue
            vistos += t.ordemId
            val pino = pinos.getOrPut(t.ordemId) { Pino(t.ordemId).also { mapa.addAnnotation(it) } }
            pino.setCoordinate(CLLocationCoordinate2DMake(p.latitude, p.longitude))
            pino.setTitle(t.tecnico?.nome)
            pino.tom = tomDe(t, agora)
            pino.sigla = iniciais(t.tecnico?.nome ?: "?")
            // O pino já desenhado ganha a cor nova sem sumir e voltar.
            (mapa.viewForAnnotation(pino) as? MKMarkerAnnotationView)?.let { pintar(it, pino, pino.ordemId == selecionado) }
        }
        pinos.keys.filter { it !in vistos }.forEach { id -> pinos.remove(id)?.let { mapa.removeAnnotation(it) } }
        if (!jaEnquadrou && vistos.isNotEmpty()) {
            jaEnquadrou = true
            verTodos(tecnicos)
        }
    }

    fun verTodos(tecnicos: List<TecnicoEmCampo>) {
        val pontos = tecnicos.mapNotNull { t -> t.posicao?.let { Coordenada(it.latitude, it.longitude) } }
        enquadrar(pontos, folga = 1.4)
    }

    fun mostrarTrilha(trilha: TrilhaCampo?) {
        mapa.removeOverlays(mapa.overlays)
        destino?.let { mapa.removeAnnotation(it) }
        destino = null
        if (trilha == null) return
        for (trecho in trilha.trechos) {
            if (trecho.size < 2) continue
            mapa.addOverlay(linha(trecho))
        }
        trilha.destino?.let { d ->
            destino = MKPointAnnotation().also {
                it.setCoordinate(CLLocationCoordinate2DMake(d.latitude, d.longitude))
                it.setTitle("Usina do cliente")
                mapa.addAnnotation(it)
            }
        }
        val pontos = trilha.trechos.flatten() + listOfNotNull(trilha.destino, trilha.posicao?.coordenada) + trilha.marcos.map { it.coordenada }
        if (pontos.size >= 2) enquadrar(pontos, folga = 1.3)
    }

    private fun linha(pontos: List<Coordenada>): MKPolyline = memScoped {
        val coordenadas = allocArray<CLLocationCoordinate2D>(pontos.size)
        pontos.forEachIndexed { i, c ->
            coordenadas[i].latitude = c.latitude
            coordenadas[i].longitude = c.longitude
        }
        MKPolyline.polylineWithCoordinates(coordenadas, pontos.size.convert())
    }

    private fun enquadrar(pontos: List<Coordenada>, folga: Double) {
        if (pontos.isEmpty()) return
        if (pontos.size == 1) {
            val p = pontos.first()
            mapa.setRegion(MKCoordinateRegionMakeWithDistance(CLLocationCoordinate2DMake(p.latitude, p.longitude), 1_500.0, 1_500.0), animated = true)
            return
        }
        val minLat = pontos.minOf { it.latitude }
        val maxLat = pontos.maxOf { it.latitude }
        val minLon = pontos.minOf { it.longitude }
        val maxLon = pontos.maxOf { it.longitude }
        val centro = CLLocationCoordinate2DMake((minLat + maxLat) / 2, (minLon + maxLon) / 2)
        val extensao = MKCoordinateSpanMake(max(abs(maxLat - minLat) * folga, 0.01), max(abs(maxLon - minLon) * folga, 0.01))
        mapa.setRegion(MKCoordinateRegionMake(centro, extensao), animated = true)
    }
}

private fun cor(argb: Long): UIColor = UIColor.colorWithRed(
    red = ((argb shr 16) and 0xFF) / 255.0,
    green = ((argb shr 8) and 0xFF) / 255.0,
    blue = (argb and 0xFF) / 255.0,
    alpha = 1.0,
)

private fun pintar(vista: MKMarkerAnnotationView, pino: Pino, escolhido: Boolean) {
    vista.glyphText = pino.sigla
    vista.markerTintColor = cor(if (escolhido) COR_TRILHA else pino.tom.cor)
    vista.canShowCallout = true
}

private class DelegadoMapa(private val aoEscolher: (String) -> Unit) : NSObject(), MKMapViewDelegateProtocol {

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        val pino = viewForAnnotation as? Pino ?: return null
        val vista = mapView.dequeueReusableAnnotationViewWithIdentifier(REUSO) as? MKMarkerAnnotationView
            ?: MKMarkerAnnotationView(annotation = pino, reuseIdentifier = REUSO)
        vista.annotation = pino
        pintar(vista, pino, escolhido = false)
        return vista
    }

    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        val linha = rendererForOverlay as? MKPolyline ?: return MKOverlayRenderer(overlay = rendererForOverlay)
        return MKPolylineRenderer(polyline = linha).also {
            it.strokeColor = cor(COR_TRILHA)
            it.lineWidth = 5.0
        }
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        (didSelectAnnotationView.annotation as? Pino)?.let { aoEscolher(it.ordemId) }
    }
}

private const val REUSO = "pino"
