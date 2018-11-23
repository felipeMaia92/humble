package humble.framework

import java.io.InputStream

trait Protocolo {
  def analisarPrimeiro(mensagem: InputStream)
  def analisarPacote(mensagem: InputStream)
  def inicializar(sessao: SessaoClient)
}

abstract class ProtocoloEncriptavel extends Protocolo {
  
  
  
}
