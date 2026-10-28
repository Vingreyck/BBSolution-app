package br.com.selebi.app.nucleo

import android.content.Context
import br.com.selebi.app.nucleo.arquivos.ArquivosOs
import br.com.selebi.app.nucleo.arquivos.PastasApp
import br.com.selebi.app.nucleo.arquivos.ProcessadorFotosAndroid
import br.com.selebi.app.nucleo.banco.criarBanco
import br.com.selebi.app.nucleo.dados.AgendadorEnvio
import br.com.selebi.app.nucleo.dados.CofreSessaoAndroid
import br.com.selebi.app.nucleo.dados.ConectividadeAndroid
import br.com.selebi.app.nucleo.dados.LocalizacaoAndroid
import br.com.selebi.app.nucleo.dados.PreferenciasApp
import br.com.selebi.app.nucleo.dados.preferenciasApp
import br.com.selebi.app.nucleo.rastreio.MemoriaRastreioAndroid
import br.com.selebi.app.nucleo.rastreio.MotorRastreio
import br.com.selebi.app.nucleo.rede.ModuloRede

/**
 * O núcleo com as peças do Android. O agendador (WorkManager) e o motor do
 * GPS (o serviço em primeiro plano) vêm do módulo `app`, que é quem declara o
 * serviço e o trabalho no manifesto.
 */
fun montarNucleoAndroid(contexto: Context, agendador: (Nucleo) -> AgendadorEnvio, motor: MotorRastreio): Nucleo {
    val app = contexto.applicationContext
    return Nucleo(
        banco = criarBanco(app),
        cofre = CofreSessaoAndroid(app, ModuloRede.json()),
        preferencias = PreferenciasApp(app.preferenciasApp),
        conectividade = ConectividadeAndroid(app),
        localizacao = LocalizacaoAndroid(app),
        arquivos = ArquivosOs(PastasApp(app.filesDir.path, app.cacheDir.path), ProcessadorFotosAndroid(app)),
        memoriaRastreio = MemoriaRastreioAndroid(app),
        motorRastreio = motor,
        agendador = agendador,
        pastaDeFalhas = app.filesDir.path,
    )
}
