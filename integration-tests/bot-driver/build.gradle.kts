// Starts the test client (ADR-0029) from inside a test plugin and reads its events. Shaded into the
// suites that need a player; never published.

plugins {
    id("sculk.jvm")
}
