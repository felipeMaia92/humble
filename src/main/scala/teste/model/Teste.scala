package teste.model

import java.lang.{ Long => JLong }
import javax.persistence.{ Column, Entity, Id, GeneratedValue, Table }
import xxx.framework.{ ActiveRecordModel, ActiveRecordCompanion }
import org.springframework.context.annotation.{ Configuration }
import org.springframework.transaction.annotation.{ EnableTransactionManagement }

object Teste extends ActiveRecordCompanion[Teste]

@Entity
@Table(name = "TESTE")
class Teste extends ActiveRecordModel[JLong] {

  @Id
  @GeneratedValue
  @Column(name = "ID_TESTE")
  var idTeste: JLong = _

  @Column(name = "DESCRICAO", length = 16384, nullable = false)
  var descricao: String = _

}
