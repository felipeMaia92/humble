package humble

import humble.model.Teste

object HumbleApp extends App {

  var t = new Teste
  t.descricao = "Teste"
  println(Teste.contarTodos)
  t.salvar
  println(Teste.contarTodos)
  println(t.json)
  t.descricao = "Alterado"
  t.salvar
  println(t.json)
  t.apagar
  println(Teste.contarTodos)
  
}
