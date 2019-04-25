package humble.framework

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.util.{ Try, Success, Failure }
import java.lang.{
  Boolean => JBoolean, Long => JLong, Integer => JInteger, Float => JFloat,
  Short => JShort, Byte => JByte, Double => JDouble, Character => JChar
}
import java.lang.reflect.{ Field => JAttribute }
import java.io.{ Serializable => JSerial, File => JFile }
import java.util.{ List => JList, ArrayList => JArrayList, Properties => JProperties, Calendar => JCalendar, Date => JDate }
import java.util.concurrent.Executors
import javax.persistence.{ PersistenceContext, EntityManager, EntityManagerFactory, Transient, Query }
import javax.sql.DataSource
import org.apache.log4j.Logger
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ DefaultServlet, ServletContextHandler }
import org.eclipse.jetty.webapp.WebAppContext
import org.hibernate.jpa.HibernatePersistenceProvider
import org.scalatra._
import org.scalatra.servlet.ScalatraListener
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{
  Bean, Configuration, ComponentScan, Import, AnnotationConfigApplicationContext
}
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.{ LocalContainerEntityManagerFactoryBean, JpaTransactionManager }
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.annotation.{ Transactional, EnableTransactionManagement }
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.{ Repository => DAO, Component => WiredSpringObject }
import scala.collection.JavaConverters._
import scala.collection.mutable.{ Map => MapMutavel }

class ActiveRecordLifeCycle extends org.scalatra.LifeCycle {
  lazy val logger = Logger.getLogger(classOf[ActiveRecordLifeCycle])
  override def init(context: javax.servlet.ServletContext) =
    ContextoAplicacao.classesNaPackage.filter(_.getSuperclass == classOf[ActiveRecordRest[_]])
      .map(classe => {
        val mapeamentoEndpointUrl = s"/${classe.getSimpleName.replaceAll("Rest", "").toLowerCase}/*"
        context.mount(classe, mapeamentoEndpointUrl)
        logger.info(s"Classe '${classe.getName}' montada com o endpoint '${mapeamentoEndpointUrl}'")
      })
}

abstract class ActiveRecordModel extends Serializable {
  private def buscarCampoPKRecursivamente(posicao: Int = 0): JAttribute = {
    val atributos: List[JAttribute] = getClass.getDeclaredFields.toList
    atributos(posicao).getDeclaredAnnotations.toList
      .filter(_.annotationType.equals(classOf[javax.persistence.Id]))
        .map(_.annotationType match {
          case id if(id == classOf[javax.persistence.Id]) => atributos(posicao)
          case _ => Try(buscarCampoPKRecursivamente(posicao = posicao + 1)) match {
            case Success(annotation) => annotation.asInstanceOf[JAttribute]
            case Failure(ex) => throw new IllegalStateException(
              s"Atributo com @javax.persistence.Id inexistente na entidade '${getClass.getSimpleName}'"
            )}}).head
  }
  @transient lazy val chavePrimaria =  Option(buscarCampoPKRecursivamente()).map(pk => { pk.setAccessible(true)
    pk }).getOrElse(null)
  def salvar: Any = Try(chavePrimaria.get(this)).toOption match {
      case Some(pk) => ContextoAplicacao.dao.atualizar(this)
      case None => ContextoAplicacao.dao.criar(this)
    }
  def apagar: Any = ContextoAplicacao.dao.apagar(this)
  def recarregar: Option[Any] = Try(chavePrimaria.get(this)) match {
      case Success(valor) => Option(ContextoAplicacao.dao.buscarPorPK(getClass, valor))
      case Failure(ex) => None
    }
  private def removerChavePrimariaFiltro: Any = {
    val tempChavePrimaria = chavePrimaria.get(this)
    chavePrimaria.set(this, null)
    tempChavePrimaria
  }
  def listarComoFiltro(registros: Integer = Integer.MAX_VALUE, pagina: Integer = 1): JList[_] = {
    val pk = removerChavePrimariaFiltro
    val lista = ContextoAplicacao.dao.listarComFiltro(this, registros, pagina)
    chavePrimaria.set(this, pk)
    lista
  }
  def contarComoFiltro: Long = {
    val pk = removerChavePrimariaFiltro
    val contador = ContextoAplicacao.dao.contarComFiltro(this)
    chavePrimaria.set(this, pk)
    contador
  }
  def json: String = ContextoAplicacao.gson.toJson(this)
}

