package br.com.selebi.app.funcionalidade.acesso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.design.componentes.fecharTecladoAoTocarFora

/**
 * A moldura das telas de entrada: coluna rolável que sobe com o teclado e
 * não passa de 480dp de largura (no tablet, o formulário não vira faixa).
 */
@Composable
fun LayoutAcesso(
    modifier: Modifier = Modifier,
    barraDoTopo: @Composable () -> Unit = {},
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = barraDoTopo,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { espaco ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(espaco)
                .consumeWindowInsets(espaco)
                .imePadding()
                .fecharTecladoAoTocarFora()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                content = conteudo,
            )
        }
    }
}
