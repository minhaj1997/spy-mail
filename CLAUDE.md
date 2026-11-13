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
  - `persistence/MessageRepository`: JdbcClient, `SELECT ... FOR UPDATE`
  - `crypto/MessageCipher`: AES-GCM
  - `config/SpyMailProperties`: `MAX_TRIES`, `KEY_SIZE`, `NATS_URL`, validated at startup
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
- The reply is sent only after the transaction commits. If the reply is lost, the message is
  gone. This is accepted and should be noted in NOTES.md.
- Never log request or reply payloads, and never log Jackson parse errors (they can quote the
  payload).

## Not verified yet

The scaffold was written without being able to build it. Check these first:

- Spring Boot 4 starter names: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`
- Jackson 3 packages (`tools.jackson...`) in `MessageSubscriber` and `SpyMailClient`
- That `docker compose up --build --wait` reaches healthy for all services
- That `mvn -pl client-tests test` passes against the running system

Only `MessageCipher` and `SpyMailProperties` were compiled and checked by hand.

## Still to do

- Tests: happy path, read twice, three wrong keys, wrong then right, unknown ID, invalid JSON,
  missing fields, and a test that keys never appear in logs
- Expiry: `MAX_AGE` config, a scheduled cleanup job, and a check on read
- Bonus: TLS and NKeys between test client, NATS and service, described in README
- Maven wrapper, Spotless with google-java-format
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
