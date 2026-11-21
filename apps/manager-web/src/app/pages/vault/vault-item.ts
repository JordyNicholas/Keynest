export type VaultItem = {
  id: string;
  label: string;
  url?: string;
  username?: string;
  policyId: string;
  expiresAt?: string;
  lastRotatedAt?: string;
  notifyBeforeDays?: number;
  createdAt?: string;
  updatedAt?: string;
};
