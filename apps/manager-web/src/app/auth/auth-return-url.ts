import { PRIMARY_OUTLET, Router } from '@angular/router';

/** Only local, primary-outlet account-workspace destinations can resume after sign-in. */
export function authReturnUrl(router: Router, value: string | null): string {
  if (!value || value.length > 2048 || !value.startsWith('/') || value.startsWith('//') || /[\\\r\n]/.test(value)) return '/vault';
  try {
    const tree = router.parseUrl(value);
    const primary = tree.root.children[PRIMARY_OUTLET];
    if (Object.keys(tree.root.children).length !== 1 || !primary || Object.keys(primary.children).length
      || !['vault', 'overview'].includes(primary.segments[0]?.path)) return '/vault';
    return router.serializeUrl(tree);
  } catch {
    return '/vault';
  }
}
