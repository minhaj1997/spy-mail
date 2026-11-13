# Spy Mail: context for Claude Code

This is a take-home assessment. The full task is in `docs/assessment.pdf`. Read it first: it is
the source of truth. This file only summarizes it and records decisions made so far.

## The task in short

A crypto service stores secret messages that destroy themselves once read. Clients talk to it
over NATS request-reply:

- `save.msg`: request `{"message": "..."}`, reply `{"id": "...", "key": "<Base64 AES key>"}`
- `receive.msg`: request `{"id": "...", "key": "..."}`, reply `{"message": "..."}`
- Errors: `{"error": {"code": "...", "message": "..."}}` with codes `INVALID_REQUEST`,
  `NOT_FOUND`, `WRONG_KEY`, `INTERNAL_ERROR`

Rules: the service generates a random AES key, never stores or logs it, deletes a message after
it is read or after the third wrong key, and all rules must hold with two instances running.
`docker compose up --build --wait` starts NATS (host port 4222), two service instances and the
database. Clients are JUnit tests that connect to NATS. Follow the Google Java Style Guide.

## Layout

- `crypto-service/`: Spring Boot 4 app, Java 25, Maven
  - `nats/MessageSubscriber`: subscribes to both subjects, parses JSON, maps results to replies
  - `service/MessageService`: save, and the transactional receive
  - `service/MessageExpiryJob`: scheduled job that deletes messages older than `MAX_AGE`
  - `persistence/MessageRepository`: JdbcClient, `SELECT ... FOR UPDATE`
  - `crypto/MessageCipher`: AES-GCM
  - `config/SpyMailProperties`: `MAX_TRIES`, `KEY_SIZE`, `MAX_AGE`, `NATS_URL`, validated at
    startup
- `client-tests/`: plain JUnit 5 clients over NATS, no Spring
- `compose.yaml`: nats, postgres, crypto-1, crypto-2, all with healthchecks

## Key decisions (keep these unless there is a good reason)

- **Queue group** `crypto-service` on both subjects, so each request reaches one instance only.
- **PostgreSQL row lock**: receive locks the row with `FOR UPDATE`, decrypts, then deletes or
  raises `failed_attempts`, all in one `@Transactional` method.
- **`ReceiveResult` is a sealed result type, not an exception**, because an exception would roll
  back the raised try counter.
- **AES-GCM** with a random 12-byte IV per message. A wrong key fails the tag check, which is how
  `WRONG_KEY` is detected. The message ID is passed as associated data.
- Receive accepts any valid AES key length (16, 24, 32 bytes), so changing `KEY_SIZE` does not
  break old messages. A key of another length counts as a wrong try.
- Invalid Base64 or blank fields give `INVALID_REQUEST` and do not count as a try.
- An ID that is not a UUID gives `NOT_FOUND`.
- A message older than `MAX_AGE` (default `2d`) gives `NOT_FOUND` on read, the same code as an
  unknown ID, since the interface has no separate "expired" error. The check runs inside the same
  row-locked transaction as the normal receive, so it's consistent across both instances. A
  scheduled job (`MessageExpiryJob`, hourly) also deletes expired rows nobody ever read, so they
  don't sit in the database forever; it's a backstop, not the thing that makes expiry correct.
- The reply is sent only after the transaction commits. If the reply is lost, the message is
  gone. This is accepted and should be noted in NOTES.md.
- Never log request or reply payloads, and never log Jackson parse errors (they can quote the
  payload).

## Verified

Build, `docker compose up --build --wait`, and the full test suite (`./mvnw -pl client-tests
test`) have all been run against the real stack, including the Spring Boot 4 starter names and
the Jackson 3 (`tools.jackson...`) packages. A Maven wrapper (`./mvnw`) is checked in, pinned to
3.9.12, so a local Maven install isn't required.

## Still to do

- Bonus: TLS and NKeys between test client, NATS and service, described in README
- Spotless with google-java-format
- NOTES.md (the developer writes this himself)
- Optional hardening: PostgreSQL `lock_timeout`

## How to work in this repo

- Plain, simple English in docs and comments. No em-dashes in any document.
- Small, focused commits with imperative messages, for example
  `Return NOT_FOUND for unknown IDs`. The commit history is reviewed.
- Explain changes so they can be defended in a live code review. Say clearly when something is
  unverified instead of claiming it works.
- Run the client tests after any change to receive logic. The concurrency tests in
  `ConcurrentReceiveTest` must keep passing.
