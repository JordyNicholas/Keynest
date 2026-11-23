package dev.keynest.manager.vault;

import dev.keynest.manager.vault.export.PortableVaultFormat;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import static dev.keynest.manager.vault.VaultExportFailure.Code.*;

public record VaultExportSelection(Scope scope, List<UUID> ids) {
  public enum Scope { ALL, SELECTED }
  public VaultExportSelection {
    if (scope == null || ids == null || ids.stream().anyMatch(java.util.Objects::isNull)) throw new VaultExportFailure(EXPORT_INVALID_REQUEST);
    if (ids.size() > PortableVaultFormat.MAX_ITEMS) throw new VaultExportFailure(EXPORT_LIMIT_EXCEEDED);
    if (new HashSet<>(ids).size() != ids.size() || scope == Scope.ALL && !ids.isEmpty()
      || scope == Scope.SELECTED && ids.isEmpty()) throw new VaultExportFailure(EXPORT_INVALID_REQUEST);
    ids = ids.stream().sorted().toList();
  }
  @Override public String toString() { return "VaultExportSelection[redacted]"; }
}
