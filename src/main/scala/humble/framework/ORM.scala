package humble.framework

import scala.reflect.ClassTag
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

abstract class Model extends Serializable {
  def salvar = SpringContext.dao.criar(this)
  def qualClasse = this.getClass.getSimpleName
}

abstract class ActiveRecord[M <: Model](implicit tag: ClassTag[M]) {
  def listarTodos: List[M] = SpringContext.dao.listarTodos(tag.runtimeClass).asInstanceOf[JList[M]].asScala.toList
}

@DAO
class HumbleDAO {

  @PersistenceContext
  var entityManager: EntityManager = _

  @Transactional(readOnly = false)
  def criar(instancia: Any) = this.entityManager.persist(instancia)

  @Transactional(readOnly = false)
  def atualizar(instancia: Any) = this.entityManager.merge(instancia)

  @Transactional(readOnly = true)
  def listarTodos(classe: Class[_]): JList[_] = this.entityManager.createQuery(s"FROM ${classe.getSimpleName}").getResultList

  @Transactional(readOnly = true)
  def contarTodos(classe: Class[_]): Long = this.entityManager.createQuery(s"SELECT COUNT(t) FROM ${classe.getSimpleName} t").getSingleResult.asInstanceOf[Long]

}

object SpringContext {

  lazy val contexto = new AnnotationConfigApplicationContext(classOf[ConfiguracaoSpring])
  lazy val dao = SpringContext.contexto.getBean(classOf[HumbleDAO])

}

@Configuration
@EnableTransactionManagement //(proxyTargetClass = true)
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
