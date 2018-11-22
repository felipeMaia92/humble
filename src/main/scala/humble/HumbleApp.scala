package humble

import humble.model.Teste

object HumbleApp extends App {

  var t = new Teste
  t.descricao = "aaaa"
  t.criar

  var t2 = new Teste
  t2.descricao = "bbb"
  t2.criar

  val lista = Teste.listarTodos

}
