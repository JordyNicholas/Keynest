// Copied only into the pinned upstream checkout for verification; never bundled with Keynest.
import { readFileSync } from 'node:fs';
import { BitwardenJsonImporter } from './bitwarden-json-importer';

class SyntheticImporter extends BitwardenJsonImporter { constructor() { super(); } }
type Login = { name: string; username: string | null; password: string; uris: string[] };
type Batch = { candidate: { encrypted: boolean; folders: unknown[]; items: Record<string, unknown>[] }; expected: Login[] };
const fixtures: { fixture: string; batches: Batch[] } = JSON.parse(readFileSync(process.env.KEYNEST_BITWARDEN_FIXTURE!, 'utf8'));
function requireMatch(condition: boolean): void { if (!condition) throw new Error('Synthetic importer contract mismatch (values suppressed)'); }
requireMatch(fixtures.fixture === 'keynest.synthetic.bitwarden.v1' && fixtures.batches.length === 3);

describe('Keynest synthetic Bitwarden interoperability', () => {
  for (const [batchIndex, batch] of fixtures.batches.entries()) {
    it(`preserves synthetic batch ${batchIndex}`, async () => {
      const result = await new SyntheticImporter().parse(JSON.stringify(batch.candidate));
      requireMatch(result.success && result.errors.length === 0 && result.ciphers.length === batch.expected.length);
      requireMatch(result.folders.length === 0 && result.collections.length === 0
        && result.folderRelationships.length === 0 && result.collectionRelationships.length === 0);
      for (const [index, expected] of batch.expected.entries()) {
        const cipher = result.ciphers[index];
        requireMatch(cipher.type === 1 && cipher.name === expected.name && cipher.login.username === expected.username && cipher.login.password === expected.password);
        requireMatch(JSON.stringify(cipher.login.uris?.map(uri => uri.uri) ?? []) === JSON.stringify(expected.uris));
        requireMatch(cipher.id === null && cipher.organizationId === null && cipher.collectionIds === null && cipher.key === null);
        requireMatch(!['id', 'policyId', 'expiresAt', 'lastRotatedAt', 'notifyBeforeDays', 'createdAt', 'updatedAt'].some(key => Object.prototype.hasOwnProperty.call(batch.candidate.items[index], key)));
      }
      requireMatch(!Object.prototype.hasOwnProperty.call(batch.candidate, 'accountPolicy'));
    });
  }
  it('refuses an encrypted flag instead of treating it as plaintext import data', async () => {
    let refused = false;
    try { await new SyntheticImporter().parse(JSON.stringify({ ...fixtures.batches[0].candidate, encrypted: true })); }
    catch { refused = true; }
    requireMatch(refused);
  });
});
