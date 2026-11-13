# Spy Mail

A service that stores secret messages that destroy themselves once read.

## Requirements

- Docker with Docker Compose
- JDK 25 and Maven 3.9 (only for running the tests)

## Start the system

```sh
docker compose up --build --wait
```

This starts the NATS server on port 4222, PostgreSQL and two instances of the crypto service.

## Run the tests

The client tests talk to the running system through NATS:

```sh
mvn -pl client-tests test
```

Stop the system with `docker compose down`.
