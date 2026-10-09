package br.com.selebi.app.nucleo.dados

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Escopo que vive enquanto o app vive — para o que não pode morrer com uma tela. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EscopoApp

@Module
@InstallIn(SingletonComponent::class)
object ModuloDados {
    @Provides
    @Singleton
    @EscopoApp
    fun escopoApp(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
