# Contributing

Thanks for helping. Sculk 6 is being rebuilt milestone by milestone (`REBUILD.md`), so the most
useful contributions right now are bug reports against the architecture documents, research
corrections, and work on the current milestone.

## Before you start

- Read `ARCHITECTURE.md`, `MODULES.md` and `THREADING.md`.
- Open an issue before a large change. A change that crosses module boundaries or adds public API
  needs agreement first; a new architectural decision needs an ADR in `docs/adr/`.

## Making a change

1. Branch from `dev`.
2. For a bug, add a failing test first.
3. Keep the change to one concern.
4. `./gradlew build` must pass. It runs ktlint, the tests on Java 21 and 25, the ABI check, the
   architecture rules and the bytecode gate.
5. If the public surface changed, run `./gradlew updateKotlinAbi` and commit the dump.
6. If the change touches a server, run the matching suite (`verifyBoot`, `verifyThreading`,
   `verifyCommands`, `verifyVelocity` under `integration-tests`) and, where it applies, add a
   scenario.
7. If a dependency changed, every artifact it brings needs a recorded checksum, or the build fails
   with "Dependency verification failed". Refresh the records, then read the diff before committing
   it: a checksum you did not expect to change is the thing verification exists to catch.

   ```
   ./gradlew --write-verification-metadata pgp,sha256 --export-keys build publishToMavenLocal :benchmarks:mainBenchmarkJar :tooling:compatibility:scanPaperApi
   ```

   A new runtime dependency of a published module must also carry a licence on
   `gradle/allowed-licenses.json`; `check` enforces it.

## A public feature is done when it has

Implementation, tests, integration tests where platform behaviour is involved, KDoc stating the
contract, a documentation page, a compiled example, its threading and compatibility contracts,
defined error behaviour, resource cleanup, a stability marker, a performance sanity check and an
ABI dump.

## Style

The rules are in `CLAUDE.md` under "Rules review enforces". In short: Kotlin first, immutable
public data, no `!!`, comments only where they explain a constraint, test names that read as
sentences.

## Commits

Conventional commits (`feat(core): …`, `fix(commands): …`, `build: …`, `docs: …`), written in plain
English, one logical change each.

## Security

Report vulnerabilities privately as described in `SECURITY.md`, never in a public issue.
