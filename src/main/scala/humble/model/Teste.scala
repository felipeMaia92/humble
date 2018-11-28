package humble.model

import java.lang.{ Long => JLong }
import javax.persistence.{ Column, Entity, Id, GeneratedValue, Table }
import humble.framework.{ ContextoAplicacao, ActiveRecordModel, ActiveRecordCompanion }
import org.springframework.context.annotation.{ Configuration }
import org.springframework.transaction.annotation.{ EnableTransactionManagement }

object Teste extends ActiveRecordCompanion[Teste] 
  
class TesteRest extends org.scalatra.ScalatraServlet {
  get("/listar/todos") { ContextoAplicacao.gson.toJson(Teste.listarTodos) }
  get("/contar/todos") { ContextoAplicacao.gson.toJson(Teste.contarTodos) }
}

@Entity
@Table(name = "TESTE", schema = "PUBLIC")
class Teste extends ActiveRecordModel {

  @Id
  @GeneratedValue
  @Column(name = "ID_TESTE")
  var idTeste: JLong = _

  @Column(name = "DESCRICAO", length = 16384, nullable = false)
  var descricao: String = _

}
