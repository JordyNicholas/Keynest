package dev.keynest.manager.auth.verification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Construction does not connect, claim a job, send a message or change core health. */
@Configuration(proxyBeanMethods = false)
class VerificationMailConfiguration {
  @Bean(destroyMethod = "close")
  SmtpVerificationMail verificationMail(Environment environment) {
    return new SmtpVerificationMail(VerificationMailSettings.read(environment::getProperty));
  }
}
