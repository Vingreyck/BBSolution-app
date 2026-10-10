import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * O app inteiro, menos a casca de cada sistema: telas, regras do checklist,
 * fila de envio, banco local e a conversa com o servidor. O Android e o iPhone
 * rodam este mesmo código.
 *
 * O que cada sistema faz do seu jeito (câmera, GPS, mapa, cofre da sessão,
 * envio em segundo plano) é declarado aqui e escrito em `androidMain` e
 * `iosMain`.
 *
 * Os pacotes são os mesmos que o app já usava (`br.com.selebi.app...`): quem
 * mudou de endereço foi só o arquivo.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // O "framework" é o que o app do iPhone (Xcode) importa. Estático: um
    // arquivo só, sem o app precisar carregar bibliotecas soltas ao abrir.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions {
        // `expect object`/`expect class` (o construtor do banco, por exemplo).
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // As datas do Kotlin (`kotlin.time.Instant`), por baixo do kotlinx-datetime.
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        // O código do iPhone fala direto com as APIs da Apple (ponteiros e
        // estruturas de C). Igual em todas as partes do iPhone, como o Gradle exige.
        matching { it.name.startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io.core)

            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.icones)
            api(libs.jb.lifecycle.viewmodel.compose)
            api(libs.jb.lifecycle.runtime.compose)
            api(libs.jb.navigation.compose)

            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            api(libs.androidx.datastore.preferences)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            // As pré-visualizações das telas no Android Studio.
            implementation(libs.compose.ui.tooling.preview)
            // A sessão cifrada com a chave presa ao Keystore do aparelho.
            implementation(libs.tink.android)
            // Rotação da foto: a câmera grava "de lado" e marca no EXIF.
            implementation(libs.androidx.exifinterface)
            // O mapa da gestão (OpenStreetMap, sem chave nem cobrança).
            implementation(libs.osmdroid.android)
            // Leitor do código de barras da etiqueta do inversor, pela tela do
            // Google Play, sem pedir permissão de câmera.
            implementation(libs.play.services.code.scanner)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // O gesto de voltar do iPhone (arrastar da borda) chegando às telas.
            implementation(libs.compose.ui.backhandler)
            // O iPhone não tem o SQLite do Android: vai um dentro do app.
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

android {
    namespace = "br.com.selebi.app.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Esquema do banco local versionado junto do código: é o que permite escrever
// a migração quando a tabela mudar, em vez de apagar os dados do técnico.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
