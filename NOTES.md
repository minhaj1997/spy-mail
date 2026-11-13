# Notes

## Most important decisions, and why

- **Queue group `crypto-service` on both subjects.** Without it, both service instances would
  get every request, and a single wrong key would be counted twice. With it, NATS hands each
  request to exactly one instance, so the two instances only ever need to agree through the
  database, not through NATS.

- **A `SELECT ... FOR UPDATE` row lock drives the whole receive flow.** Locking, decrypting,
  and then deleting or raising the failed-attempt count all happen in one transaction on the
  locked row. If both instances get a request for the same message at the same time, the second
  one simply waits for the first one's transaction to commit, then sees the up-to-date row (or
  no row at all). This is what makes "two instances running" safe without any extra
  coordination mechanism between the instances themselves.

- **`ReceiveResult` is a sealed result type, not an exception.** A wrong key still has to commit
  its raised try counter. If a wrong key were reported by throwing out of the `@Transactional`
  method, Spring would roll the whole transaction back, including the counter increment, and an
  attacker could retry forever. Returning a value instead keeps the counter change even though
  the attempt is reported as a failure.

- **AES-GCM with the message ID as associated data.** GCM's authentication tag is what tells a
  wrong key apart from a right one: a wrong key fails the tag check instead of producing
  garbage plaintext. Passing the message ID as associated data means a ciphertext can't be
  copied to a different ID and decrypted there.

- **Receive accepts any valid AES key length (16, 24, or 32 bytes), not just the currently
  configured `KEY_SIZE`.** This means changing `KEY_SIZE` doesn't break messages that were saved
  under the old size. A key of some other length is simply treated as a wrong try.

- **Invalid Base64, a non-JSON body, or a blank/missing field all give `INVALID_REQUEST` and
  never count as a try.** Only an actual wrong key against a real message counts. This also
  means the three-tries limit can't be burned through by accident with a malformed request.

- **A non-UUID ID gives `NOT_FOUND`, not `INVALID_REQUEST`.** IDs are always UUIDs, so no
  message could ever exist under a different kind of ID. Treating it as not found rather than
  invalid avoids telling an attacker anything about what a valid ID looks like beyond what the
  interface already implies.

- **Expiry reuses `NOT_FOUND` rather than adding a new error code.** The interface defines four
  codes and no "expired" one, so an expired message is reported exactly like an unknown ID,
  which is also how a human would describe it ("there's nothing there"). The age check happens
  inside the same row-locked transaction as a normal receive, so it's consistent across both
  instances the same way the try counter is. An hourly scheduled job also deletes expired rows
  that nobody ever read, but that job is a storage backstop, not what makes expiry correct: the
  on-read check does that by itself even if the job never runs.

- **The reply is sent only after the transaction commits.** If the reply is lost after that
  point (a crash, a network drop), the message is already gone and cannot be recovered. This is
  a deliberate tradeoff: the alternative, replying before committing, risks telling a recipient
  "here is your message" and then rolling back, which is worse.

- **Request and reply payloads are never logged, and neither are Jackson parse errors.** Both
  can contain or quote the plaintext message or the key. The only thing logged on an unexpected
  error is the exception itself, through the normal `INTERNAL_ERROR` path.

## AI tools used

I used Claude Code (Anthropic's CLI, model Sonnet 5) throughout this task, for the following:

- **Build and compatibility verification.** My scaffold hadn't been compiled against a working
  JDK 25 or run through `docker compose` yet. Claude Code found a Homebrew-installed JDK 25,
  compiled the whole project with it, built and ran the full `docker compose up --build --wait`
  stack, and ran the client tests against the live two-instance system before reporting anything
  as verified. The Spring Boot 4 starter names and the Jackson 3 package names
  (`tools.jackson...`) were confirmed this way rather than assumed correct.

- **Writing the missing tests.** I asked for the tests that weren't written yet (happy path,
  read twice, wrong-then-right, three wrong keys, unknown and non-UUID IDs, invalid JSON,
  missing and blank fields, and keys never appearing in logs). Each test was run against the
  real running stack, not just compiled, with the actual pass counts reported back to me (for
  example, 28 passing after the first round). The log-safety test genuinely shells out to
  `docker compose logs` and checks for the key string; I had it confirm the test wasn't silently
  skipping before trusting the result.

- **The `MAX_AGE` expiry feature.** Config, the on-read check, and the scheduled cleanup job
  were implemented and then verified three ways: a full build, a full test run with the default
  `MAX_AGE` of two days (confirming the new expiry test correctly skips instead of hanging for
  two days), and a separate run with `MAX_AGE=5s` to confirm the expiry test actually exercises
  the behavior and passes, rather than trivially skipping every time.

- **Maven wrapper.** Added and verified by actually running `./mvnw` against the project, not
  just generating the files.

I reviewed every diff before it was committed and ran the test suite myself after each change
described above, rather than relying on a tool's own report that something worked.

## What I would do next with more time

- **NKeys and TLS** between the JUnit test client and NATS, and between NATS and the crypto
  service, with the connection setup described in the README. This is the largest remaining
  bonus item: it needs certificate generation, an NKey-based NATS authorization config, and
  matching TLS/NKey client setup in two separate Java codebases (the Spring service and the
  plain JUnit test client), none of which exists in the project yet.
- **Spotless with google-java-format**, so the Google Java Style Guide is enforced mechanically
  instead of by hand.
- **A fast-expiry test profile** (for example a `compose.override.yaml` with a short `MAX_AGE`)
  so the expiry test could run automatically in CI instead of needing a manual restart with
  `MAX_AGE=5s`.
- **A PostgreSQL `lock_timeout`**, so a stuck `FOR UPDATE` lock cannot hang a request
  indefinitely if something ever goes wrong with a long-running transaction.
