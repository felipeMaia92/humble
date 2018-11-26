package humble

import humble.framework.SpringContext
import humble.model.Teste

/*

    nome: String = "humble",
    prefixoPackage: String = "humble",
    url: String = "jdbc:h2:./arquivos/bancoDados;CIPHER=AES;FILE_LOCK=SOCKET;",
    usuario: String = "admin",
    senha: String = "bc0259c5c27fea4a09afb897d581c970 cabd7e3571d4cccb57f130c6fa919a0a",
    driverDialeto: DriverDialeto = DriverDialeto.H2,
    hbm2ddl: HBM2DDL = HBM2DDL.ATUALIZAR,
    exibirSQL: JBoolean = true,
    formatarSQLExibido: JBoolean = true,
    usarOtimizadorReflection: JBoolean = true

*/
object HumbleApp extends App {

  SpringContext.inicializar()

}
