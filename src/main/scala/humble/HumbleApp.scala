package humble

import humble.model.Teste

object HumbleApp extends App {

  humble.framework.SpringContext.inicializar()

  println(Teste.contarTodos)
  var t = new Teste
  t.descricao = "aaaa"
  println(t.json)
  t.salvar
  println(t.json)
  println(Teste.contarTodos)
  t.descricao = "bbbb"
  t.salvar
  println(t.json)
  t.apagar
  println(Teste.contarTodos)

}
