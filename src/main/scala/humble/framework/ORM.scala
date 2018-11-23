package humble.framework

import scala.reflect.ClassTag
import annotation.meta.param
import scala.util.{ Try, Success, Failure }
import java.lang.reflect.{ Field => JAttribute }
import java.io.{ Serializable => JSerial }
import java.util.{ List => JList, ArrayList => JArrayList, Properties => JProperties }
import javax.persistence.{ PersistenceContext, EntityManager, EntityManagerFactory, Transient }
import javax.sql.DataSource
import org.hibernate.jpa.HibernatePersistenceProvider
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
  
  def primaryKeyClass: Class[_] = tag.runtimeClass
  
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
}

@DAO
class HumbleDAO {

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
  lazy val contexto = new AnnotationConfigApplicationContext(classOf[ConfiguracaoSpring])
  lazy val dao = SpringContext.contexto.getBean(classOf[HumbleDAO])
  lazy val gson = new com.google.gson.GsonBuilder()
                        .disableHtmlEscaping
                        .setDateFormat("dd/MM/yyyy HH:mm:ss")
                        .setPrettyPrinting
                        .create
}

@Configuration
@EnableTransactionManagement
@ComponentScan(Array("humble.*"))
@EnableJpaRepositories(Array("humble"))
class ConfiguracaoSpring {

  @Bean
  def dataSource: DataSource = {
    var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource
		dataSource.setDriverClassName("org.h2.Driver")
		dataSource.setUrl("jdbc:h2:./arquivos/bancoDados;CIPHER=AES;FILE_LOCK=SOCKET;")
		dataSource.setUsername("admin")
		dataSource.setPassword("bc0259c5c27fea4a09afb897d581c970 cabd7e3571d4cccb57f130c6fa919a0a")
		dataSource
  }

  def hibernateProperties: JProperties = {
		var properties = new JProperties
		properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
		properties.put("hibernate.show_sql", java.lang.Boolean.TRUE)
		properties.put("hibernate.format_sql", java.lang.Boolean.TRUE)
		properties.put("hibernate.hbm2ddl.auto", "update")
		properties.put("hibernate.cglib.use_reflection_optimizer", java.lang.Boolean.FALSE);
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
		factory.setPackagesToScan("humble")
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter)
		factory.setJpaProperties(this.hibernateProperties)
		factory.setPersistenceUnitName("humblePersistenceUnit")
		factory.setPersistenceProviderClass(classOf[HibernatePersistenceProvider])
		factory.afterPropertiesSet
		return factory.getObject
  }

}
