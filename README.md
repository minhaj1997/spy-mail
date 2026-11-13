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

## Run the tests

The client tests talk to the running system through NATS. Use the Maven wrapper, so you
don't need Maven installed:

```sh
./mvnw -pl client-tests test
```

Stop the system with `docker compose down`.
