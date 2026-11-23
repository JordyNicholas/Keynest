package dev.keynest.manager.vault;

import dev.keynest.manager.vault.export.EncryptedVaultBackup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class VaultExportConfiguration {
  @Bean EncryptedVaultBackup encryptedVaultBackup() { return new EncryptedVaultBackup(); }
}