abstract class ActiveRecordCompanion[M <: ActiveRecordModel](implicit tag: ClassTag[M]) extends javax.servlet.http.HttpServlet {
  def listarTodos(registros: Integer = Integer.MAX_VALUE, pagina: Integer = 1): List[M] =
    ContextoAplicacao.dao.listarTodos(tag.runtimeClass, registros, pagina).asInstanceOf[JList[M]].asScala.toList
  def listarComFiltro(filtro: M, registros: Integer = Integer.MAX_VALUE, pagina: Integer = 1): List[M] = {
    Option(filtro) match {
      case Some(instancia) => ContextoAplicacao.dao.listarComFiltro(instancia, registros, pagina).asInstanceOf[JList[M]].asScala.toList
      case None => listarTodos()
    }
  }
  def contarTodos: Long = ContextoAplicacao.dao.contarTodos(tag.runtimeClass)
  def contarComFiltro(filtro: M): Long = {
    Option(filtro) match {
      case Some(instancia) => ContextoAplicacao.dao.contarComFiltro(filtro)
      case None => contarTodos
    }
  }
  def buscarPorPK(pk: Any): Option[M] = Try(ContextoAplicacao.dao.buscarPorPK(tag.runtimeClass, pk).asInstanceOf[M]) match {
    case Success(instancia) => Option(instancia)
    case Failure(ex) => None
  }
  def fromJson(json: String): M = ContextoAplicacao.gson.fromJson(json, tag.runtimeClass)
}

