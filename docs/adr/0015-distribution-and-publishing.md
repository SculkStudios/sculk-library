# ADR-0015: Distribution and publishing

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Sculk 5 published through JitPack with hand-rolled `maven-publish`, no signing, no SBOM and no
verification metadata. Sonatype OSSRH was retired on 2025-06-30; the Central Portal is the only
Maven Central path and has no official Gradle plugin and no OIDC trusted publishing. The Gradle
Plugin Portal requires a one-time manual approval for a non-prefixed group such as `studio.sculk`
(`docs/research/f-build-distribution.md` §1.4, §4).

## Decision

Publish every `studio.sculk:*` artifact, the BOM and the version catalog to Maven Central through
the Central Portal using the vanniktech maven-publish plugin, signed with a GPG key held as a
GitHub environment secret behind required reviewers. Publish the Gradle plugin to the Plugin
Portal and mirror it to Central. Ship a CycloneDX SBOM, GitHub build-provenance attestations on
every jar, and a second-runner rebuild that diffs checksums against the published artifacts.
Lock and verify dependencies with Gradle verification metadata (sha256 and PGP). Remove JitPack.
Snapshots go to the Central Portal snapshot repository.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| JitPack as primary | No signing, no reproducibility, builds on demand from source. |
| JReleaser | Capable, but a second configuration surface; kept as fallback if Sigstore bundles are wanted. |
| Hosted Renovate updating verification metadata | The hosted app cannot run the wrapper; a helper workflow refreshes the metadata on Renovate branches instead. |

## Consequences

Publishing needs the `studio.sculk` namespace verified by DNS and the Plugin Portal group approved;
a placeholder plugin release goes out early to absorb that delay. Release jobs run with caching and
incremental compilation off so rebuilds are byte-identical. The licence report and the
class-version gate run before publication.
