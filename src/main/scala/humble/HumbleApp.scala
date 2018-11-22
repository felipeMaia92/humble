package humble

import humble.model.Teste
import humble.framework.SpringContext

object HumbleApp extends App {

  var t = new Teste
  t.descricao = "aaaa"
  t.salvar

  var t2 = new Teste
  t2.descricao = "bbb"
  t2.salvar

  println(t.qualClasse)

  val lista = Teste.listarTodos
  lista.map(item => println(item.descricao))

}
