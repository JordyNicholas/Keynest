import { HttpClient } from '@angular/common/http';
import { catchError, map, Observable, of, timeout } from 'rxjs';
import { AuthService } from './auth.service';

/** Revoke the captured tab session; a late completion must not clear a newer session. */
export function logoutSession(http: HttpClient, api: string, auth: AuthService): Observable<void> {
  const token = auth.token();
  if (!token) return of(undefined);
  const headers = auth.authHeaders();
  return http.post(`${api}/auth/logout`, {}, { headers }).pipe(
    timeout(5000), catchError(() => of(undefined)),
    map(() => { if (auth.token() === token) auth.clearSession(); }),
  );
}