class NenhumRegistroException extends IllegalAccessException("Nenhum registro encontrado.")
case class ErroRest(mensagem: String, detalhes: String = null) {
  def this(mensagem: String, ex: Throwable) =
    this(mensagem = mensagem, detalhes = {
      val sw = new java.io.StringWriter
      ex.printStackTrace(new java.io.PrintWriter(sw))
      sw.toString.split("\n").head
    })
}
case class RespostaFiltroRest(qtdRegistros: Long, totalRegistros: Long, paginaAtual: Integer, totalPaginas: Integer, registros: JList[Object]) {
  def this(lista: JList[_], pagina: Integer, registros: Long, totalRegistros: Long) =
    this(
      qtdRegistros = lista.size.toLong,
      totalRegistros = totalRegistros,
      paginaAtual = pagina,
      totalPaginas = (totalRegistros / registros).toInt + (if(totalRegistros % registros != 0) 1 else 0),
      registros = lista.asInstanceOf[JList[Object]]
    )

}
abstract class ActiveRecordRest[M <: ActiveRecordModel](implicit tag: ClassTag[M]) extends org.scalatra.ScalatraServlet {
  private lazy final val CONST_MENSAGEM_ERRO_BUSCAR_PK = "Ocorreu um erro ao buscar o registro."
  lazy val logger = Logger.getLogger(getClass)
  before() {
    contentType = if(request.getRequestURI == request.getServletPath) "text/html" else "application/json"
  }
  private def erroRest(mensagem: String, ex: Option[Throwable] = None) =
    ContextoAplicacao.gson.toJson(ex match {
      case Some(e) => { logger.error(mensagem, e)
        new ErroRest(mensagem, e) }
      case None => ErroRest(mensagem)
    })
  private def recuperarInstanciaDaPKStr: M = {
    var instancia: M = tag.runtimeClass.newInstance.asInstanceOf[M]
    val pk = params.get("pk").get
    instancia.chavePrimaria.set(instancia, instancia.chavePrimaria.getType match {
      case _long if(_long == classOf[Long] || _long == classOf[JLong]) => pk.toLong
      case _integer if(_integer == classOf[Integer] || _integer == classOf[JInteger]) => pk.toInt
      case _byte if(_byte == classOf[Byte] || _byte == classOf[JByte]) => pk.toByte
      case _boolean if(_boolean == classOf[Boolean] || _boolean == classOf[JBoolean]) => pk.toBoolean
      case _double if(_double == classOf[Double] || _double == classOf[JDouble]) => pk.toDouble
      case _float if(_float == classOf[Float] || _float == classOf[JFloat]) => pk.toFloat
      case _short if(_short == classOf[Short] || _short == classOf[JShort]) => pk.toShort
      case _character if(_character == classOf[Character] || _character == classOf[JChar]) => pk.toCharArray()(0)
      case _string if(_string == classOf[String]) => pk
      case _ => pk.asInstanceOf[java.lang.Object]
    })
    instancia.recarregar match {
      case Some(instanciaOk: M) => instanciaOk
      case None => throw new NenhumRegistroException
    }
  }
  get("/") {
    s"<h1>Olar ${tag.runtimeClass.getSimpleName}!</h1>"
  }
  get("/buscar/:pk") {
    Try(recuperarInstanciaDaPKStr) match {
      case Success(instancia: M) => Ok(instancia.json)
      case Failure(ex) => ex match {
        case _: NenhumRegistroException => NotFound(erroRest(ex.getMessage, None))
        case _ => InternalServerError(erroRest(CONST_MENSAGEM_ERRO_BUSCAR_PK, Some(ex)))
      }
    }
  }
  post("/listar") {
    val registros = params.getAsOrElse[Long]("registros", Integer.MAX_VALUE)
    val pagina = params.getAsOrElse[Int]("pagina", 1)
    val filtro: M = request.body.length match {
      case 0 => tag.runtimeClass.newInstance.asInstanceOf[M]
      case _ => ContextoAplicacao.gson.fromJson(request.body, tag.runtimeClass)
    }
    Ok(ContextoAplicacao.gson.toJson(
      new RespostaFiltroRest(filtro.listarComoFiltro(registros.toInt, pagina), pagina, registros, filtro.contarComoFiltro)
    ))
  }
  post("/salvar") {
    Try(ContextoAplicacao.gson.fromJson(request.body, tag.runtimeClass)) match {
      case Success(instancia: M) => Ok(instancia.salvar.asInstanceOf[M].json)
      case Failure(ex) => BadRequest(ContextoAplicacao.gson.toJson(ex.getMessage))
    }
  }
  delete("/apagar/:pk") {
    Try(recuperarInstanciaDaPKStr) match {
      case Success(instancia: M) => Ok(instancia.apagar.asInstanceOf[M].json)
      case Failure(ex) => Ok(ContextoAplicacao.gson.toJson(ex.getMessage))
    }
  }
}

class DAOSimples {

  @PersistenceContext
  var entityManager: EntityManager = _

  @Transactional(readOnly = false)
  def criar(instancia: Any) = {
    entityManager.persist(instancia)
    instancia
  }

  @Transactional(readOnly = false)
  def atualizar(instancia: Any) = entityManager.merge(instancia)

  @Transactional(readOnly = false)
  def apagar(instancia: Any): Any = {
    entityManager.remove(entityManager.merge(instancia))
    instancia
  }

  @Transactional(readOnly = true)
  def buscarPorPK(classe: Class[_], pk: Any): Any = entityManager.find(classe, pk)

  @Transactional(readOnly = true)
  def listarTodos(classe: Class[_], registros: Integer, pagina: Integer): JList[_] =
    entityManager.createQuery(s"FROM ${classe.getSimpleName}").setFirstResult((pagina - 1) * registros).setMaxResults(registros).getResultList

  @Transactional(readOnly = true)
  def listarComFiltro(filtro: Any, registros: Integer, pagina: Integer): JList[_] =
    gerarQuerydaInstancia(filtro, "").setFirstResult((pagina - 1) * registros).setMaxResults(registros).getResultList

  @Transactional(readOnly = true)
  def contarTodos(classe: Class[_]): Long = entityManager.createQuery(s"SELECT COUNT(t) FROM ${classe.getSimpleName} t").getSingleResult.asInstanceOf[Long]

