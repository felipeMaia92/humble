package humble.framework

import scala.reflect.ClassTag
import scala.util.{ Try, Success, Failure }
import java.lang.{ Boolean => JBoolean }
import java.lang.reflect.{ Field => JAttribute }
import java.io.{ Serializable => JSerial, File => JFile }
import java.util.{ List => JList, ArrayList => JArrayList, Properties => JProperties }
import javax.persistence.{ PersistenceContext, EntityManager, EntityManagerFactory, Transient }
import javax.sql.DataSource
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ DefaultServlet, ServletContextHandler }
import org.eclipse.jetty.webapp.WebAppContext
import org.hibernate.jpa.HibernatePersistenceProvider
import org.scalatra.servlet.ScalatraListener
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{ Bean, Configuration, ComponentScan, Import, AnnotationConfigApplicationContext }
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.{ LocalContainerEntityManagerFactoryBean, JpaTransactionManager }
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.annotation.{ Transactional, EnableTransactionManagement }
import org.springframework.stereotype.{ Repository => DAO, Component => WiredSpringObject }
import scala.collection.JavaConverters._

class LifeCycle extends org.scalatra.LifeCycle {

  override def init(context: javax.servlet.ServletContext) {
    context.mount(classOf[humble.model.TesteRest], "/*")
  }
  
}

abstract class ActiveRecordModel extends Serializable {
  
  @transient private lazy val pk: JAttribute = {
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
                  s"Atributo com @javax.persistence.Id não encontrado na entidade '${this.getClass.getSimpleName}'"
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
    val primaryKey = Try(this.pk.get(this)) match {
      case Success(valor) => Option(valor)
      case Failure(ex) => None
    }
    primaryKey match {
      case Some(pk) => ContextoAplicacao.dao.atualizar(this)
      case None => ContextoAplicacao.dao.criar(this)
    }
  }
  def apagar = ContextoAplicacao.dao.apagar(this)
  def json: String = ContextoAplicacao.gson.toJson(this)
}

abstract class ActiveRecordCompanion[M <: ActiveRecordModel](implicit tag: ClassTag[M]) extends javax.servlet.http.HttpServlet {
  def listarTodos: List[M] = ContextoAplicacao.dao.listarTodos(tag.runtimeClass).asInstanceOf[JList[M]].asScala.toList
  def contarTodos: Long = ContextoAplicacao.dao.contarTodos(tag.runtimeClass)
  def buscarPorPK(pk: Any): Option[M] = Try(ContextoAplicacao.dao.buscarPorPK(tag.runtimeClass, pk).asInstanceOf[M]) match {
    case Success(instancia) => Option(instancia)
    case Failure(ex) => None
  }
  def fromJson(json: String): M = ContextoAplicacao.gson.fromJson(json, tag.runtimeClass)
  def entityManager: EntityManager = ContextoAplicacao.dao.entityManager
  def listarPorHQL(hql: String): List[M] = this.entityManager.createQuery(hql).getResultList.asInstanceOf[JList[M]].asScala.toList
  def listarPorSQL(sql: String): List[M] = this.entityManager.createNativeQuery(sql).getResultList.asInstanceOf[JList[M]].asScala.toList
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
  def listarTodos(classe: Class[_]): JList[_] = this.entityManager.createQuery(s"FROM ${classe.getSimpleName}").getResultList

  @Transactional(readOnly = true)
  def contarTodos(classe: Class[_]): Long = this.entityManager.createQuery(s"SELECT COUNT(t) FROM ${classe.getSimpleName} t").getSingleResult.asInstanceOf[Long]

  @Transactional(readOnly = true)
  def buscarPorPK(classe: Class[_], pk: Any): Any = this.entityManager.find(classe, pk)

}

@Configuration
@EnableTransactionManagement
class ConfiguracaoSpringSimples

object ContextoAplicacao {
  private var contexto: ApplicationContext = null
  def iniciar(
      nome: String = null,
      prefixoPackage: String = null,
      url: String = s"jdbc:h2:./arquivos/bancoDados;FILE_LOCK=SOCKET;",
      usuario: String = "admin",
      senha: String = "",
      driverDialeto: DriverDialeto = DriverDialeto.H2,
      hbm2ddl: HBM2DDL = HBM2DDL.ATUALIZAR,
      exibirSQL: JBoolean = false,
      formatarSQL: JBoolean = true,
      usarOtimizadorReflection: JBoolean = true,
      portaWebApp: Integer = 8080,
      prefixoContextoWebApp: String = "/",
      diretorioResourcesWebApp: String = "src/main/webapp"
  ) = {
		val configuracaoAutomatica = {
      val properties = new JProperties
      properties.load(this.getClass.getClassLoader.getResourceAsStream("configuracoes.properties"))
      properties
    }
    val contexto = new AnnotationConfigApplicationContext(classOf[ConfiguracaoSpringSimples])
    val dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, usuario, senha)
		dataSource.setDriverClassName(driverDialeto.driver)
		val factory = new LocalContainerEntityManagerFactoryBean
    factory.setDataSource(dataSource)
		factory.setPackagesToScan(Option(prefixoPackage).getOrElse(configuracaoAutomatica.getProperty("spring.package.scan")))
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter)
		factory.setJpaProperties({
		  val properties = new JProperties
  		properties.put("hibernate.dialect", driverDialeto.dialeto)
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
  lazy val dao = ContextoAplicacao.contexto.getBean(classOf[DAOSimples])
  lazy val gson = (new com.google.gson.GsonBuilder).disableHtmlEscaping.setDateFormat("dd/MM/yyyy HH:mm:ss").serializeNulls.setPrettyPrinting.create
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
