"""Seed existing-owner fixtures only in explicitly disposable CI Compose databases.

Not an account API, deployment initializer, or email-verification result.
The known password/hash belongs only to synthetic example.invalid records.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import uuid

PASSWORD = 'Synthetic account password!42'
HASH = '$argon2id$v=19$m=65536,t=3,p=1$wAj9BMeT0cE8OgR15FSz8A$xrBsFUWabTkqGSeEkoP14j+Xrl6KKVxSv42zOtMlVyY'
COMPOSE_FILES = ('deploy/docker-compose.yml', 'deploy/docker-compose.hosted.yml')


def seed(email, compose_file='deploy/docker-compose.yml'):
  if not re.fullmatch(r'keynest-(?:hosted-)?ci-[0-9]+-[0-9]+', os.environ.get('COMPOSE_PROJECT_NAME', '')):
    raise ValueError('requires an explicitly disposable CI Compose project name')
  if compose_file not in COMPOSE_FILES:
    raise ValueError('unsupported disposable Compose file')
  if not re.fullmatch(r'(?:compose-[0-9a-f]{24}|csp-[0-9a-f-]{36})@example\.invalid', email):
    raise ValueError('requires a synthetic fixture address')
  # Values are generated here or strictly constrained above; stdin never enters a shell command.
  sql = ("INSERT INTO users (id,email,password_hash,created_at,legacy_access_granted) VALUES ('"
    + str(uuid.uuid4()) + "','" + email + "','" + HASH + "',CURRENT_TIMESTAMP,TRUE);\n")
  subprocess.run(['docker', 'compose', '-f', compose_file, 'exec', '-T', 'postgres', 'sh', '-c',
    'exec psql -q -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"'],
    input=sql.encode(), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=15, cwd=Path(__file__).resolve().parents[1])


if __name__ == '__main__':
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('--disposable', action='store_true', required=True)
  parser.add_argument('email')
  args = parser.parse_args()
  try:
    seed(args.email)
  except Exception:
    raise SystemExit('Disposable account setup failed (details suppressed)')
