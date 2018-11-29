package humble.model

import java.lang.{ Long => JLong }
import javax.persistence.{ Column, Entity, Id, GeneratedValue, Table }
import humble.framework.{ ActiveRecordModel, ActiveRecordCompanion, ActiveRecordRest }

class TesteRest extends ActiveRecordRest[Teste]

object Teste extends ActiveRecordCompanion[Teste]

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
