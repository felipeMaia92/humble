package humble.framework

import scala.reflect.ClassTag
import annotation.meta.param
import scala.util.{ Try, Success, Failure }
import java.lang.{ Boolean => JBoolean }
import java.lang.reflect.{ Field => JAttribute }
import java.io.{ Serializable => JSerial }
import java.util.{ List => JList, ArrayList => JArrayList, Properties => JProperties }
import javax.persistence.{ PersistenceContext, EntityManager, EntityManagerFactory, Transient }
import javax.sql.DataSource
import org.hibernate.jpa.HibernatePersistenceProvider
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{ Bean, Configuration, ComponentScan }
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.{ LocalContainerEntityManagerFactoryBean, JpaTransactionManager }
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.annotation.{ Transactional, EnableTransactionManagement }
import org.springframework.stereotype.{ Repository => DAO, Component => WiredSpringObject }
import scala.collection.JavaConverters._

abstract class ActiveRecordModel[PK <: JSerial](implicit @(transient @param) tag: ClassTag[PK]) extends Serializable {

  def salvar =
    this.primaryKey match {
      case Some(pk) => SpringContext.dao.atualizar(this)
      case None => SpringContext.dao.criar(this)
    }

  def apagar = SpringContext.dao.apagar(this)

  @transient private val atributos: List[JAttribute] = this.getClass.getDeclaredFields.toList
  @transient private lazy val CONST_ANNOTATION_ID_JPA = classOf[javax.persistence.Id]

  private def buscarPKNoAtributo(posicao: Int): List[JAttribute] = {
    val annotations = atributos(posicao).getDeclaredAnnotations.toList
    annotations.takeWhile(_.annotationType.equals(classOf[javax.persistence.Id]))
      .map(
        _.annotationType match {
          case CONST_ANNOTATION_ID_JPA => this.atributos(posicao)
          case _ => Try(buscarPKNoAtributo(posicao + 1)) match {
            case Success(annotation) => annotation.asInstanceOf[JAttribute]
            case Failure(ex) => throw new IllegalAccessException
          }
        }
      )
  }

  def primaryKey: Option[PK] = {
    val pk = this.buscarPKNoAtributo(0).head
    pk.setAccessible(true)
    Try(pk.get(this)) match {
      case Success(valor) => Option(valor.asInstanceOf[PK])
      case Failure(ex) => None
    }
  }

  def json: String = SpringContext.gson.toJson(this)

}

abstract class ActiveRecordCompanion[M <: ActiveRecordModel[_]](implicit tag: ClassTag[M]) {
  def listarTodos: List[M] = SpringContext.dao.listarTodos(tag.runtimeClass).asInstanceOf[JList[M]].asScala.toList
  def contarTodos: Long = SpringContext.dao.contarTodos(tag.runtimeClass)
  def buscarPorPK(pk: Any): Option[M] = Try(SpringContext.dao.buscarPorPK(tag.runtimeClass, pk).asInstanceOf[M]) match {
    case Success(instancia) => Option(instancia)
    case Failure(ex) => None
  }
  def fromJson(json: String): M = SpringContext.gson.fromJson(json, tag.runtimeClass)
}

@DAO
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



object SpringContext {

  private var contexto: AnnotationConfigApplicationContext = null

  def inicializar(
        nome: String = "humble",
        prefixoPackage: String = "humble",
        url: String = "jdbc:h2:./arquivos/bancoDados;CIPHER=AES;FILE_LOCK=SOCKET;",
        usuario: String = "admin",
        senha: String = "bc0259c5c27fea4a09afb897d581c970 cabd7e3571d4cccb57f130c6fa919a0a",
        driverDialeto: DriverDialeto = DriverDialeto.H2,
        hbm2ddl: HBM2DDL = HBM2DDL.ATUALIZAR,
        exibirSQL: JBoolean = true,
        formatarSQLExibido: JBoolean = true,
        usarOtimizadorReflection: JBoolean = true
  ) = {
    this.contexto = new AnnotationConfigApplicationContext(classOf[ConfiguracaoSpring])
    this.contexto.scan(s"${prefixoPackage}.*")
    this.contexto.refresh
    this.contexto.start
  }

  lazy val dao = SpringContext.contexto.getBean(classOf[DAOSimples])
  lazy val gson = new com.google.gson.GsonBuilder()
                        .disableHtmlEscaping
                        .setDateFormat("dd/MM/yyyy HH:mm:ss")
                        .setPrettyPrinting
                        .create
}

@Configuration
@EnableTransactionManagement
class ConfiguracaoSpring(
        val nome: String,
        val prefixoPackage: String,
        val url: String,
        val usuario: String,
        val senha: String,
        val driverDialeto: DriverDialeto,
        val hbm2ddl: HBM2DDL,
        val exibirSQL: JBoolean,
        val formatarSQLExibido: JBoolean,
        val usarOtimizadorReflection: JBoolean) {

  @Bean
  def dataSource: DataSource = {
    var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource
		dataSource.setDriverClassName(this.driverDialeto.driver)
		dataSource.setUrl(this.url)
		dataSource.setUsername(this.usuario)
		dataSource.setPassword(this.senha)
		dataSource
  }

  def hibernateProperties: JProperties = {
		var properties = new JProperties
		properties.put("hibernate.dialect", this.driverDialeto.dialeto)
		properties.put("hibernate.show_sql", this.exibirSQL)
		properties.put("hibernate.format_sql", this.formatarSQLExibido)
		properties.put("hibernate.hbm2ddl.auto", this.hbm2ddl.valor)
		properties.put("hibernate.cglib.use_reflection_optimizer", this.usarOtimizadorReflection);
		properties
  }

  @Bean
  def transactionManager: JpaTransactionManager = {
    var transactionManager = new JpaTransactionManager
    transactionManager.setEntityManagerFactory(this.entityManagerFactory)
    transactionManager
  }

  @Bean
  def entityManagerFactory: EntityManagerFactory = {
    var factory = new LocalContainerEntityManagerFactoryBean
    factory.setDataSource(this.dataSource)
		factory.setPackagesToScan(this.prefixoPackage)
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter)
		factory.setJpaProperties(this.hibernateProperties)
		factory.setPersistenceUnitName(s"${this.nome}PersistenceUnit")
		factory.setPersistenceProviderClass(classOf[HibernatePersistenceProvider])
		factory.afterPropertiesSet
		return factory.getObject
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
