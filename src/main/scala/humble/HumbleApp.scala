package humble

import humble.framework.{ ContextoAplicacao, DriverDialeto, HBM2DDL }

object HumbleApp extends App {

  ContextoAplicacao.iniciar(
    ativarServidorWeb = true,
    portaWebApp = 8080,
    nome = null,
    prefixoContextoWebApp = "/",
    prefixoPackage = null,
    urlJDBC = s"jdbc:h2:./arquivos/bancoDados;FILE_LOCK=SOCKET;",
    usuarioBancoDados = "admin",
    senhaBancoDados = "",
    driverDialetoJPA = DriverDialeto.H2,
    hbm2ddl = HBM2DDL.ATUALIZAR,
    exibirSQL = true,
    formatarSQL = true,
    usarOtimizadorReflection = true,
    diretorioResourcesWebApp = "src/main/webapp",
    ativarJobManager = true
  )

}
