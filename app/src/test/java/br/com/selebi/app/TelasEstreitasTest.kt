package br.com.selebi.app

/**
 * As mesmas telas do [TelasCapturaTest], no pior caso de um celular: 320 dp de
 * largura (iPhone SE, Android pequeno) e a letra do sistema em 130%.
 *
 *   gradlew :app:testDebugUnitTest --tests "*TelasEstreitasTest*"
 *
 * As imagens saem em app/build/telas-estreitas/. Texto cortado ou quebrado no
 * meio da palavra aparece aqui antes de aparecer na mão do técnico.
 */
class TelasEstreitasTest : TelasCapturaTest() {
    override val pasta = "build/telas-estreitas"
    override val larguraDp = 320
    override val escalaDaLetra = 1.3f
}
