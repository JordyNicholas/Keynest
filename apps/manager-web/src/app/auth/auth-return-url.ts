import { PRIMARY_OUTLET, Router } from '@angular/router';

/** Only bounded local workspace or fixed AI-setup destinations can resume after sign-in. */
export function authReturnUrl(router: Router, value: string | null): string {
  if (!value || value.length > 2048 || !value.startsWith('/') || value.startsWith('//') || /[\\\r\n]/.test(value)) return '/overview';
  try {
    const tree = router.parseUrl(value);
    const primary = tree.root.children[PRIMARY_OUTLET];
    if (Object.keys(tree.root.children).length !== 1 || !primary || Object.keys(primary.children).length) return '/overview';
    const workspace = ['vault', 'overview'].includes(primary.segments[0]?.path);
    const aiSetup = primary.segments.length === 1 && primary.segments[0].path === 'account'
      && Object.keys(primary.segments[0].parameters).length === 0
      && Object.keys(tree.queryParams).length === 1 && tree.queryParams['ai'] === 'policy-explanation' && tree.fragment === 'ai-settings';
    if (!workspace && !aiSetup) return '/overview';
    return router.serializeUrl(tree);
  } catch {
    return '/overview';
  }
}
