@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package br.com.selebi.app.nucleo.dados

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * O [CofreSessao] do iPhone: o Keychain, o cofre do próprio sistema.
 *
 * "Só neste aparelho, depois do primeiro desbloqueio": não vai para backup nem
 * para outro iPhone (lá a sessão não valeria), e o app consegue ler mesmo
 * quando o sistema o acorda em segundo plano com a tela bloqueada — o que
 * acontece durante o rastreamento em campo.
 */
class CofreSessaoIos(private val json: Json) : CofreSessao {

    override suspend fun ler(): SessaoSalva? = withContext(Dispatchers.IO) {
        runCatching { Chaveiro.ler()?.let { json.decodeFromString(SessaoSalva.serializer(), it) } }.getOrNull()
    }

    override suspend fun salvar(sessao: SessaoSalva) = withContext(Dispatchers.IO) {
        // Sem cofre, a sessão vale só enquanto o app estiver aberto.
        runCatching { Chaveiro.salvar(json.encodeToString(SessaoSalva.serializer(), sessao)) }
        Unit
    }

    override suspend fun apagar() = withContext(Dispatchers.IO) {
        runCatching { Chaveiro.apagar() }
        Unit
    }
}

/** Um item só no Keychain: a sessão, em texto (JSON). */
private object Chaveiro {
    private const val SERVICO = "br.com.selebi.app.sessao"
    private const val CONTA = "sessao"

    fun ler(): String? = comChave { servico, conta ->
        memScoped {
            val consulta = dicionario(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to servico,
                kSecAttrAccount to conta,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )
            val resultado = alloc<CFTypeRefVar>()
            val situacao = SecItemCopyMatching(consulta, resultado.ptr)
            CFRelease(consulta)
            if (situacao != errSecSuccess) return@memScoped null
            val dados = CFBridgingRelease(resultado.value) as? NSData ?: return@memScoped null
            NSString.create(data = dados, encoding = NSUTF8StringEncoding)?.toString()
        }
    }

    fun salvar(texto: String) {
        apagar()
        val dados = NSString.create(string = texto).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val valor = CFBridgingRetain(dados)
        try {
            comChave { servico, conta ->
                val item = dicionario(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to servico,
                    kSecAttrAccount to conta,
                    kSecValueData to valor,
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                )
                SecItemAdd(item, null)
                CFRelease(item)
            }
        } finally {
            CFBridgingRelease(valor)
        }
    }

    fun apagar() {
        comChave { servico, conta ->
            val consulta = dicionario(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to servico,
                kSecAttrAccount to conta,
            )
            SecItemDelete(consulta)
            CFRelease(consulta)
        }
    }

    /** O serviço e a conta como texto do sistema, soltos de novo no fim. */
    private inline fun <T> comChave(bloco: (CFTypeRef?, CFTypeRef?) -> T): T {
        val servico = CFBridgingRetain(SERVICO)
        val conta = CFBridgingRetain(CONTA)
        try {
            return bloco(servico, conta)
        } finally {
            CFBridgingRelease(servico)
            CFBridgingRelease(conta)
        }
    }

    private fun dicionario(vararg pares: Pair<CFStringRef?, CFTypeRef?>): CFMutableDictionaryRef? {
        val dicionario = CFDictionaryCreateMutable(
            null,
            pares.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        pares.forEach { (chave, valor) -> CFDictionaryAddValue(dicionario, chave, valor) }
        return dicionario
    }
}
