package humble.model

import java.lang.{ Long => JLong }
import org.apache.log4j.Logger
import javax.persistence.{ Column, Entity, Id, GeneratedValue, Table }

import humble.framework.{ ActiveRecordModel, ActiveRecordCompanion, ActiveRecordRest, ActiveRecordJob }

object Teste     extends ActiveRecordCompanion[Teste]
class  TesteRest extends ActiveRecordRest[Teste]
class  TesteJob  extends ActiveRecordJob {
 
  override def executar = {
    var t = new Teste
    t.descricao = java.util.Calendar.getInstance.getTime.toString
    t.salvar
    this.logger.info(s"Total de registros: ${Teste.contarTodos}")
  }
  
  override def expressaoCronFrequenciaExecucao: String = "0 0/1 * 1/1 * ? *"
  
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


