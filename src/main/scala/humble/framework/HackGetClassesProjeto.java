package humble.framework;

public class HackGetClassesProjeto {

  public static java.util.List<Class<?>> listarClassesNaPackage(final String package_) {
    java.util.List<Class<?>> listaSaida = new java.util.ArrayList<Class<?>>();
    org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider provider =
      new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new org.springframework.core.type.filter.RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*")));
    java.util.Set<org.springframework.beans.factory.config.BeanDefinition> classes = provider.findCandidateComponents(package_);
    for(org.springframework.beans.factory.config.BeanDefinition bean: classes)
      try { listaSaida.add(Class.forName(bean.getBeanClassName())); }
      catch (ClassNotFoundException e) { /*ignorar*/ }
    return listaSaida;
  }

}
