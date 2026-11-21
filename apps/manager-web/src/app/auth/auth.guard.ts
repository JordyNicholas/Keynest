import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Require a tab session and preserve the requested local workspace destination. */
export const authGuard: CanActivateFn = (_, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.signedIn()) {
    return true;
  }
  return router.createUrlTree(['/account'], { queryParams: { returnTo: state.url } });
};
