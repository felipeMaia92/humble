package humble.model

import org.springframework.stereotype.{ Repository => DAOService, Component => WiredSpringObject }
import org.springframework.transaction.annotation.Transactional
import javax.persistence.{ Column, Entity, Id, GeneratedValue, Table, UniqueConstraint }
import humble.framework.{ Model, ActiveRecord }

@Entity
@Table(name = "TESTE")
@WiredSpringObject
@Transactional(readOnly = false)
class Teste extends Model {

  @Id
  @GeneratedValue
  @Column(name = "ID_TESTE")
  var idTeste: Long = _

  @Column(name = "DESCRICAO", length = 16384, nullable = false)
  var descricao: String = _

}

@DAOService
object Teste extends ActiveRecord[Teste]
