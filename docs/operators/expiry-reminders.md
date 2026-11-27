# Expiry reminders

The Manager API can scan for approaching expiries and dispatch reminders through an opt-in console simulator. The scanner queues item references and effective expiry times. The console adapter emits a fixed simulation message and marks the reminder sent. The default configuration leaves scan, dispatch, and simulation disabled.

## Local rehearsal

Before starting the API against a disposable database, enable the three settings:

```sh
export KEYNEST_NOTIFICATIONS_SCAN_ENABLED=true
export KEYNEST_NOTIFICATIONS_DISPATCH_ENABLED=true
export KEYNEST_NOTIFICATIONS_CONSOLE_DEMO_ENABLED=true
```

Create a synthetic account and an item whose expiry falls inside its notice window. With default intervals, the scanner runs every five minutes and the dispatcher every minute. Look for `Expiry reminder delivery simulated`. A later scan for the same expiry does not repeat the sent reminder. The console adapter makes no network calls and omits account identifiers.

## Recipient and message

The dispatcher reads the item’s current owner and recomputes expiry before dispatch. It skips missing items and cancels reminders whose expiry changed or falls outside the notice window. The console adapter simulates delivery. A real adapter must verify recipient eligibility. See the [threat model](../security/threat-model.md) for the outbox boundary.

## Dispatch and retries

The dispatcher claims a due row with a bounded lease and token. Completion requires the matching token. Attempts and retry delay are bounded, with fixed outcomes for success, retryable failure, permanent failure, and stale cancellation.

## Configuration and verification

External email delivery remains proposed. A future transport needs verified recipient eligibility, explicit consent, generic mail content, authenticated TLS, and tests for the failure and retry paths. Keep recipient addresses, message bodies, credentials, and raw transport errors out of logs. See the [project roadmap](../contributing/project/roadmap.md) for sequencing.
