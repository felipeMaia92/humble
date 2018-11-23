package humble.model

import java.lang.{ Long => JLong }
import javax.persistence.{ Column, Entity, Id, GeneratedValue, Table }
import humble.framework.{ Model, ActiveRecord }

@Entity
@Table(name = "TESTE")
class Teste extends Model[JLong] {

  @Id
  @GeneratedValue
  @Column(name = "ID_TESTE")
  var idTeste: JLong = _

  @Column(name = "DESCRICAO", length = 16384, nullable = false)
  var descricao: String = _

}

object Teste extends ActiveRecord[Teste]
