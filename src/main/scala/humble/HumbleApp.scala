package humble

import java.lang.{ Boolean => JBoolean }
import humble.framework.{ ContextoAplicacao, DriverDialeto, HBM2DDL }

object HumbleApp extends App {

  val ativarServidorWeb: JBoolean = true
  val portaWebApp: Integer = 8080
  val nome: String = null
  val prefixoContextoWebApp: String = "/"
  val prefixoPackage: String = null
  val urlJDBC: String = s"jdbc:h2:./arquivos/bancoDados;FILE_LOCK=SOCKET;"
  val usuarioBancoDados: String = "admin"
  val senhaBancoDados: String = ""
  val driverDialetoJPA: DriverDialeto = DriverDialeto.H2
  val hbm2ddl: HBM2DDL = HBM2DDL.ATUALIZAR
  val exibirSQL: JBoolean = false
  val formatarSQL: JBoolean = true
  val usarOtimizadorReflection: JBoolean = true
  val diretorioResourcesWebApp: String = "src/main/webapp"

  ContextoAplicacao.iniciar(
    ativarServidorWeb,
    portaWebApp,
    nome,
    prefixoContextoWebApp,
    prefixoPackage,
    urlJDBC,
    usuarioBancoDados,
    senhaBancoDados,
    driverDialetoJPA,
    hbm2ddl,
    exibirSQL,
    formatarSQL,
    usarOtimizadorReflection,
    diretorioResourcesWebApp
  )

}
