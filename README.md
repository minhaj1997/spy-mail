# Spy Mail

A service that stores secret messages that destroy themselves once read.

## Requirements

- Docker with Docker Compose
- JDK 25 (only for running the tests; the Docker build brings its own)

## Start the system

```sh
docker compose up --build --wait
```

This starts the NATS server on port 4222, PostgreSQL and two instances of the crypto service.

## Configuration

The crypto service reads these environment variables, all optional:

- `MAX_TRIES` (default `3`): number of wrong keys before a message is deleted.
- `KEY_SIZE` (default `256`): AES key size in bits, one of `128`, `192` or `256`.
- `MAX_AGE` (default `2d`): how long an unread message is kept before it expires. Accepts a
  number with a unit suffix: `s`, `m`, `h` or `d`.

A message older than `MAX_AGE` is treated as gone as soon as anyone tries to read it, even if
the periodic cleanup job hasn't deleted its row yet.

## Run the tests

The client tests talk to the running system through NATS. Use the Maven wrapper, so you
don't need Maven installed:

```sh
./mvnw -pl client-tests test
```

`ExpiryTest` is skipped by default, since the default `MAX_AGE` of two days is too long to wait
out in a test. To actually exercise expiry, restart the stack with a short `MAX_AGE` and run the
tests with the same value:

```sh
MAX_AGE=5s docker compose up --build --wait
MAX_AGE=5s ./mvnw -pl client-tests test -Dtest=ExpiryTest
```

Stop the system with `docker compose down`.
