const prompts = {
  delete: 'Permanently delete this vault item?',
  'replace-password': 'Replace the saved password for this vault item? Confirm after changing it at the account website.',
} as const;

export function confirmVaultChange(action: keyof typeof prompts): boolean {
  return window.confirm(prompts[action]);
}
