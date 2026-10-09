import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Endereço da API: local.properties vence gradle.properties.
 *
 * O local.properties é da máquina (não vai para o Git), então é ali que cada um
 * aponta para o IP do próprio computador quando testa num celular de verdade.
 */
val propriedadesLocais = Properties().apply {
    val arquivo = rootProject.file("local.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

/** Chave de assinatura do release: `keystore.properties` é da máquina e não vai para o Git. */
val propriedadesChave = Properties().apply {
    val arquivo = rootProject.file("keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

fun propriedade(nome: String): String =
    propriedadesLocais.getProperty(nome) ?: providers.gradleProperty(nome).get()

android {
    namespace = "br.com.selebi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.selebi.app"
        // Android 8.0+. Abaixo disso sobra menos de 1% dos celulares em uso no
        // Brasil, e é o que permite usar java.time sem desugaring.
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (propriedadesChave.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(propriedadesChave.getProperty("storeFile"))
                storePassword = propriedadesChave.getProperty("storePassword")
                keyAlias = propriedadesChave.getProperty("keyAlias")
                keyPassword = propriedadesChave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_URL", "\"${propriedade("selebi.api.url")}\"")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "API_URL", "\"${propriedade("selebi.api.url.release")}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric precisa dos recursos do app para desenhar as telas.
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "1536m"
                // Grava as capturas das telas em app/build/telas/ a cada
                // `gradlew testDebugUnitTest`. Renderização "hardware" para as
                // sombras dos cards saírem como no celular.
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            }
        }
    }
}

ksp {
    // Esquema do banco local versionado junto do código: é o que permite
    // escrever a migração quando a tabela mudar, em vez de apagar os dados do
    // técnico a cada versão.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Fila de envio da OS: sobe o que o técnico fez sem sinal quando a rede
    // volta, mesmo com o app fechado.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    // Rotação da foto: a câmera grava "de lado" e marca no EXIF.
    implementation(libs.androidx.exifinterface)
    // Leitor do código de barras da etiqueta do inversor: o serial certo sem
    // digitar 16 caracteres em cima do telhado. Usa a tela do Google Play, sem
    // pedir permissão de câmera.
    implementation(libs.play.services.code.scanner)
    // Rastreamento em campo: o mapa da gestão (OpenStreetMap, sem chave nem
    // cobrança — a escolha do SeeNet depois de o Google Maps pedir cartão) e
    // as posições ao vivo empurradas pelo servidor (Server-Sent Events).
    implementation(libs.osmdroid.android)
    implementation(libs.okhttp.sse)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
