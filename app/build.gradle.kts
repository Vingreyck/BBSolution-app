import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Lê o google-services.json (o projeto do Firebase) e o transforma em recursos do app.
    alias(libs.plugins.google.services)
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
        // Android 8.0+. Abaixo disso sobra menos de 1% dos celulares em uso no Brasil.
        minSdk = 26
        targetSdk = 36
        versionCode = 7
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    // As telas, os dados e a rede: o mesmo código do iPhone.
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Fila de envio da OS: sobe o que o técnico fez sem sinal quando a rede
    // volta, mesmo com o app fechado.
    implementation(libs.androidx.work.runtime)
    // Avisos (push) de OS atribuída, reagendada ou cancelada.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(platform(libs.androidx.compose.bom))
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
