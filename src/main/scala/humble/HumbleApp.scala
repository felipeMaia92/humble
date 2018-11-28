package humble

import humble.model.Teste

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ DefaultServlet, ServletContextHandler }
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

// org.scalatra.servlet.ScalatraListener <- listener
object HumbleApp extends App {

  humble.framework.SpringContext.inicializar()

  val server = new Server(8081)
  val context = new WebAppContext
  context setContextPath "/"
  context.setResourceBase("src/main/webapp")
  context.addEventListener(new ScalatraListener)
  context.addServlet(classOf[DefaultServlet], "/")
  server.setHandler(context)
  server.start
  server.join

  /*
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
  println(Teste.contarTodos)*/

}