  @Transactional(readOnly = true)
  def contarComFiltro(filtro: Any): Long = gerarQuerydaInstancia(filtro, "SELECT COUNT(1)").getSingleResult.asInstanceOf[Long]

  private def gerarQuerydaInstancia(filtro: Any, prefixoHQL: String): Query = {
    var hql = new ListBuffer[String]()
    var mapAtributoObjeto: MapMutavel[String, Any] = MapMutavel[String, Any]()
    filtro.getClass.getDeclaredFields.filter(_.getDeclaredAnnotations.toList.exists(_annt => {
        _annt.annotationType == classOf[javax.persistence.Column]    || _annt.annotationType == classOf[javax.persistence.ManyToOne] ||
        _annt.annotationType == classOf[javax.persistence.OneToMany] || _annt.annotationType == classOf[javax.persistence.ManyToMany]
    })).map(atributo => { atributo.setAccessible(true)
      Option(atributo.get(filtro)).map(valor => atributo.getType match {
        case str if (str == classOf[String]) =>
          (s"LOWER(${atributo.getName}) LIKE :${atributo.getName}", s"%${valor.toString.replaceAll("\\s+", "%").toLowerCase}%")
        case mdl if (mdl.getSuperclass == classOf[ActiveRecordModel]) => { val childObj = valor.asInstanceOf[ActiveRecordModel]
          (s"${atributo.getName}.${childObj.chavePrimaria.getName} = :${atributo.getName}", childObj.chavePrimaria.get(childObj)) }
        case _ => (s"${atributo.getName} = :${atributo.getName}", valor)
      }).map(mapHQLvalor => { hql += s"${if (!hql.isEmpty) "AND" else ""} ${mapHQLvalor._1}"
        mapAtributoObjeto = mapAtributoObjeto + (atributo.getName -> mapHQLvalor._2) })
    })
    Option(ContextoAplicacao.dao.entityManager.createQuery(
      s" ${prefixoHQL} FROM ${filtro.getClass.getSimpleName} ${if(!hql.isEmpty) s"WHERE ${hql.mkString(" ")}" else ""} ")
    ).map(query => { mapAtributoObjeto.map(atributoObjeto => query.setParameter(atributoObjeto._1, atributoObjeto._2))
      query }).get
  }
}

@Configuration
@EnableTransactionManagement
class ConfiguracaoSpringSimples

object ContextoAplicacao {
  protected lazy val logger: Logger = Logger.getLogger(getClass)
  private var contexto: ApplicationContext = null
  var packageRaizProjeto: String = null
  var camadaRestInicializada = false

  def classesNaPackage = {
    val provider = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false)
    provider.addIncludeFilter(new org.springframework.core.`type`.filter.RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*")))
    provider.findCandidateComponents(ContextoAplicacao.packageRaizProjeto).asScala.map(bean =>
      Class.forName(bean.asInstanceOf[org.springframework.beans.factory.config.BeanDefinition].getBeanClassName)).toList
  }

