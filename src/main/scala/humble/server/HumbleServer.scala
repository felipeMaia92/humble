package humble.server

import java.io.{ IOException, InputStream, OutputStream, ByteArrayOutputStream }
import java.util.{ List => JList, ArrayList => JArrayList, ArrayDeque => JArrayDeque, LinkedList => JLinkedList }
import java.net.InetSocketAddress
import scala.util.{ Try, Success, Failure }
import org.apache.log4j.Logger
import org.apache.mina.core.service.IoHandler
import org.apache.mina.core.session.{ IdleStatus, IoSession }
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.transport.socket.nio.NioSocketAcceptor
import org.apache.mina.filter.executor.{ OrderedThreadPoolExecutor, ExecutorFilter }

object HumbleServer extends App {
  
  val porta = 7171
  
  println("EAEAEAEAEAEAE")
  
}

class Servidor(
    var porta: Integer = 7171, 
    var iniciado: Boolean = false,
    private var listeners: JList[ListenerConexao] = new JArrayList[ListenerConexao],
    private var conexoes: JList[SessaoClient] = new JArrayList[SessaoClient]
  ) extends IoHandler {
  
  private val CONST_CHAVE_ATB_SESSAO = "clientSession"
  private val CONST_CHAVE_ATB_INPT_STRM = "inputStream"
  private val CONST_CHAVE_ATB_THRD_POOL = "threadPool"
  private val CONST_THREADS_EVENTO = 16;
  private val CONST_THREADS_IO = 2;
  
  private lazy final val logger = Logger.getLogger(classOf[Servidor])
  
  private var acceptor: NioSocketAcceptor = null
  private var eventExecutor: OrderedThreadPoolExecutor = null
  
  def iniciar = {
    if(iniciado) throw new IOException("Servidor já se encontra inicializado.")
    this.conexoes.clear
    this.acceptor = new NioSocketAcceptor(CONST_THREADS_IO)
    this.eventExecutor = new OrderedThreadPoolExecutor(CONST_THREADS_EVENTO)
    
    val chain = this.acceptor.getFilterChain
    chain.addFirst(CONST_CHAVE_ATB_THRD_POOL, new ExecutorFilter(eventExecutor))
    this.acceptor.setReuseAddress(true)
    this.acceptor.getSessionConfig.setReuseAddress(true)
    this.acceptor.setHandler(this)
    this.acceptor.bind(new InetSocketAddress(this.porta))
    this.iniciado = true
  }
  
  def parar = {
    if(!iniciado) throw new IOException("Servidor não está rodando.")
    this.acceptor.unbind
    this.conexoes.forEach(_.fechar)
    this.conexoes.clear
    this.acceptor.dispose
    this.eventExecutor.shutdown
    this.iniciado = false
  }
  
  private def recuperarInstanciaClientSessao(sessao: IoSession): SessaoClient = {
    Option(sessao.getAttribute(CONST_CHAVE_ATB_SESSAO)) match {
      case Some(sessaoClient) => sessaoClient.asInstanceOf[SessaoClient]
      case None => {
        val sessaoClient = new SessaoClient(sessao)
        sessao.setAttribute(CONST_CHAVE_ATB_SESSAO, sessaoClient)
        sessaoClient
      }
    }
  }
  
  def exceptionCaught(sessao: IoSession, exception: Throwable) = {
    logger.error(s"Erro não tratado de conexão com um client no endereço ${sessao.getRemoteAddress.toString}", exception)
    val sessaoClient = this.recuperarInstanciaClientSessao(sessao)
    if(Option(sessaoClient).isDefined) sessaoClient.fechar
  }
  
  def messageReceived(sessao: IoSession, mensagem: Any) = {
    val buffer = mensagem.asInstanceOf[IoBuffer]
    val sessaoClient = this.recuperarInstanciaClientSessao(sessao)
    val streamEntradaSessao = Option(sessao.getAttribute(CONST_CHAVE_ATB_INPT_STRM)) match {
      case Some(inputStreamSessao) => inputStreamSessao.asInstanceOf[StreamEntrada]
      case None => {
        val inputStreamSessao = new StreamEntrada
        sessao.setAttribute(CONST_CHAVE_ATB_INPT_STRM, inputStreamSessao)
        inputStreamSessao
      }
    }
    streamEntradaSessao.append(buffer)
    this.listeners.forEach(_.mensagemRecebida(sessaoClient, streamEntradaSessao))
    sessaoClient.mensagemRecebida(streamEntradaSessao)
    // TODO PORRA, MAIS UMA CLASSE VSF
  }
  
  def messageSent(sessao: IoSession, mensagem: Any) = {
    val sessaoClient = this.recuperarInstanciaClientSessao(sessao)
    this.listeners.forEach(_.mensagemEnviada(sessaoClient))
    sessaoClient.mensagemEnviada
  }
  
  def sessionClosed(sessao: IoSession) = {
    val sessaoClient = this.recuperarInstanciaClientSessao(sessao)
    this.listeners.forEach(_.conexaoFechada(sessaoClient))
    sessaoClient.sessaoFechada
    this.conexoes.remove(sessaoClient)
  }
  
  def sessionCreated(sessao: IoSession) = {
    val sessaoClient = this.recuperarInstanciaClientSessao(sessao)
    this.conexoes.add(sessaoClient)
    this.listeners.forEach(_.conexaoCriada(sessaoClient))
    sessaoClient.sessaoCriada
  }
  
  def sessionIdle(sessao: IoSession, status: IdleStatus) = {
    val sessaoClient = this.recuperarInstanciaClientSessao(sessao)
    this.listeners.forEach(_.conexaoOciosa(sessaoClient))
    sessaoClient.sessaoOciosa
  }
  
  def sessionOpened(sessao: IoSession) = {
    val sessaoClient = this.recuperarInstanciaClientSessao(sessao)
    this.listeners.forEach(_.conexaoAberta(sessaoClient))
    sessaoClient.sessaoAberta
  }
  
  def adicionarListenerConexao(listener: ListenerConexao) = this.listeners.add(listener)
  def removerListenerConexao(listener: ListenerConexao) = this.listeners.remove(listener)
  def removerTodosListenersConexao(listener: ListenerConexao) = this.listeners.clear
  
}

