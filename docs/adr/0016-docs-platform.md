# ADR-0016: Documentation platform

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

The rebuild directive named TanStack Start + React + MDX + Bun as the preferred candidate for a new
documentation portal, conditional on a short spike. The requirements that matter are a
manifest-driven navigation, version and platform selectors, compiled snippets, full-text search,
versioned builds, interactive playgrounds, a project generator and Cloudflare Workers deployment.

The spike (`docs/research/g-docs-spike.md`) scaffolded TanStack Start with MDX, built the manifest
sidebar, badges, synced tabs and source-imported snippets, prerendered and passed a Workers
dry-run deploy. It also measured the current Starlight site. Findings: TanStack Start is still a
release candidate; its three-page prototype shipped 344 KiB of client JavaScript against roughly
95 KiB for the 63-page Starlight site; every shell feature (table of contents, search UI,
highlighting, mobile navigation, i18n, accessibility, sitemap) had to be hand-built; and the
Sculk-specific requirements scored as custom code on both frameworks.

## Decision

Build Sculk Docs 6 on Astro Starlight with generated navigation. Keep the manifest, snippet,
versioning and selector pipelines framework-agnostic so the shell can be replaced. The fallback,
if Starlight blocks a requirement, is TanStack Start with Fumadocs UI, which the prototype showed
to be viable.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| TanStack Start + MDX, hand-built shell | Weeks of shell work and a 3.5× client JavaScript floor to reach parity with features Starlight already ships. |
| TanStack Start + Fumadocs UI | Closest contender; its default search is a server route, its static mode on Start is unverified, and the framework is still RC. Kept as fallback. |
| `starlight-versions` plugin | Early development, copies content into the repository. Multi-build per tag is more robust. |

## Consequences

The hand-maintained sidebar is replaced by a manifest-generated one and a Gradle task becomes part
of the docs build. Starlight must be upgraded to 0.42.x first. Playgrounds live in React islands
that cannot share context, so state goes through nanostores. Revisit when TanStack Start reaches
GA and Fumadocs offers a verified static search mode, or if Starlight's component overrides prove
insufficient for the selectors.
