# discord-bot

A standalone Discord bot on `sculk-discord`. **No Minecraft anywhere** — that is the point of the
example, and a build check enforces it rather than trusting the import list.

## Running it

1. Create an application at <https://discord.com/developers/applications>, add a bot, copy the token.
2. Under **Bot → Privileged Gateway Intents**, enable **Message Content**. The `!echo` relay needs it;
   without it Discord delivers messages with the text stripped, which reads as a bot that can see
   messages and cannot understand them.
3. Invite it with the `bot` and `applications.commands` scopes.

```bash
DISCORD_TOKEN=your-token DISCORD_GUILD=your-guild-id ./gradlew :examples:discord-bot:run
```

`DISCORD_GUILD` is optional and only changes how fast commands appear. Naming a guild registers
instantly; a global registration is cached by Discord for about an hour, which is a long time to wait
to find out a description had a typo.

## What it demonstrates

| | |
| --- | --- |
| `/ping` | The smallest command there is |
| `/kit list` | A themed container with buttons, handled by namespace |
| `/kit give` | A required user option, and autocomplete resolved per keystroke |
| `/confirm` | Post, then **wait fifteen seconds for a click** — the collector JDA has no equivalent for |
| `/punish` | A modal, and a Discord-side permission check |
| `!echo …` | The listening half of a chat bridge, with the echo loop and mention safety handled |

## The parts worth copying

**Nothing is thrown at you.** A missing token, an absent backend, a channel the bot cannot see: each
is a `SculkResult.Failure` carrying a sentence fit for a log.

**Shutdown is not optional.** `SculkHandle.all(handles).close()` then `gateway.close()`. Skip it and
JDA's threads keep the process alive after main returns.

**It is tested with no network.** `src/test` runs the real command registration and the real relay
against `FakeDiscordGateway` — including the assertion that an `@everyone` typed by a user pings
nobody. If that needed a live gateway, the framework's testability claim would be false.

```bash
./gradlew :examples:discord-bot:test
```
