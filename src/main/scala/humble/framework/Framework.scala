package humble.framework

import scala.reflect.ClassTag
import scala.util.{ Try, Success, Failure }
import java.lang.{ Boolean => JBoolean }
import java.lang.reflect.{ Field => JAttribute }
import java.io.{ Serializable => JSerial, File => JFile }
import java.util.{ List => JList, ArrayList => JArrayList, Properties => JProperties }
import javax.persistence.{ PersistenceContext, EntityManager, EntityManagerFactory, Transient, Query }
import javax.sql.DataSource
import org.apache.log4j.Logger
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ DefaultServlet, ServletContextHandler }
import org.eclipse.jetty.webapp.WebAppContext
import org.hibernate.jpa.HibernatePersistenceProvider
import org.scalatra.{ Ok, BadRequest }
import org.scalatra.servlet.ScalatraListener
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{ Bean, Configuration, ComponentScan, Import, AnnotationConfigApplicationContext }
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.{ LocalContainerEntityManagerFactoryBean, JpaTransactionManager }
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.annotation.{ Transactional, EnableTransactionManagement }
import org.springframework.stereotype.{ Repository => DAO/*, Component => WiredSpringObject*/ }
import scala.collection.JavaConverters._
import scala.collection.mutable.{ Map => MapMutavel }

class ActiveLifeCycle extends org.scalatra.LifeCycle {
  lazy val logger = Logger.getLogger(classOf[ActiveLifeCycle])
  override def init(context: javax.servlet.ServletContext) {
    HackGetClassesProjeto.listarClassesNaPackage(ContextoAplicacao.packageRaizProjeto)
      .asScala.filter(_.getSuperclass().equals(classOf[ActiveRecordRest[_]])).map(classe => {
        val mapeamentoEndpointUrl = s"/${classe.getSimpleName.replaceAll("Rest", "").toLowerCase}/*"
        context.mount(classe, mapeamentoEndpointUrl)
        this.logger.info(s"Classe '${classe.getName}' montada com o endpoint '${mapeamentoEndpointUrl}'")
    })
  }
}

abstract class ActiveRecordModel extends Serializable {
  @transient lazy val chavePrimaria: JAttribute = {
    def buscarPKNoAtributo(posicao: Int): JAttribute = {
      lazy val CONST_ANNOTATION_ID_JPA = classOf[javax.persistence.Id]
      val atributos: List[JAttribute] = this.getClass.getDeclaredFields.toList
      atributos(posicao).getDeclaredAnnotations.toList
        .filter(_.annotationType.equals(classOf[javax.persistence.Id]))
          .map(
            _.annotationType match {
              case CONST_ANNOTATION_ID_JPA => atributos(posicao)
              case _ => Try(buscarPKNoAtributo(posicao + 1)) match {
                case Success(annotation) => annotation.asInstanceOf[JAttribute]
                case Failure(ex) => throw new IllegalAccessException(
                  s"Atributo com @javax.persistence.Id inexistente na entidade '${this.getClass.getSimpleName}'"
                )
              }
            }
          ).head
    }
    val saida = buscarPKNoAtributo(0)
    saida.setAccessible(true)
    saida
  }
  def salvar = {
    val primaryKey = Try(this.chavePrimaria.get(this)) match {
      case Success(valor) => Option(valor)
      case Failure(ex) => None
    }
    primaryKey match {
      case Some(pk) => ContextoAplicacao.dao.atualizar(this)
      case None => ContextoAplicacao.dao.criar(this)
    }
  }
  def apagar = ContextoAplicacao.dao.apagar(this)
  def recarregar: Option[Any] = {
    Try(this.chavePrimaria.get(this)) match {
      case Success(valor) => Option(ContextoAplicacao.dao.buscarPorPK(this.getClass, valor))
      case Failure(ex) => None
    }
  }
  private def removerChavePrimariaFiltro(instancia: Any): Any = {
    val tempChavePrimaria = this.chavePrimaria.get(this)
    this.chavePrimaria.set(this, null)
    tempChavePrimaria
  }
  def listarComoFiltro(registros: Integer = Integer.MAX_VALUE, pagina: Integer = 1): JList[_] = {
    val pk = this.removerChavePrimariaFiltro(this)
    val lista = ContextoAplicacao.dao.listarComFiltro(this, registros, pagina)
    this.chavePrimaria.set(this, pk)
    lista
  }
  def contarComoFiltro: Long = {
    val pk = this.removerChavePrimariaFiltro(this)
    val contador = ContextoAplicacao.dao.contarComFiltro(this)
    this.chavePrimaria.set(this, pk)
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
      case None => this.listarTodos()
    }
  }
  def contarTodos: Long = ContextoAplicacao.dao.contarTodos(tag.runtimeClass)
  def contarComFiltro(filtro: M): Long = {
    Option(filtro) match {
      case Some(instancia) => ContextoAplicacao.dao.contarComFiltro(filtro)
      case None => this.contarTodos
    }
  }
  def buscarPorPK(pk: Any): Option[M] = Try(ContextoAplicacao.dao.buscarPorPK(tag.runtimeClass, pk).asInstanceOf[M]) match {
    case Success(instancia) => Option(instancia)
    case Failure(ex) => None
  }
  def fromJson(json: String): M = ContextoAplicacao.gson.fromJson(json, tag.runtimeClass)
}

