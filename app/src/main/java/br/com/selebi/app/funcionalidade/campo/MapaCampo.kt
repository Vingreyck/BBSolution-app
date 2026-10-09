package br.com.selebi.app.funcionalidade.campo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.selebi.app.nucleo.modelo.Campo
import br.com.selebi.app.nucleo.modelo.Frescor
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TecnicoEmCampo
import br.com.selebi.app.nucleo.modelo.TrilhaCampo
import br.com.selebi.app.nucleo.util.iniciais
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.time.Instant

/*
 * O mapa do campo, com o OpenStreetMap (osmdroid): grátis, sem chave, o mesmo
 * mapa do site. Os pinos são as iniciais do técnico na cor da situação — cobre
 * a caminho, verde no local, cinza sem sinal —, e o escolhido ganha o trajeto.
 */

private val SERGIPE = GeoPoint(-10.75, -37.3)
private const val COR_TRILHA = 0xFF2F6FB0.toInt()

private enum class Tom(val cor: Int) {
    CAMINHO(0xFFA8521F.toInt()),
    LOCAL(0xFF2C6E49.toInt()),
    SEM_SINAL(0xFF6E7A85.toInt()),
}

private fun tomDe(t: TecnicoEmCampo, agora: Instant): Tom {
    val p = t.posicao ?: return Tom.SEM_SINAL
    if (Campo.frescor(p.capturadoEm, p.modo, agora) == Frescor.SEM_SINAL) return Tom.SEM_SINAL
    return if (t.status == StatusOs.EM_DESLOCAMENTO) Tom.CAMINHO else Tom.LOCAL
}

/** O pino desenhado: círculo com as iniciais, borda branca; o escolhido com anel azul. */
private fun pino(contexto: Context, texto: String, tom: Tom, escolhido: Boolean): BitmapDrawable {
    val d = contexto.resources.displayMetrics.density
    val lado = (44 * d).toInt()
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val tela = Canvas(bmp)
    val c = lado / 2f
    val tinta = Paint(Paint.ANTI_ALIAS_FLAG)
    if (escolhido) {
        tinta.color = COR_TRILHA
        tela.drawCircle(c, c, c, tinta)
    }
    tinta.color = 0xFFFFFFFF.toInt()
    tela.drawCircle(c, c, c - 2 * d, tinta)
    tinta.color = tom.cor
    tela.drawCircle(c, c, c - 5 * d, tinta)
    tinta.color = 0xFFFFFFFF.toInt()
    tinta.textSize = 14 * d
    tinta.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    tinta.textAlign = Paint.Align.CENTER
    tela.drawText(texto, c, c - (tinta.descent() + tinta.ascent()) / 2, tinta)
    return BitmapDrawable(contexto.resources, bmp)
}

private fun casa(contexto: Context): BitmapDrawable {
    val d = contexto.resources.displayMetrics.density
    val lado = (30 * d).toInt()
    val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val tela = Canvas(bmp)
    val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF12171C.toInt() }
    tela.drawRoundRect(0f, 0f, lado.toFloat(), lado.toFloat(), 6 * d, 6 * d, tinta)
    tinta.color = 0xFFFFFFFF.toInt()
    tela.drawRoundRect(2 * d, 2 * d, lado - 2 * d, lado - 2 * d, 5 * d, 5 * d, tinta)
    tinta.color = 0xFF12171C.toInt()
    tinta.textSize = 18 * d
    tinta.textAlign = Paint.Align.CENTER
    tinta.typeface = Typeface.DEFAULT_BOLD
    tela.drawText("⌂", lado / 2f, lado / 2f - (tinta.descent() + tinta.ascent()) / 2, tinta)
    return BitmapDrawable(contexto.resources, bmp)
}

/**
 * @param enquadrar muda quando o mapa deve mostrar todo mundo (botão "Ver todos").
 * @param mostrarMapa falso nas capturas de tela: o osmdroid precisa de rede e de
 *   ladrilhos, e lá fica só o lugar do mapa.
 */
