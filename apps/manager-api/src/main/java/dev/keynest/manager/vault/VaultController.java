package dev.keynest.manager.vault;

import dev.keynest.manager.auth.AuthService;
import dev.keynest.manager.auth.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/vault/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class VaultController {

    private final AuthService authService;
    private final VaultService vaultService;

    public VaultController(AuthService authService, VaultService vaultService) {
        this.authService = authService;
        this.vaultService = vaultService;
    }

    @GetMapping
    public List<VaultService.VaultItemView> list(@RequestHeader("Authorization") String authorization) {
        UserAccount user = authService.requireUser(authorization);
        return vaultService.list(user);
    }

    @GetMapping("/{id}")
    public VaultService.VaultItemSecretView get(
            @RequestHeader("Authorization") String authorization,
            @PathVariable UUID id,
            @RequestParam(name = "reveal", defaultValue = "false") boolean reveal
    ) {
        UserAccount user = authService.requireUser(authorization);
        return vaultService.get(user, id, reveal);
    }

    @PostMapping
    public VaultService.VaultItemView create(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateBody body
    ) {
        UserAccount user = authService.requireUser(authorization);
        return vaultService.create(user, new VaultService.CreateVaultItemRequest(
                body.label(),
                body.url(),
                body.username(),
                body.password(),
                body.generate(),
                body.policyId(),
                body.expiresInDays(),
                body.notifyBeforeDays()
        ));
    }

    @PutMapping("/{id}")
    public VaultService.VaultItemView update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable UUID id,
            @RequestBody UpdateBody body
    ) {
        UserAccount user = authService.requireUser(authorization);
        return vaultService.update(user, id, new VaultService.UpdateVaultItemRequest(
                body.label(),
                body.url(),
                body.username(),
                body.password(),
                body.policyId(),
                body.expiresInDays(),
                body.notifyBeforeDays()
        ));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(
            @RequestHeader("Authorization") String authorization,
            @PathVariable UUID id
    ) {
        UserAccount user = authService.requireUser(authorization);
        vaultService.delete(user, id);
        return Map.of("status", "deleted");
    }

    public record CreateBody(
            @NotBlank String label,
            String url,
            String username,
            String password,
            Boolean generate,
            String policyId,
            Integer expiresInDays,
            Integer notifyBeforeDays
    ) {
    }

    public record UpdateBody(
            String label,
            String url,
            String username,
            String password,
            String policyId,
            Integer expiresInDays,
            Integer notifyBeforeDays
    ) {
    }
}
