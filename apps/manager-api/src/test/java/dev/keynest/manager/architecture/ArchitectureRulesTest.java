package dev.keynest.manager.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import dev.keynest.architecturefixtures.TransportDependentService;
import dev.keynest.policy.architecturefixtures.ManagerDependentPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests production bytecode from the Manager and its three library dependencies. */
class ArchitectureRulesTest {

  private static final JavaClasses PRODUCTION = new ClassFileImporter()
    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
    .importPackages("dev.keynest.manager", "dev.keynest.policy", "dev.keynest.generate", "dev.keynest.seal");

  private static final ArchRule LIBRARIES_STAY_INDEPENDENT = noClasses()
    .that().resideInAnyPackage("dev.keynest.policy..", "dev.keynest.generate..", "dev.keynest.seal..")
    .should().dependOnClassesThat().resideInAnyPackage(
      "dev.keynest.manager..", "org.springframework..", "jakarta.persistence..", "jakarta.servlet..")
    .because("public libraries must remain independent of Manager, Spring and persistence/servlet frameworks")
    .allowEmptyShould(false);

  private static final ArchRule SERVICES_DO_NOT_DEPEND_ON_API = noClasses()
    .that().areAnnotatedWith(Service.class)
    .should().dependOnClassesThat().resideInAPackage("dev.keynest.manager.api..")
    .because("use cases must not depend on Manager HTTP edge code")
    .allowEmptyShould(false);

  private static final ArchRule SERVICES_DO_NOT_CALL_CONTROLLERS = noClasses()
    .that().areAnnotatedWith(Service.class)
    .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
    .because("controllers delegate to use cases, including controllers outside the api package")
    .allowEmptyShould(false);

  @Test
  void productionImportsContainEveryModuleAndExcludeNegativeFixtures() {
    assertThat(PRODUCTION).extracting(JavaClass::getName).contains(
      "dev.keynest.policy.PasswordPolicy", "dev.keynest.generate.PasswordGenerator",
      "dev.keynest.seal.PasswordHasher", "dev.keynest.manager.auth.AuthService",
      "dev.keynest.manager.auth.PasswordChangeService", "dev.keynest.manager.vault.VaultExportService");
    assertThat(PRODUCTION).noneMatch(type -> type.getPackageName().contains("architecturefixtures"));
  }

  @Test
  void librariesStayIndependent() {
    LIBRARIES_STAY_INDEPENDENT.check(PRODUCTION);
  }

  @Test
  void servicesDoNotDependOnApi() {
    SERVICES_DO_NOT_DEPEND_ON_API.check(PRODUCTION);
  }

  @Test
  void servicesDoNotCallControllers() {
    SERVICES_DO_NOT_CALL_CONTROLLERS.check(PRODUCTION);
  }

  @Test
  void rejectsLibraryDependenciesOnManagerAndSpring() {
    JavaClasses badLibrary = new ClassFileImporter().importClasses(ManagerDependentPolicy.class);
    assertThatThrownBy(() -> LIBRARIES_STAY_INDEPENDENT.check(badLibrary))
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("dev.keynest.manager.ManagerApiApplication")
      .hasMessageContaining("org.springframework.stereotype.Service");
  }

  @Test
  void rejectsServiceDependencyOnHttpEdge() {
    JavaClasses badService = new ClassFileImporter().importClasses(TransportDependentService.class);
    assertThatThrownBy(() -> SERVICES_DO_NOT_DEPEND_ON_API.check(badService))
      .isInstanceOf(AssertionError.class).hasMessageContaining("dev.keynest.manager.api.ApiPaths");
  }

  @Test
  void rejectsServiceDependencyOnControllerOutsideApiPackage() {
    JavaClasses badService = new ClassFileImporter().importClasses(TransportDependentService.class);
    assertThatThrownBy(() -> SERVICES_DO_NOT_CALL_CONTROLLERS.check(badService))
      .isInstanceOf(AssertionError.class).hasMessageContaining("dev.keynest.manager.auth.AuthController");
  }

  @Test
  void emptyRuleSelectionsFailInsteadOfPassingSilently() {
    JavaClasses unrelated = new ClassFileImporter().importClasses(String.class);
    for (ArchRule rule : new ArchRule[] {
      LIBRARIES_STAY_INDEPENDENT, SERVICES_DO_NOT_DEPEND_ON_API, SERVICES_DO_NOT_CALL_CONTROLLERS
    }) {
      assertThatThrownBy(() -> rule.check(unrelated)).isInstanceOf(AssertionError.class)
        .hasMessageContaining("failed to check any classes");
    }
  }
}