@Composable
fun MapaCampo(
    tecnicos: List<TecnicoEmCampo>,
    selecionado: String?,
    trilha: TrilhaCampo?,
    agora: Instant,
    enquadrar: Int,
    aoEscolher: (String) -> Unit,
    modifier: Modifier = Modifier,
    mostrarMapa: Boolean = true,
) {
    if (!mostrarMapa) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Map, contentDescription = "Mapa", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val contexto = LocalContext.current
    val escuro = MaterialTheme.colorScheme.surface.luminance() < 0.3f
    val mapa = remember {
        Configuration.getInstance().apply {
            userAgentValue = contexto.packageName
            // No cache do app: sem pedir acesso ao armazenamento.
            osmdroidBasePath = File(contexto.cacheDir, "osmdroid")
            osmdroidTileCache = File(contexto.cacheDir, "osmdroid/ladrilhos")
        }
        MapView(contexto).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 5.0
            controller.setZoom(9.0)
            controller.setCenter(SERGIPE)
            overlays.add(CopyrightOverlay(contexto))
        }
    }
    val camadaTrilha = remember { FolderOverlay().also { mapa.overlays.add(it) } }
    val camadaPinos = remember { FolderOverlay().also { mapa.overlays.add(it) } }
    val pinos = remember { mutableMapOf<String, Marker>() }
    val jaEnquadrou = remember { booleanArrayOf(false) }
    // O toque no pino chama sempre a versão atual do "escolher".
    val escolher by rememberUpdatedState(aoEscolher)

    val dono = LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> mapa.onResume()
                Lifecycle.Event.ON_PAUSE -> mapa.onPause()
                else -> Unit
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose {
            dono.lifecycle.removeObserver(observador)
            mapa.onDetach()
        }
    }

    fun verTodos() {
        val pontos = tecnicos.mapNotNull { it.posicao?.let { p -> GeoPoint(p.latitude, p.longitude) } }
        if (pontos.isEmpty()) return
        mapa.post {
            if (pontos.size == 1) {
                mapa.controller.setZoom(15.0)
                mapa.controller.setCenter(pontos.first())
            } else {
                mapa.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(pontos).increaseByScale(1.4f), true)
            }
        }
    }

    LaunchedEffect(enquadrar) { if (enquadrar > 0) verTodos() }

    // O trajeto do escolhido; ao chegar, o mapa enquadra o caminho inteiro.
    LaunchedEffect(trilha, selecionado) {
        camadaTrilha.items.clear()
        val t = trilha
        if (t != null && selecionado != null) {
            for (trecho in t.trechos) {
                camadaTrilha.add(
                    Polyline(mapa).apply {
                        setPoints(trecho.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = COR_TRILHA
                        outlinePaint.strokeWidth = 5 * contexto.resources.displayMetrics.density
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                    },
                )
            }
            t.destino?.let { d ->
                camadaTrilha.add(
                    Marker(mapa).apply {
                        position = GeoPoint(d.latitude, d.longitude)
                        icon = casa(contexto)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Usina do cliente"
                    },
                )
            }
            val pontos = t.trechos.flatten() + listOfNotNull(t.destino, t.posicao?.coordenada) + t.marcos.map { it.coordenada }
            if (pontos.size >= 2) {
                mapa.post {
                    mapa.zoomToBoundingBox(
                        BoundingBox.fromGeoPointsSafe(pontos.map { GeoPoint(it.latitude, it.longitude) }).increaseByScale(1.3f),
                        true,
                    )
                }
            }
        }
        mapa.invalidate()
    }

    AndroidView(
        factory = { mapa },
        modifier = modifier,
        update = { m ->
            m.overlayManager.tilesOverlay.setColorFilter(if (escuro) TilesOverlay.INVERT_COLORS else null)
            val vistos = mutableSetOf<String>()
            for (t in tecnicos) {
                val p = t.posicao ?: continue
                vistos += t.ordemId
                val marcador = pinos.getOrPut(t.ordemId) {
                    Marker(m).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ ->
                            escolher(t.ordemId)
                            true
                        }
                        camadaPinos.add(this)
                    }
                }
                marcador.position = GeoPoint(p.latitude, p.longitude)
                marcador.icon = pino(contexto, iniciais(t.tecnico?.nome ?: "?"), tomDe(t, agora), t.ordemId == selecionado)
                marcador.title = t.tecnico?.nome
            }
            pinos.keys.filter { it !in vistos }.forEach { id ->
                pinos.remove(id)?.let { camadaPinos.remove(it) }
            }
            if (!jaEnquadrou[0] && vistos.isNotEmpty()) {
                jaEnquadrou[0] = true
                verTodos()
            }
            m.invalidate()
        },
    )
}