case class RespostaFiltroRest(lista: JList[Object], registros: Long, totalRegistros: Long, paginaAtual: Integer, totalPaginas: Integer)
abstract class ActiveRecordRest[M <: ActiveRecordModel](implicit tag: ClassTag[M]) extends org.scalatra.ScalatraServlet {

  before() {
    contentType = "application/json"
  }

  get("/buscar/:pk") {
    // FIXME Cara, isso tá porco
    var instancia: M = tag.runtimeClass.newInstance.asInstanceOf[M]
    val pk = params.get("pk").get
    val pkNormalizada = instancia.chavePrimaria.getType.getSimpleName match {
      case "Long" =>            pk.toLong
      case "Integer" | "Int" => pk.toInt
      case "Byte" =>            pk.toByte
      case "Boolean" =>         pk.toBoolean
      case "Double" =>          pk.toDouble
      case "Float" =>           pk.toFloat
      case "Short" =>           pk.toShort
      case "Character" =>       pk.toCharArray()(0)
      case "String" =>          pk
      case "Date" => ContextoAplicacao.sdf.parse(pk)
      case _ => pk.asInstanceOf[java.lang.Object]
    }
    instancia.chavePrimaria.set(instancia, pkNormalizada)
    instancia.recarregar match {
      case Some(instancia: M) => Ok(instancia.json)
      case None => Ok(ContextoAplicacao.gson.toJson("Nenhum registro encontrado."))
    }
  }

  post("/listar") {
    val registros = params.getAsOrElse[Long]("registros", Integer.MAX_VALUE)
    val pagina = params.getAsOrElse[Int]("pagina", 1)
    val filtro: M = request.body.length match {
      case 0 => tag.runtimeClass.newInstance.asInstanceOf[M]
      case _ => ContextoAplicacao.gson.fromJson(request.body, tag.runtimeClass)
    }
    val lista = filtro.listarComoFiltro(registros.toInt, pagina).asInstanceOf[JList[Object]]
    val totalRegistros = filtro.contarComoFiltro
    val parcialTotalPaginas = (totalRegistros / registros).toInt
    Ok(ContextoAplicacao.gson.toJson(
      RespostaFiltroRest(lista, lista.size.toLong, totalRegistros, pagina,parcialTotalPaginas + (if(totalRegistros % registros != 0) 1 else 0))
    ))
  }

  post("/salvar") {
    Try(ContextoAplicacao.gson.fromJson(request.body, tag.runtimeClass)) match {
      case Success(instancia: M) => {
        instancia.salvar
        Ok(instancia.json)
      }
      case Failure(ex) => BadRequest(ContextoAplicacao.gson.toJson(ex.getMessage))
    }
  }

  delete("/apagar/:pk") {
    var instancia: M = tag.runtimeClass.newInstance.asInstanceOf[M]
    instancia.chavePrimaria.set(instancia, params.get("pk").get)
    instancia.recarregar match {
      case Some(temp: M) => {
        temp.apagar
        Ok(temp.json)
      }
      case None => Ok(ContextoAplicacao.gson.toJson("Nenhum registro encontrado."))
    }
  }

}

class DAOSimples {

  @PersistenceContext
  var entityManager: EntityManager = _

  @Transactional(readOnly = false)
  def criar(instancia: Any) = this.entityManager.persist(instancia)

  @Transactional(readOnly = false)
  def atualizar(instancia: Any) = this.entityManager.merge(instancia)

  @Transactional(readOnly = false)
  def apagar(instancia: Any) = this.entityManager.remove(this.entityManager.merge(instancia))

