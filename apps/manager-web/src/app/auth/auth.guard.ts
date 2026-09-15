import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Stub for E4 — require a session token before Vault. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.signedIn()) {
    return true;
  }
  return router.createUrlTree(['/account']);
};
