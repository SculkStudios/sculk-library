# Security

## Reporting a vulnerability

Email security@sculk.studio or use GitHub's private vulnerability reporting on this repository.
Do not open a public issue. Expect an acknowledgement within 72 hours and a fix or mitigation
plan within 14 days for anything rated high or critical. Reporters are credited in the release
notes unless they ask otherwise.

Supported for security fixes: the latest 6.x minor and, until 6.0 ships, the latest 5.x release
on `main`.

## Threat model

Sculk is a library. Its attack surface is whatever a plugin author can be made to misuse, plus
the untrusted inputs the framework itself parses. Each threat below names the control the
framework enforces and the module that owns it.

| Threat | Control | Owner |
| --- | --- | --- |
| MiniMessage injection via player-controlled text | Templates are trusted, values are not. Values enter only through unparsed or component placeholders. No `replace()` on a template before parsing. Enforced by API shape and a test that renders `<red>` from a value as literal text. | `sculk-text` |
| PlaceholderAPI or MiniPlaceholders output containing markup | External placeholder results are treated as values unless the integration is explicitly configured to trust an expansion. | `sculk-placeholders` |
| Plugin-message spoofing from a client pretending to be the proxy | Frames carry a protocol magic, version and declared origin; the Paper side rejects frames whose channel is not registered or whose origin is a player connection when the transport expects a proxy. Optional HMAC for deployments that cannot trust the network. | `sculk-network` |
| Oversized or malformed network frames, replay | Hard maximum frame size, bounded serializer, correlation IDs with expiry, rejection without deserialising further. No arbitrary object deserialisation anywhere. | `sculk-network` |
| Packet trust | Packet listeners never block and never trust client-supplied coordinates or entity IDs beyond the packet's own schema. Development diagnostics flag blocking calls on packet threads. | `sculk-packets-*` |
| SQL injection | Query DSL and prepared statements only. Identifiers are quoted per dialect from a fixed allow-list of characters. Raw SQL requires an explicit, visibly named escape hatch. | `sculk-data` |
| Path traversal in config, resource packs, datapacks, file watching | Every path from configuration or network is resolved against its base directory and rejected if it escapes it. `sculk-config` reads only the file a plugin names and resolves no paths of its own. | `sculk-resourcepack`, `sculk-registry` |
| ZIP traversal and decompression bombs | Resource-pack and datapack archives are read entry by entry with per-entry and total size caps and path normalisation. | `sculk-resourcepack` |
| Arbitrary URL fetching and SSRF from configuration | Only `https` by default; private and link-local ranges blocked unless the operator opts in; redirects capped; response size capped. | `sculk-resourcepack`, `sculk-web` |
| Resource-pack hosting | The built-in host serves only the compiled pack from a fixed directory and includes the SHA-1 the client verifies. | `sculk-resourcepack` |
| Secrets in logs and error output | Values typed `Secret` print as `<redacted>` in every string conversion. A configuration error never echoes a `Secret` value, and a YAML syntax error reports the problem and line, never the line's text. The configuration file is the only place a secret's plain text is written; a generated file holds the type's default, which is a placeholder. Database URLs are logged without credentials. | `sculk-config`, `sculk-core` |
| Malicious or malformed configuration | Parsing is bounded by snakeyaml-engine's alias and input-size limits. Every load, reload and save runs the same checks, and one that fails changes neither the file nor the value in use. | `sculk-config` |
| Discord mention and markdown injection | Relayed text is escaped and allowed mentions default to none. | `sculk-discord` |
| Web authentication | No default-open endpoints. Token comparison is constant time. Health endpoints disclose nothing operator-sensitive. | `sculk-web` |
| Unbounded caches and queues | Every cache is bounded; every queue has a capacity and a documented overflow policy. | `sculk-cache`, `sculk-core` |
| Blocking IO on tick threads | Repositories, caches and HTTP are suspend-only; development diagnostics detect blocking on tick runners. | all |

Each row must have at least one test that demonstrates the attack failing. A new feature that
introduces a new trust boundary adds a row here in the same change.

## Dependencies

Dependency versions are locked and verified (`gradle/verification-metadata.xml`). Renovate opens
update pull requests; nothing is merged without CI. Every release ships a CycloneDX SBOM and
signed artifacts.
