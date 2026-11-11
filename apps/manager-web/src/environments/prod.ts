declare const KEYNEST_API_BASE_URL: string;

export const environment = {
  production: true,
  // npm run build validates and substitutes the public deployment destination.
  apiBaseUrl: typeof KEYNEST_API_BASE_URL === 'undefined' ? '/api/v1' : KEYNEST_API_BASE_URL,
};
