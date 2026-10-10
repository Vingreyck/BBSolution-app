package br.com.selebi.app.funcionalidade.acesso

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.design.componentes.BotaoPrincipal
import br.com.selebi.app.nucleo.design.tema.SelebiTema

/** Depois do cadastro: o que acontece agora, sem deixar a pessoa na dúvida. */
@Composable
fun CadastroEnviadoTela(mensagem: String, aoVoltar: () -> Unit) {
    val cores = SelebiTema.cores
    LayoutAcesso {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .size(96.dp)
                .align(Alignment.CenterHorizontally)
                .background(cores.sucessoFundo, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.HowToReg, contentDescription = null, tint = cores.sucesso, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Cadastro enviado",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            mensagem,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Não chega aviso nenhum quando o acesso é liberado; o jeito
                // mais rápido é a pessoa avisar — numa empresa pequena, é um
                // WhatsApp para o chefe.
                Passo(1, "Avise um administrador que você se cadastrou.")
                Passo(2, "Ele libera o acesso e escolhe a sua função.")
                Passo(3, "Você entra com o CPF e a senha que acabou de criar.")
            }
        }
        Spacer(Modifier.height(32.dp))
        BotaoPrincipal("Voltar para o login", aoVoltar)
    }
}

@Composable
private fun Passo(numero: Int, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$numero", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}
