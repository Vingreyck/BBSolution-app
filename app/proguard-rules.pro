# Regras do R8 para o build de release.
#
# kotlinx.serialization, Ktor, OkHttp, Room, DataStore e Tink trazem as
# próprias regras dentro das bibliotecas. O que sobra para cá é o que é nosso.

# Os DTOs são lidos por reflexão do serializer gerado; mantém os companion
# objects que o plugin cria.
-keepclassmembers @kotlinx.serialization.Serializable class br.com.selebi.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# As rotas de navegação também são @Serializable (navegação tipada).
-keep @kotlinx.serialization.Serializable class br.com.selebi.app.navegacao.** { *; }