  def iniciar(
      ativarServidorWeb: JBoolean,
      portaWebApp: Integer,
      nome: String,
      prefixoContextoWebApp: String,
      prefixoPackage: String,
      urlJDBC: String,
      usuarioBancoDados: String,
      senhaBancoDados: String,
      driverDialetoJPA: DriverDialeto,
      hbm2ddl: HBM2DDL,
      exibirSQL: JBoolean,
      formatarSQL: JBoolean,
      usarOtimizadorReflection: JBoolean,
      diretorioResourcesWebApp: String,
      ativarJobManager: JBoolean
  ) = {
    val configuracaoAutomatica = {
      val properties = new JProperties
      properties.load(getClass.getClassLoader.getResourceAsStream("configuracoes.properties"))
      properties
    }
    val contexto = new AnnotationConfigApplicationContext(classOf[ConfiguracaoSpringSimples])
    val dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(urlJDBC, usuarioBancoDados, senhaBancoDados)
    dataSource.setDriverClassName(driverDialetoJPA.driver)
    val factory = new LocalContainerEntityManagerFactoryBean
    factory.setDataSource(dataSource)
    packageRaizProjeto = Option(prefixoPackage).getOrElse(configuracaoAutomatica.getProperty("spring.package.scan"))
    factory.setPackagesToScan(packageRaizProjeto)
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter)
    factory.setJpaProperties({
      val properties = new JProperties
      properties.put("hibernate.dialect", driverDialetoJPA.dialeto)
      properties.put("hibernate.show_sql", exibirSQL)
      properties.put("hibernate.format_sql", formatarSQL)
      properties.put("hibernate.hbm2ddl.auto", hbm2ddl.valor)
      properties.put("hibernate.cglib.use_reflection_optimizer", usarOtimizadorReflection);
      properties
    })
    factory.setPersistenceUnitName(s"${Option(nome)
      .getOrElse(configuracaoAutomatica.getProperty("projeto.nome").replaceAll("\\W", ""))}PersistenceUnit")
    factory.setPersistenceProviderClass(classOf[HibernatePersistenceProvider])
    factory.afterPropertiesSet
    val transactionManager = new JpaTransactionManager
    transactionManager.setEntityManagerFactory(factory.getObject)
    contexto.getBeanFactory.registerSingleton("dataSource", dataSource)
    contexto.getBeanFactory.registerSingleton("entityManagerFactory", factory.getObject)
    contexto.getBeanFactory.registerSingleton("transactionManager", transactionManager)
    contexto.register(classOf[DAOSimples])
    if(ativarServidorWeb) {
      val server = new Server(portaWebApp)
      server.setHandler({
        val webContext = new WebAppContext
        webContext setContextPath(prefixoContextoWebApp)
        webContext.setResourceBase(diretorioResourcesWebApp)
        webContext.addEventListener(new ScalatraListener)
        webContext.addServlet(classOf[DefaultServlet], "/")
        webContext
      })
      contexto.getBeanFactory.registerSingleton("server", server)
    }
    if(ativarJobManager) {
      val scheduler = (new org.quartz.impl.StdSchedulerFactory).getScheduler
      scheduler.start
      classesNaPackage.filter(_.getSuperclass.equals(classOf[ActiveRecordJob])).map(classe => {
        val nomeJob = s"${classe.getSimpleName.replaceAll("Job", "").toLowerCase}"
        val instanciaConfig = classe.newInstance.asInstanceOf[ActiveRecordJob]
        val jobDetail = new org.quartz.JobDetail
        jobDetail.setName(nomeJob)
        jobDetail.setJobClass(classe)
        val cronTrigger = new org.quartz.CronTrigger
        cronTrigger.setName(s"${nomeJob}Trigger")
        cronTrigger.setJobName(jobDetail.getName)
        cronTrigger.setStartTime(JCalendar.getInstance.getTime)
        cronTrigger.setCronExpression(instanciaConfig.expressaoCronFrequenciaExecucao)
        scheduler.addJob(jobDetail, JBoolean.TRUE)
        scheduler.scheduleJob(cronTrigger)
        logger.info(s"Classe '${classe.getName}' agendada como job '${nomeJob}'")
      })
      contexto.getBeanFactory.registerSingleton("scheduler", scheduler)
    }
    ContextoAplicacao.contexto = contexto
    if(ativarServidorWeb) {
      Try(contexto.getBean("server").asInstanceOf[Server]) match {
        case Success(server) =>
          Executors.newCachedThreadPool.execute(new Runnable {
            override def run = {
              server.start
              server.join
            }
          })
        case Failure(ex) => {
          ex.printStackTrace
          logger.fatal(ex.getMessage)
          System.exit(-1)
        }
      }
    }
  }
  private lazy val formatoData = "dd/MM/yyyy HH:mm:ss"
  lazy val dao = contexto.getBean(classOf[DAOSimples])
  lazy val gson = (new com.google.gson.GsonBuilder).disableHtmlEscaping.setDateFormat(formatoData).serializeNulls.setPrettyPrinting.create
  lazy val sdf = new java.text.SimpleDateFormat(formatoData)
}

