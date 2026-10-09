package br.com.selebi.app.nucleo.rede

import br.com.selebi.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuloRede {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun okHttp(autenticacao: InterceptadorAutenticacao): OkHttpClient =
        OkHttpClient.Builder()
            // Tempos pensados para 3G de povoado: conectar tem que ser rápido
            // ou falhar logo; ler pode demorar um pouco mais.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(autenticacao)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC: só método, endereço e status. Corpo nunca — nele
                    // vão senha, token e CPF.
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    fun retrofit(cliente: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_URL)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun api(retrofit: Retrofit): SelebiApi = retrofit.create(SelebiApi::class.java)
}
