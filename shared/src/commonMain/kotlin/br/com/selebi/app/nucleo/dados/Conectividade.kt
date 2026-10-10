package br.com.selebi.app.nucleo.dados

import kotlinx.coroutines.flow.Flow

/** Tem internet agora? Vira a faixa "Sem conexão" no topo das telas. */
interface Conectividade {
    val online: Flow<Boolean>
}
