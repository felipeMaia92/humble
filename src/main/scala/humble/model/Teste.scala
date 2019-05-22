package humble.model

import java.lang.{ Long => JLong }
import java.util.{ List => JList, ArrayList => JAList }
import org.apache.log4j.Logger
import javax.persistence.{ Column, Entity, Id, GeneratedValue, Table, OneToMany, JoinColumn, FetchType, ManyToOne }
import humble.framework.{ ActiveRecordModel, ActiveRecordCompanion, ActiveRecordRest, ActiveRecordJob }

class HomeRest extends ActiveRecordRest[Teste]

@Entity
@Table(name = "TESTE", schema = "PUBLIC")
class Teste extends ActiveRecordModel {

  @Id
  @GeneratedValue
  @Column(name = "ID_TESTE")
  var idTeste: JLong = _

  @Column(name = "DESCRICAO", length = 16384, nullable = false)
  var descricao: String = _

  @OneToMany(fetch = FetchType.EAGER, mappedBy = "teste")
  @transient var filhos: JList[Filho] = null

}

object Teste extends ActiveRecordCompanion[Teste]

class TesteRest extends ActiveRecordRest[Teste]

@Entity
@Table(name = "FILHO", schema = "PUBLIC")
class Filho extends ActiveRecordModel {

  @Id
  @GeneratedValue
  @Column(name = "ID_FILHO")
  var idFilho: JLong = _

  @Column(name = "AAA", length = 16384, nullable = false)
  var aaa: String = _

  @ManyToOne(fetch = FetchType.EAGER)
  var teste: Teste = null

}

class FilhoRest extends ActiveRecordRest[Filho]

class TesteJob extends ActiveRecordJob {

  override def executar = {
    var f = new Filho
    f.aaa = java.util.Calendar.getInstance.getTime.toString
    f.teste = Teste.listarTodos(registros = 10, pagina = 1)(1)
    f.salvar
  }

  override def expressaoCronFrequenciaExecucao: String = "0 0/1 * 1/1 * ? *"

}