  @Transactional(readOnly = true)
  def buscarPorPK(classe: Class[_], pk: Any): Any = this.entityManager.find(classe, pk)

  @Transactional(readOnly = true)
  def listarTodos(classe: Class[_], registros: Integer, pagina: Integer): JList[_] =
    this.entityManager.createQuery(s"FROM ${classe.getSimpleName}").setFirstResult((pagina - 1) * registros).setMaxResults(registros).getResultList

  @Transactional(readOnly = true)
  def listarComFiltro(filtro: Any, registros: Integer, pagina: Integer): JList[_] =
    this.gerarQuerydaInstancia(filtro, "").setFirstResult((pagina - 1) * registros).setMaxResults(registros).getResultList

  @Transactional(readOnly = true)
  def contarTodos(classe: Class[_]): Long = this.entityManager.createQuery(s"SELECT COUNT(t) FROM ${classe.getSimpleName} t").getSingleResult.asInstanceOf[Long]

  @Transactional(readOnly = true)
  def contarComFiltro(filtro: Any): Long = this.gerarQuerydaInstancia(filtro, "SELECT COUNT(1)").getSingleResult.asInstanceOf[Long]

  private def gerarQuerydaInstancia(filtro: Any, prefixoHQL: String): Query = {
    lazy val CONST_ANNOTATION_COLUMN_JPA = classOf[javax.persistence.Column]
    val hql = new StringBuilder
    var mapAtributoObjeto: MapMutavel[String, Any] = MapMutavel[String, Any]()
    filtro.getClass.getDeclaredFields.toList.map(atributo =>
      atributo.getDeclaredAnnotations.toList.map(
        _.annotationType match {
          case CONST_ANNOTATION_COLUMN_JPA => {
            atributo.setAccessible(true)
            Try(atributo.get(filtro)) match {
              case Success(getOk) => {
                Option(getOk) match {
                  case Some(valor) => {
                    if(!hql.toString.isEmpty) hql.append("AND ")
                    if(atributo.getType.getName.endsWith(".String")) {
                      hql.append(s"LOWER(${atributo.getName}) LIKE :${atributo.getName} ")
                      mapAtributoObjeto = mapAtributoObjeto + (atributo.getName -> s"%${valor.toString.replaceAll("\\s+", "%").toLowerCase}%")
                    }
                    else {
                      hql.append(s"${atributo.getName} = :${atributo.getName} ")
                      mapAtributoObjeto = mapAtributoObjeto + (atributo.getName -> valor)
                    }
                  }
                  case None => Unit
                }
              }
              case Failure(ex) => Unit
            }
          }
          case _ => Unit
        }
      )
    )
    val query = ContextoAplicacao.dao.entityManager.createQuery(s"""
          ${prefixoHQL} FROM ${filtro.getClass.getSimpleName}
          ${if(hql.toString != null && hql.length > 0) s"WHERE ${hql.toString}" else ""}
    """)
    mapAtributoObjeto.map(atributoObjeto => query.setParameter(atributoObjeto._1, atributoObjeto._2))
    query
  }
}

@Configuration
@EnableTransactionManagement
class ConfiguracaoSpringSimples

object ContextoAplicacao {
  private var contexto: ApplicationContext = null
  var packageRaizProjeto: String = null
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
      diretorioResourcesWebApp: String
  ) = {
    val configuracaoAutomatica = {
      val properties = new JProperties
      properties.load(this.getClass.getClassLoader.getResourceAsStream("configuracoes.properties"))
      properties
    }
    val contexto = new AnnotationConfigApplicationContext(classOf[ConfiguracaoSpringSimples])
    val dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(urlJDBC, usuarioBancoDados, senhaBancoDados)
    dataSource.setDriverClassName(driverDialetoJPA.driver)
    val factory = new LocalContainerEntityManagerFactoryBean
    factory.setDataSource(dataSource)
    this.packageRaizProjeto = Option(prefixoPackage).getOrElse(configuracaoAutomatica.getProperty("spring.package.scan"))
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
    this.contexto = contexto
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
      server.start
      server.join
    }
  }
  private lazy val formatoData = "dd/MM/yyyy HH:mm:ss"
  lazy val dao = ContextoAplicacao.contexto.getBean(classOf[DAOSimples])
  lazy val gson = (new com.google.gson.GsonBuilder).disableHtmlEscaping
    .setDateFormat(formatoData).serializeNulls.setPrettyPrinting.create
  lazy val sdf = new java.text.SimpleDateFormat(formatoData)
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
