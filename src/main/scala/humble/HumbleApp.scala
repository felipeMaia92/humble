package humble

import humble.model.Teste
import humble.framework.SpringContext

object HumbleApp extends App {

  var t = new Teste
  t.descricao = "aaaa"
  System.err.println(t.primaryKey)
  t.salvar
  System.err.println(t.primaryKey)

}
