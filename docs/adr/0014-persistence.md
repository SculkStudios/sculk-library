# ADR-0014: Persistence architecture

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Plugin persistence is small point queries at low concurrency, with SQLite as the default store and
MySQL, MariaDB and PostgreSQL for networks. Sculk 5's descriptor-driven repositories, real upserts,
additive schema migration and real-engine CI held up; H2 in MySQL mode once passed a statement that
MySQL rejects. Research (`docs/research/e2-presentation-gui-config-data-packets.md` §D) found that
R2DBC has no SQLite driver, a specification frozen since 2022, and drivers that bring Netty 4.1 into
servers running Netty 4.2; Exposed requires `kotlin-reflect`; Liquibase is not OSI-licensed;
mysql-connector-j is GPL-2.0 with a FOSS exception and MariaDB Connector/J is LGPL-2.1.

## Decision

JDBC with HikariCP behind suspend repositories. All database work runs on
`Dispatchers.IO.limitedParallelism(maximumPoolSize)` so no coroutine blocks inside
`getConnection()` and no tick thread ever touches JDBC. Repositories are generated from
kotlinx.serialization descriptors, not reflection. Schema changes use two mechanisms: additive
entity-table migration (Sculk 5 behaviour, renames warned) and a versioned script runner with a
Flyway-shaped history table and checksum validation; nothing is ever dropped automatically. Drivers
are separate modules (`sculk-data-sqlite`, `-mysql`, `-mariadb`, `-postgres`) that rely on
Paper-provided SQLite and MySQL drivers or the `paper-plugin.yml` library loader and are never
shaded. SQLite runs with pool size 1, WAL and a busy timeout. CI runs the dialect suite against
real MySQL, MariaDB, PostgreSQL and SQLite and fails if any engine was skipped. Caching is a
separate module family (`sculk-cache`, `-caffeine`, `-valkey`); `sculk-data` never depends on it.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| R2DBC | No SQLite driver; Netty 4.1; frozen SPI; few users. The suspend repository contract leaves it addable later as `sculk-data-r2dbc`. |
| Exposed as the data layer | `kotlin-reflect`, DAO cache staleness, a full ORM where plugins need repositories. An optional adapter remains a 6.x candidate. |
| Flyway or Liquibase as a dependency | Weight and licence for a feature that is a table and a checksum. Their shapes are borrowed. |

## Consequences

The dialect matrix and the migration runner are Sculk's to maintain and are covered by real-engine
tests. The repository API stays `suspend` and `SculkResult`-returning so a reactive or Exposed
transport is additive. Redis and Valkey stay out of `sculk-data`.
