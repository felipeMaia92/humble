package humble

object HumbleApp extends App {

  xxx.framework.SpringContext.inicializar(
    xxx.framework.Configuracao(
      "teste",
      "teste",
      "jdbc:h2:./arquivos/bancoDados;CIPHER=AES;FILE_LOCK=SOCKET;",
      "admin",
      "bc0259c5c27fea4a09afb897d581c970 cabd7e3571d4cccb57f130c6fa919a0a"
    )
  )

  var t = new teste.model.Teste
  t.descricao = "aaaa"
  println(t.json)
  t.salvar
  println(t.json)

}
