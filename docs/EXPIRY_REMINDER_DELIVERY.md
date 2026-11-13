# Expiry reminder delivery

Status: proposed for [#37](https://github.com/JordyNicholas/Keynest/issues/37) and [#39](https://github.com/JordyNicholas/Keynest/issues/39). The scanner in #106 queues an item ID and effective expiry. Delivery is a separate opt-in setting.

## Recipient and message

Resolve the current item owner and recipient at dispatch time. Recompute effective expiry using the scanner's rules. Cancel the reminder if the item is gone, its effective expiry has changed, or it is outside the current notice window. Use generic subject and body text directing the user to sign in to Keynest. Exclude item IDs, labels, account URLs, usernames, passwords, and envelopes from messages. Resolve recipient eligibility before external delivery. Establish an explicit opt-in and an address-verification rule before enabling real delivery.

## Dispatch and retries

Claim a due row atomically with a bounded lease and a claim token. Commit the claim before any network call. Condition completion on the same token so an expired worker cannot overwrite a newer claim. Record sent, retryable failure, permanent failure, and stale cancellation using fixed statuses and error codes. Bound attempts and backoff, and configure transport timeouts shorter than the lease. A crash after SMTP acceptance but before recording success can produce a duplicate. Document this behavior for operators.

## Configuration and verification

Keep delivery disabled by default. Use an isolated local SMTP catcher with synthetic addresses for demos. For remote SMTP, require authenticated TLS and inject credentials through operator configuration. Keep recipient addresses, message bodies, credentials, and raw transport errors out of logs.

Verify concurrent claims, stale expiry cancellation, cross-owner recipient selection, lease expiry, retry exhaustion, and generic message content. Rehearse local delivery through the catcher before enabling any remote transport.
