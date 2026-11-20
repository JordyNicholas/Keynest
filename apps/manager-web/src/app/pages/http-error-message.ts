/** Only local messages reach the UI. Never render an arbitrary server body. */
export function httpErrorMessage(status: number, action: string): string {
  if (status === 401) return 'Your session expired or is no longer valid. Sign in again.';
  if (status === 403) return 'This request was denied. Check the deployment permissions and API connection.';
  if (status === 404) return 'This item is no longer available. Refresh Vault and try again.';
  if (status === 409) return 'Your password policy changed. Reload this form to use the current rules.';
  if (status === 429) return 'Too many requests. Wait a minute and try again.';
  if (status === 0) return 'Could not reach the API. Check your connection or open API health in Account settings.';
  if (status === 503) return 'The service is temporarily busy or unavailable. Try again shortly.';
  return `Could not ${action}. Try again. If this continues, check API health in Account settings.`;
}
