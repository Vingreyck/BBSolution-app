package br.com.selebi.app.nucleo.rede

import br.com.selebi.app.nucleo.modelo.Empresa
import br.com.selebi.app.nucleo.modelo.Membro
import br.com.selebi.app.nucleo.modelo.Papel
import br.com.selebi.app.nucleo.modelo.Permissao
import br.com.selebi.app.nucleo.modelo.Usuario
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.datetime.Instant

fun UsuarioDto.paraModelo(): Usuario = Usuario(
    id = id,
    nome = nome,
    cpf = cpf,
    email = email,
    papel = Papel.daApi(papel),
    deveTrocarSenha = deveTrocarSenha,
    empresa = Empresa(empresa.id, empresa.nome),
    permissoes = permissoes.mapNotNull(Permissao::daApi).toSet(),
)

fun MembroDto.paraModelo(): Membro = Membro(
    id = id,
    nome = nome,
    cpf = cpf,
    cpfOculto = cpfOculto,
    email = email,
    papel = Papel.daApi(papel),
    ativo = ativo,
    senhaProvisoria = senhaProvisoria,
    usaApp = usaApp,
    criadoEm = Datas.lerInstante(criadoEm) ?: Instant.fromEpochSeconds(0),
    aprovadoEm = Datas.lerInstante(aprovadoEm),
)
