package br.com.selebi.app.nucleo.rastreio

/**
 * O que está sendo rastreado, gravado no aparelho.
 *
 * Leitura imediata, sem corrotina: o serviço de GPS precisa ler na hora em
 * que o sistema o recria depois de matar o app. É pouco dado, sem nada de
 * sigiloso além do número da OS.
 */
interface MemoriaRastreio {
    fun ler(): EstadoRastreio?

    fun salvar(estado: EstadoRastreio)

    fun apagar()
}
