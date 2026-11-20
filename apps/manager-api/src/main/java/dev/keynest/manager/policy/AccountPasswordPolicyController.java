package dev.keynest.manager.policy;

import dev.keynest.manager.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account/password-policy")
public class AccountPasswordPolicyController {
  private final AuthService auth;
  private final AccountPasswordPolicyService policies;

  public AccountPasswordPolicyController(AuthService auth, AccountPasswordPolicyService policies) {
    this.auth = auth;
    this.policies = policies;
  }

  @GetMapping
  public ResponseEntity<AccountPasswordPolicyService.View> read(@RequestHeader(value = "Authorization", required = false) String bearer) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
      .body(policies.view(policies.effective(auth.requireUser(bearer))));
  }

  @PutMapping
  public ResponseEntity<AccountPasswordPolicyService.View> update(
    @RequestHeader(value = "Authorization", required = false) String bearer,
    @Valid @RequestBody AccountPasswordPolicyService.Settings settings
  ) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
      .body(policies.update(auth.requireUser(bearer), settings));
  }
}