abstract class ActiveRecordJob extends org.quartz.Job {
  protected lazy val logger: Logger = Logger.getLogger(getClass)
  def executar
  def expressaoCronFrequenciaExecucao: String
  override def execute(context: org.quartz.JobExecutionContext): Unit = {
    logger.debug(s"Executando Job '${getClass.getSimpleName}'...")
    val tempo = JCalendar.getInstance.getTimeInMillis
    executar
    logger.debug(s"Fim da execução do Job '${getClass.getSimpleName}' em ${(JCalendar.getInstance.getTimeInMillis - tempo) / 1000d} segundos")
  }
}

case class HBM2DDL(valor: String)
object HBM2DDL {
  val NADA         = HBM2DDL("none")
  val VALIDAR      = HBM2DDL("validate")
  val ATUALIZAR    = HBM2DDL("update")
  val DROPAR_CRIAR = HBM2DDL("create")
  val CRIAR_DROPAR = HBM2DDL("create-drop")
}

case class DriverDialeto(driver: String, dialeto: String)
object DriverDialeto {
  val H2             = DriverDialeto("org.h2.Driver",                                "org.hibernate.dialect.H2Dialect")
  val DERBY_EMBEDDED = DriverDialeto("org.apache.derby.jdbc.EmbeddedDriver",         "org.hibernate.dialect.DerbyDialect")
  val POSTGRESQL     = DriverDialeto("org.postgresql.Driver",                        "org.hibernate.dialect.PostgreSQLDialect")
  val POSTGRESPLUS   = DriverDialeto("org.postgresql.Driver",                        "org.hibernate.dialect.PostgresPlusDialect")
  val MYSQL          = DriverDialeto("com.mysql.jdbc.Driver",                        "org.hibernate.dialect.MySQLDialect")
  val MYSQL5         = DriverDialeto("com.mysql.jdbc.Driver",                        "org.hibernate.dialect.MySQL5Dialect")
  val FIREBIRD       = DriverDialeto("org.firebirdsql.jdbc.FBDriver",                "org.hibernate.dialect.FirebirdDialect")
  val HSQL           = DriverDialeto("org.hsqldb.jdbcDriver",                        "org.hibernate.dialect.HSQLDialect")
  val ORACLE10g_11g  = DriverDialeto("oracle.jdbc.driver.OracleDriver",              "org.hibernate.dialect.Oracle10gDialect")
  val ORACLE8i       = DriverDialeto("oracle.jdbc.driver.OracleDriver",              "org.hibernate.dialect.Oracle8iDialect")
  val ORACLE9        = DriverDialeto("oracle.jdbc.driver.OracleDriver",              "org.hibernate.dialect.Oracle9Dialect")
  val ORACLE9i       = DriverDialeto("oracle.jdbc.driver.OracleDriver",              "org.hibernate.dialect.Oracle9iDialect")
  val ORACLE         = DriverDialeto("oracle.jdbc.driver.OracleDriver",              "org.hibernate.dialect.OracleDialect")
  val SQLSERVER2005  = DriverDialeto("com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.hibernate.dialect.SQLServer2005Dialect")
  val SQLSERVER2008  = DriverDialeto("com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.hibernate.dialect.SQLServer2008Dialect")
  val SQLSERVER2012  = DriverDialeto("com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.hibernate.dialect.SQLServer2012Dialect")
  val SQLSERVER      = DriverDialeto("com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.hibernate.dialect.SQLServerDialect")
  val SYBASE11       = DriverDialeto("com.sybase.jdbc2.jdbc.SybDriver",              "org.hibernate.dialect.Sybase11Dialect")
  val SYBASEANYWHERE = DriverDialeto("com.sybase.jdbc3.jdbc.SybDriver",              "org.hibernate.dialect.SybaseAnywhereDialect")
  val SYBASE         = DriverDialeto("com.sybase.jdbc2.jdbc.SybDriver",              "org.hibernate.dialect.SybaseDialect")
  val TERADATA       = DriverDialeto("com.teradata.jdbc.TeraDriver",                 "org.hibernate.dialect.TeradataDialect")
}
