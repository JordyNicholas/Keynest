import { accessSync, constants } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** Canonical default policy packaged with keynest-policy; imported by public tools. */
const policyPath = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../libs/keynest-policy/src/main/resources/policies/keynest.default.v1.json',
);

try {
  accessSync(policyPath, constants.R_OK);
} catch {
  console.error(
    'Missing default policy JSON required by Manager Web public tools:\n'
      + `  ${policyPath}\n`
      + 'Ensure the monorepo checkout includes libs/keynest-policy and that '
      + '.vercelignore does not exclude that file.',
  );
  process.exit(1);
}
