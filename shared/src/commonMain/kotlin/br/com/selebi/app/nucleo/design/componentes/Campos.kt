package br.com.selebi.app.nucleo.design.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import br.com.selebi.app.nucleo.util.Cpf
import br.com.selebi.app.nucleo.util.MascaraCpf

/**
 * Campo de texto padrão do Selebi.
 *
 * O erro aparece **embaixo do campo que errou**, e não num aviso genérico no
 * topo: é o que o servidor devolve em `campos`, e é o que deixa a pessoa
 * consertar sem procurar.
 */
@Composable
fun CampoTexto(
    valor: String,
    aoMudar: (String) -> Unit,
    rotulo: String,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    erro: String? = null,
    dica: String? = null,
    teclado: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    acoes: KeyboardActions = KeyboardActions.Default,
    transformacao: VisualTransformation = VisualTransformation.None,
    finalDoCampo: @Composable (() -> Unit)? = null,
    habilitado: Boolean = true,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        leadingIcon = icone?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = finalDoCampo,
        isError = erro != null,
        supportingText = (erro ?: dica)?.let { { Text(it) } },
        keyboardOptions = teclado,
        keyboardActions = acoes,
        visualTransformation = transformacao,
        singleLine = true,
        enabled = habilitado,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/** CPF: teclado numérico, só dígitos no valor, máscara na tela. */
@Composable
fun CampoCpf(
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    erro: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    aoConfirmar: () -> Unit = {},
) {
    // Só acusa CPF inválido depois dos 11 dígitos: erro vermelho enquanto a
    // pessoa ainda digita é bronca antes da hora.
    val erroLocal = if (valor.length == 11 && !Cpf.valido(valor)) "Esse CPF não é válido. Confira os números." else null
    CampoTexto(
        valor = valor,
        aoMudar = { aoMudar(Cpf.digitos(it)) },
        rotulo = "CPF",
        icone = Icons.Outlined.Badge,
        erro = erro ?: erroLocal,
        teclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        acoes = KeyboardActions(onDone = { aoConfirmar() }, onGo = { aoConfirmar() }),
        transformacao = MascaraCpf(),
        modifier = modifier,
    )
}

/** Senha com o olho de mostrar/esconder — dispensa o campo "repita a senha". */
@Composable
fun CampoSenha(
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    rotulo: String = "Senha",
    erro: String? = null,
    dica: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    aoConfirmar: () -> Unit = {},
) {
    var visivel by rememberSaveable { mutableStateOf(false) }
    CampoTexto(
        valor = valor,
        aoMudar = aoMudar,
        rotulo = rotulo,
        icone = Icons.Outlined.Lock,
        erro = erro,
        dica = dica,
        teclado = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
        ),
        acoes = KeyboardActions(onDone = { aoConfirmar() }, onGo = { aoConfirmar() }),
        transformacao = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        finalDoCampo = {
            IconButton(onClick = { visivel = !visivel }) {
                Icon(
                    if (visivel) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visivel) "Esconder senha" else "Mostrar senha",
                )
            }
        },
        modifier = modifier,
    )
}