trait ListenerConexao {
  def conexaoAberta(sessao: SessaoClient)
  def conexaoCriada(sessao: SessaoClient)
  def conexaoFechada(sessao: SessaoClient)
  def conexaoOciosa(sessao: SessaoClient)
  def mensagemRecebida(sessao: SessaoClient, in: InputStream)
  def mensagemEnviada(sessao: SessaoClient)
}

class SessaoClient {
  
  var sessao: IoSession = _
  var streamSaida: StreamSaida = _
  var listeners: JList[ListenerConexao] = new JArrayList[ListenerConexao]
  var fechada: Boolean = false
  
  def this(sessao: IoSession) {
    this()
    this.sessao = sessao
    this.streamSaida = new StreamSaida(sessao)
  }
  
  def getStreamSaida: OutputStream = if(fechada) throw new java.nio.channels.ClosedChannelException else streamSaida
  def getEnderecoClient: java.net.InetAddress = this.sessao.getRemoteAddress.asInstanceOf[InetSocketAddress].getAddress
  
  def sessaoAberta = this.listeners.forEach(_.conexaoAberta(this))
  def sessaoCriada = this.listeners.forEach(_.conexaoCriada(this))
  def sessaoFechada = this.listeners.forEach(_.conexaoFechada(this))
  def sessaoOciosa = this.listeners.forEach(_.conexaoOciosa(this))
  def mensagemRecebida(in: InputStream) = this.listeners.forEach(_.mensagemRecebida(this, in))
  def mensagemEnviada = this.listeners.forEach(_.mensagemEnviada(this))

  def removerTodosListenersConexao = this.listeners.clear
  def removerListenerConexao(listener: ListenerConexao) = this.listeners.remove(listener)
  def adicionarListenerConexao(listener: ListenerConexao) = this.listeners.add(listener)
  
  def fechar = {
    if(fechada) throw new IOException("Conexão já se encontra fechada!")
    this.sessaoFechada
    this.streamSaida.close
    Try(this.sessao.close(false).await) match {
      case Success(fechamentoSessao) => Unit
      case Failure(ex) => Unit // java.lang.InterruptedException
    }
    this.fechada = true
  }
  
}

class StreamEntrada(
  data: JArrayDeque[IoBuffer] = new JArrayDeque[IoBuffer],
  resetCache: JLinkedList[IoBuffer] = null,
  marcado: Boolean = false
) extends InputStream {
  
  def read: Int = {
    1
  }
  
  def append(buffer: IoBuffer) = {
    this.data.offerLast(buffer)
  }
  
  private def atualizarListaBuffer: Boolean = {
    (data.isEmpty, if(this.data.isEmpty) 0 else this.data.getFirst.remaining) match {
      case (true, 0) => {
        val buff = this.data.removeFirst
        if(marcado) {
          this.resetCache.push(this.data.removeFirst)
          if(!this.data.isEmpty) this.data.getFirst.mark
        }
        else buff.free
        this.atualizarListaBuffer
      }
      case (_, _) => !this.data.isEmpty
    }
  }
  
}

class StreamSaida(
  var sessao: IoSession,
  var streamSaida: ByteArrayOutputStream = new ByteArrayOutputStream
) extends OutputStream {
  
  override def write(b: Int) = this.streamSaida.write(b)
  override def write(b: Array[Byte]) = this.streamSaida.write(b)
  override def write(b: Array[Byte], off: Int, len: Int) = this.streamSaida.write(b, off, len)
  override def flush = {
    this.streamSaida.flush
    if(this.streamSaida.size > 0) {
      this.sessao.write(IoBuffer.wrap(this.streamSaida.toByteArray))
      this.streamSaida.reset
    }
  }
  override def close = {
    this.flush
    this.streamSaida.close
    Try(this.sessao.close(false).await) match {
      case Success(sessaoAsync) => Unit
      case Failure(ex) => Unit // java.lang.InterruptedException
    }
  }
  def size: Int = this.streamSaida.size
}
