package br.com.selebi.app.funcionalidade.execucao

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.selebi.app.nucleo.design.tema.SelebiTema
import br.com.selebi.app.nucleo.modelo.ItemChecklist
import br.com.selebi.app.nucleo.modelo.RegrasOs
import br.com.selebi.app.nucleo.modelo.Resposta
import br.com.selebi.app.nucleo.modelo.TipoResposta
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.coroutines.delay

/*
 * Como se responde cada tipo de item. Toque direto para o que é de tocar
 * (sim/não, opções); texto e número guardam sozinhos quando o técnico para de
 * digitar ou muda de passo — não existe botão "salvar" para esquecer.
 */

/** O que o leitor de código de barras devolve: o texto lido, ou nulo se desistiu ou falhou. */
typealias LeitorDeCodigo = (aoLer: (String?) -> Unit) -> Unit

@Composable
fun CampoResposta(
    item: ItemChecklist,
    habilitado: Boolean,
    aoResponder: (Resposta?) -> Unit,
    aoLerCodigo: LeitorDeCodigo,
    modifier: Modifier = Modifier,
) {
    val valor = item.valor
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (item.tipoResposta) {
            TipoResposta.CHECK -> {
                val feito = (valor as? Resposta.Marcado)?.sim == true
                OpcaoGrande(
                    if (feito) "Feito" else "Marcar como feito",
                    marcada = feito,
                    aoTocar = { aoResponder(if (feito) null else Resposta.Marcado(true)) },
                    jeito = JeitoOpcao.BOTAO,
                    icone = if (feito) Icons.Outlined.CheckCircle else Icons.Outlined.TaskAlt,
                    habilitada = habilitado,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TipoResposta.SIM_NAO -> {
                val sim = (valor as? Resposta.Marcado)?.sim
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpcaoGrande(
                        "Sim", sim == true, { aoResponder(Resposta.Marcado(true)) },
                        Modifier.weight(1f), JeitoOpcao.BOTAO, Icons.Outlined.ThumbUp, habilitado,
                    )
                    OpcaoGrande(
                        "Não", sim == false, { aoResponder(Resposta.Marcado(false)) },
                        Modifier.weight(1f), JeitoOpcao.BOTAO, Icons.Outlined.ThumbDown, habilitado,
                    )
                }
            }
            TipoResposta.ESCOLHA -> {
                val escolhida = (valor as? Resposta.Texto)?.valor
                item.opcoes.forEach { opcao ->
                    OpcaoGrande(
                        opcao,
                        marcada = opcao == escolhida,
                        aoTocar = { aoResponder(if (opcao == escolhida) null else Resposta.Texto(opcao)) },
                        habilitada = habilitado,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            TipoResposta.MULTIPLA -> {
                val marcadas = (valor as? Resposta.Lista)?.valores.orEmpty()
                Text("Marque todas as que valem.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.opcoes.forEach { opcao ->
                    val marcada = opcao in marcadas
                    OpcaoGrande(
                        opcao,
                        marcada = marcada,
                        aoTocar = {
                            val nova = if (marcada) marcadas - opcao else item.opcoes.filter { it in marcadas || it == opcao }
                            aoResponder(if (nova.isEmpty()) null else Resposta.Lista(nova))
                        },
                        jeito = JeitoOpcao.VARIAS,
                        habilitada = habilitado,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            TipoResposta.NUMERO -> CampoNumero(item, habilitado, aoResponder)
            TipoResposta.TEXTO -> CampoTextoLivre(item, habilitado, aoResponder)
            TipoResposta.SERIAL -> CampoSerial(item, habilitado, aoResponder, aoLerCodigo)
            TipoResposta.FOTO -> Unit
        }
    }
}

/**
 * Texto que guarda sozinho: 800 ms depois de parar de digitar, e ao sair do
 * passo — o que estiver escrito vai, mesmo que o técnico toque em "Próximo"
 * no meio da palavra.
 */
@Composable
internal fun GuardarAoParar(rascunho: String, salvo: String, guardar: (String) -> Unit, chave: Any) {
    val atual by rememberUpdatedState(rascunho)
    val salvoAtual by rememberUpdatedState(salvo)
    val guardarAtual by rememberUpdatedState(guardar)
    LaunchedEffect(rascunho) {
        if (rascunho.trim() == salvo.trim()) return@LaunchedEffect
        delay(800)
        guardarAtual(rascunho)
    }
    DisposableEffect(chave) {
        onDispose { if (atual.trim() != salvoAtual.trim()) guardarAtual(atual) }
    }
}

@Composable
private fun CampoTextoLivre(item: ItemChecklist, habilitado: Boolean, aoResponder: (Resposta?) -> Unit) {
    val salvo = (item.valor as? Resposta.Texto)?.valor.orEmpty()
    var rascunho by rememberSaveable(item.id) { mutableStateOf(salvo) }
    GuardarAoParar(rascunho, salvo, { aoResponder(Resposta.Texto(it)) }, item.id)
    OutlinedTextField(
        value = rascunho,
        onValueChange = { rascunho = it.take(RegrasOs.TEXTO_MAXIMO) },
        label = { Text("Resposta") },
        enabled = habilitado,
        minLines = 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Unidades que se contam de um em um — ganham os botões de menos e mais. */
private val UNIDADES_DE_CONTAR = setOf("un", "vagas", "vaga", "módulos", "modulos", "pç", "peças")

/** Valores que aparecem quase sempre, a um toque: disjuntor de padrão é 40, 50 ou 63 A. */
fun sugestoesNumero(unidade: String?): List<Double> = when (unidade?.trim()?.lowercase()) {
    "a" -> listOf(32.0, 40.0, 50.0, 63.0, 70.0, 80.0, 100.0)
    else -> emptyList()
}

private fun textoDoNumero(n: Double?): String = when {
    n == null -> ""
    n == Math.floor(n) && kotlin.math.abs(n) < 1e15 -> n.toLong().toString()
    else -> String.format(Datas.brasil, "%s", n).replace('.', ',')
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CampoNumero(item: ItemChecklist, habilitado: Boolean, aoResponder: (Resposta?) -> Unit) {
    val salvo = textoDoNumero((item.valor as? Resposta.Numero)?.valor)
    var rascunho by rememberSaveable(item.id) { mutableStateOf(salvo) }
    val numero = RegrasOs.lerNumero(rascunho)
    val invalido = rascunho.isNotBlank() && numero == null
    GuardarAoParar(
        rascunho,
        salvo,
        { texto -> if (texto.isBlank()) aoResponder(null) else RegrasOs.lerNumero(texto)?.let { aoResponder(Resposta.Numero(it)) } },
        item.id,
    )
    val deContar = item.unidade?.trim()?.lowercase() in UNIDADES_DE_CONTAR

    fun definir(n: Double) {
        rascunho = textoDoNumero(n)
        aoResponder(Resposta.Numero(n))
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (deContar) {
            FilledTonalIconButton(
                onClick = { definir(((numero ?: 0.0) - 1).coerceAtLeast(0.0)) },
                enabled = habilitado,
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Outlined.Remove, contentDescription = "Menos um") }
        }
        OutlinedTextField(
            value = rascunho,
            onValueChange = { novo -> rascunho = novo.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }.take(15) },
            label = { Text("Valor") },
            suffix = item.unidade?.let { { Text(it) } },
            isError = invalido,
            supportingText = if (invalido) {
                { Text("Digite só o número.") }
            } else {
                null
            },
            enabled = habilitado,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f),
        )
        if (deContar) {
            FilledTonalIconButton(
                onClick = { definir((numero ?: 0.0) + 1) },
                enabled = habilitado,
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Outlined.Add, contentDescription = "Mais um") }
        }
    }
    val sugestoes = sugestoesNumero(item.unidade)
    if (sugestoes.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sugestoes.forEach { s ->
                FilterChip(
                    selected = numero == s,
                    onClick = { definir(s) },
                    enabled = habilitado,
                    label = { Text("${textoDoNumero(s)} ${item.unidade.orEmpty()}".trim()) },
                )
            }
        }
    }
}

/**
 * Números de série: um por inversor. Lidos pela câmera no código de barras da
 * etiqueta — ou digitados, conferidos com a mesma regra do servidor.
 */
@Composable
private fun CampoSerial(
    item: ItemChecklist,
    habilitado: Boolean,
    aoResponder: (Resposta?) -> Unit,
    aoLerCodigo: LeitorDeCodigo,
) {
    val lista = (item.valor as? Resposta.Lista)?.valores.orEmpty()
    var digitado by rememberSaveable(item.id) { mutableStateOf("") }
    var aviso by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    val problema = RegrasOs.problemaNoSerial(digitado)
    val normalizado = RegrasOs.normalizarSerial(digitado)

    fun adicionar(serial: String) {
        val s = RegrasOs.normalizarSerial(serial)
        if (s.isEmpty() || RegrasOs.problemaNoSerial(s) != null) return
        aoResponder(Resposta.Lista((lista + s).distinct()))
        digitado = ""
        aviso = if (s in lista) "$s já estava na lista." else null
    }

    lista.forEach { serial ->
        // Com cara de etiqueta: fundo claro, borda e o número em letra de máquina.
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = SelebiTema.cores.sucesso,
                    modifier = Modifier.padding(end = 10.dp).size(20.dp),
                )
                Text(serial, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { (lista - serial).let { aoResponder(if (it.isEmpty()) null else Resposta.Lista(it)) } },
                    enabled = habilitado,
                ) { Icon(Icons.Outlined.Close, contentDescription = "Tirar $serial") }
            }
        }
    }
    FilledTonalButton(
        onClick = {
            aoLerCodigo { lido ->
                if (lido == null) return@aoLerCodigo
                val s = RegrasOs.serialDoCodigo(lido)
                if (RegrasOs.problemaNoSerial(s) == null && s.isNotEmpty()) {
                    adicionar(s)
                } else {
                    // Leu algo que não parece serial: fica no campo para o técnico conferir.
                    digitado = s
                    aviso = "Confira o que foi lido com a etiqueta."
                }
            }
        },
        enabled = habilitado,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(22.dp))
        Text("Ler código de barras da etiqueta", modifier = Modifier.padding(start = 10.dp))
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = digitado,
            onValueChange = { digitado = it.uppercase().filterNot(Char::isWhitespace).take(60) },
            label = { Text(if (lista.isEmpty()) "Ou digite o número de série" else "Outro número de série") },
            isError = problema != null,
            supportingText = (problema ?: aviso)?.let { { Text(it) } },
            enabled = habilitado,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { adicionar(digitado) }),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = { adicionar(digitado) },
            enabled = habilitado && normalizado.isNotEmpty() && problema == null,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(top = 8.dp).height(56.dp),
        ) { Text("Adicionar") }
    }
}
