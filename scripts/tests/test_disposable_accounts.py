"""Prevent fixture setup from silently targeting ordinary deployments or arbitrary SQL."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('disposable_accounts', Path(__file__).resolve().parents[1] / 'disposable_accounts.py')
accounts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(accounts)


class DisposableAccountGuards(unittest.TestCase):
  @patch.object(accounts.subprocess, 'run')
  def test_refuses_normal_missing_and_ambiguous_project_names(self, run):
    for project in ('', 'keynest', 'production', 'keynest-ci-1', 'keynest-ci-1-1-extra'):
      with self.subTest(project=project), patch.dict(accounts.os.environ, {'COMPOSE_PROJECT_NAME': project}):
        with self.assertRaises(ValueError):
          accounts.seed('compose-' + 'a' * 24 + '@example.invalid')
    run.assert_not_called()

  @patch.object(accounts.subprocess, 'run')
  def test_refuses_real_addresses_sql_delimiters_and_unreviewed_compose_files(self, run):
    with patch.dict(accounts.os.environ, {'COMPOSE_PROJECT_NAME': 'keynest-ci-1-1'}):
      for email in ('owner@example.com', 'compose-' + 'a' * 24 + "'@example.invalid", 'csp-1@example.invalid'):
        with self.subTest(email=email), self.assertRaises(ValueError):
          accounts.seed(email)
      with self.assertRaises(ValueError):
        accounts.seed('compose-' + 'a' * 24 + '@example.invalid', '/tmp/arbitrary.yml')
    run.assert_not_called()

  @patch.object(accounts.subprocess, 'run')
  def test_seed_is_stdin_only_existing_owner_state_without_verification_or_password_output(self, run):
    with patch.dict(accounts.os.environ, {'COMPOSE_PROJECT_NAME': 'keynest-hosted-ci-12-2'}):
      accounts.seed('compose-' + 'a' * 24 + '@example.invalid', 'deploy/docker-compose.hosted.yml')
    args, options = run.call_args
    self.assertIn('deploy/docker-compose.hosted.yml', args[0])
    self.assertNotIn(accounts.PASSWORD, str(args))
    self.assertNotIn(accounts.PASSWORD.encode(), options['input'])
    self.assertIn(b'legacy_access_granted', options['input'])
    self.assertNotIn(b'email_verified_at', options['input'])
    self.assertIs(options['stdout'], accounts.subprocess.DEVNULL)
    self.assertIs(options['stderr'], accounts.subprocess.DEVNULL)


if __name__ == '__main__':
  unittest.main()
