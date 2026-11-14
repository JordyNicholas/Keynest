# Local expiry reminder rehearsal

Run Manager API against a disposable database with the scanner, dispatcher, and console adapter enabled.

```sh
export KEYNEST_NOTIFICATIONS_SCAN_ENABLED=true
export KEYNEST_NOTIFICATIONS_DISPATCH_ENABLED=true
export KEYNEST_NOTIFICATIONS_CONSOLE_DEMO_ENABLED=true
```

Create a synthetic account and a vault item whose expiry falls inside its notice window. With the default five-minute scan and one-minute dispatch intervals, look for the fixed message `Expiry reminder delivery simulated`. The adapter marks the row sent, so subsequent scans of the same expiry do not repeat it. Use a fresh database for future delivery rehearsals. The console adapter makes no network calls and does not log an account identifier.
