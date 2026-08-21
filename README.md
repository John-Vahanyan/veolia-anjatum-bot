# Veolia Jur Water Outage Notifier Bot

A Telegram bot that watches the public **Veolia Jur** (Yerevan/Armenia water utility)
Telegram channel for water-outage announcements, parses each announcement into
structured data (district, date, time window, affected streets), and notifies
subscribers by direct message whenever a new announcement mentions a
street or district they've subscribed to.

## Why this exists

Veolia Jur posts outage notices to a public Telegram channel, but there's no
way to get pinged only when *your* street is affected — you either read every
post or miss the ones that matter. This bot lets you subscribe to a street or
district keyword once and get a direct notification only for announcements
that mention it.

## How it works

```
┌─────────────────────┐   scheduled poll    ┌──────────────────────┐
│ https://t.me/s/<ch>  │ ───────────────────▶│  ChannelHtmlFetcher   │
│ (public HTML preview)│    every ~90s        │  (Jsoup)              │
└─────────────────────┘                      └──────────┬───────────┘
                                                          │ new ChannelPosts
                                                          ▼
                                              ┌──────────────────────┐
                                              │ OutageAnnouncement    │
                                              │ Parser (regex)        │
                                              └──────────┬───────────┘
                                                          │ structured fields
                                                          ▼
                                              ┌──────────────────────┐
                                              │ KeywordMatcher        │
                                              │ vs. all subscriptions │
                                              └──────────┬───────────┘
                                                          │ matches
                                                          ▼
                                              ┌──────────────────────┐
                                              │ VeoliaNotifierBot      │
                                              │ (Telegram long-polling)│
                                              └──────────────────────┘
```

**No MTProto/userbot login is used.** Every public Telegram channel exposes a
lightweight, login-free HTML preview at `https://t.me/s/<channel_username>`.
The poller fetches that page on a schedule, extracts each post's stable
`data-post="<channel>/<id>"` attribute to detect duplicates, and only
processes posts it hasn't seen before (tracked in the `processed_posts`
table) — so restarts never reprocess old posts or silently drop ones that
arrived during downtime.

The bot side runs in **long-polling** mode (not webhooks), which means no
public HTTPS endpoint, reverse proxy, or open inbound port is needed —
it works from a plain Linux droplet out of the box.

### Parsing

A typical post looks like:

```
Վթարային ջրանջատում Երևանի Շենգավիթ վարչական շրջանում օգոստոսի 16-ին

«Վեոլիա Ջուր» ընկերությունը տեղեկացնում է իր հաճախորդներին և սպառողներին, որ վթարային
աշխատանքներով պայմանավորված ս.թ օգոստոսի 16-ին ժամը 14:00-16:00-ն կդադարեցվի
Մանթաշյան 6-12 զույգ համարի շենքերի ջրամատակարարումը:
```

The parser (`OutageAnnouncementParser`) extracts:
- **district** — from the title line, e.g. `Երևանի Շենգավիթ վարչական շրջանում`
- **date** — e.g. `օգոստոսի 16`
- **start/end time** — e.g. `14:00` / `16:00` (supports open-ended posts with
  only a start time, e.g. `ժամը 08:30-ից`)
- **streets** — one or more street/address fragments, split on commas and the
  Armenian conjunction "և"/"եւ"

Posts that don't match the expected structure (e.g. an unrelated channel
post) are logged as a warning and skipped, never crash the poller.

### Subscribing: region → district → street

`/subscribe` (and the **Subscribe** menu button) walks the user through a
guided, button-driven flow rather than asking for free text up front:

1. Pick one of Armenia's 11 regions (`Region`), Yerevan included. Every
   screen from here on has a **Go back** button that returns to this list.
2. If the region isn't Yerevan: choose **whole region** (notified for every
   outage anywhere in it) or **enter a street name** to narrow further.
   If it *is* Yerevan: pick one of its 12 districts (`District`) first, then
   the same **whole district** / **enter a street name** choice.
3. A street name typed at that point is *scoped* to the region/district just
   chosen, not just a bare keyword.

Picking a region/district the user is already **whole-area** subscribed to
skips straight to "you're already subscribed to all outages in X" (plus a
**Go back** button) instead of re-offering the choice — a street subscription
underneath an existing whole-area one would just double-notify for the same
announcement, since matching doesn't dedupe across a user's own subscriptions.

Region and district buttons are shown in the user's UI language, but only
the Armenian base form (`Region#armenian()` / `District#armenian()`) is ever
compared against post text — Veolia Jur posts exclusively in Armenian, and
every post's title names its area that way (`"<Region>ի մարզի ..."` /
`"Երևանի <District> վարչական շրջանում"`), so a plain substring check is
enough for the scope itself, no fuzzy tolerance needed.

The typed-command form still works too: `/subscribe <keyword>` subscribes
to an unscoped keyword directly, matched the old way (see below) — mainly
useful for power users who already know exactly what to type.

### Matching

An **unscoped** keyword (the legacy `/subscribe <keyword>` form, or a
"whole region"/"whole district" guided-flow choice) matches an announcement
if it appears (or *approximately* appears — see below) as a case-insensitive
substring of the district field or any parsed street fragment
(`KeywordMatcher#matches`). Whitespace and common punctuation differences
are normalized away before comparing.

A **scoped street** subscription (the guided flow's "enter a street name"
choice) requires *both* halves to match (`KeywordMatcher#matchesScoped`):
the announcement's district fragment must match the chosen region/district,
*and* one of its street fragments must match the typed street name. This is
what stops a street subscription from firing on a similarly-spelled street
in a completely different part of the country — the original failure mode
that motivated adding scoping, where a plain keyword with 2-edit fuzzy
tolerance could match all sorts of unrelated streets nationwide.

Veolia Jur only ever posts in Armenian, but the bot's audience isn't
Armenian-only, so a street name can be typed in Armenian, English, or
Russian letters. A Latin/Cyrillic keyword is automatically converted to
Armenian at subscribe time (`Transliterator`) using a best-effort phonetic
mapping — Armenian encodes phonetic distinctions (aspirated vs. plain
consonants, for instance) that Latin/Cyrillic spelling can't unambiguously
capture, so this is deliberately a heuristic, not an authoritative transform.

To absorb that slack, matching *can* tolerate up to 2 character edits
(insert/delete/substitute) once a keyword is 4+ characters long — but only
for a keyword that actually went through that conversion. Each subscription
stores whether it was transliterated (`Subscription#fuzzyMatch`), and that's
the only case fuzzy tolerance is used; a keyword the user typed directly in
Armenian must match exactly, since there's no transliteration slack to
excuse a near-miss there — tolerating one would just risk matching an
unrelated, similarly-spelled street (see `KeywordMatcher`'s
`allowFuzzy`/`allowFuzzyForStreet` parameters). The bot tells the user
exactly what Armenian spelling was stored and, when tolerance applies, says
so explicitly.

## Tech stack

- Java 21, Spring Boot (non-web — `@Scheduled` polling + a long-polling bot,
  no embedded servlet container)
- [`telegrambots`](https://github.com/rubenlagus/TelegramBots) for the Telegram
  Bot API (long polling)
- Jsoup for parsing the `t.me/s/...` HTML preview page
- SQLite (`org.xerial:sqlite-jdbc`) via plain Spring `JdbcTemplate` — no ORM,
  no separate DB server, deployment is a single JAR + a `.db` file
- Schema managed by a single idempotent `schema.sql` run on every startup
  (`spring.sql.init.mode=always`) — no migration framework needed for a
  schema this small. The one exception is `SubscriptionSchemaMigration`, a
  small hand-written `ApplicationRunner` that rebuilds the `subscriptions`
  table in place the first time it finds the pre-scoping shape (SQLite can't
  retrofit new columns/constraints onto an existing table via plain DDL).
- JUnit 5 + AssertJ for tests

## Project layout

```
am.veolia.bot
├── bot          Telegram command handling (VeoliaNotifierBot)
├── poller       scheduled channel fetching (ChannelHtmlFetcher, ChannelPollingScheduler)
├── parser       post → structured data (OutageAnnouncementParser, KeywordMatcher)
├── repository   SQLite persistence (UserRepository, SubscriptionRepository, ProcessedPostRepository)
├── model        domain records (OutageAnnouncement, ChannelPost, Subscription, Region, District, ...)
├── i18n         bot UI text in Armenian/English/Russian (Messages)
└── config       env-var-driven configuration, DataSource wiring, bot registration
```

## Bot commands & menu

Right after choosing a language, users get a persistent button menu (a
Telegram reply keyboard, always visible above the message box) mirroring
every action below — tapping **Subscribe** launches the guided
region → district → street flow described above; tapping **Unsubscribe**
shows an inline button per current subscription (labeled with its
region/district scope, if any) and removes whichever one is tapped. Typing
the commands directly still works the same way; the menu is just a second
way in.

| Command | Description |
|---|---|
| `/start` | Greets the user and prompts for a UI language (Armenian/English/Russian) |
| `/menu` | Re-show the button menu (e.g. if it was dismissed) |
| `/language` | Change the UI language at any time |
| `/subscribe <keyword>` | Subscribe to a street or district name |
| `/unsubscribe <keyword>` | Remove a subscription |
| `/list` | Show current subscriptions |
| `/help` | List available commands |

The bot's own messages (greetings, confirmations, help, menu labels) are
shown in your chosen language. The forwarded outage announcement text itself
is always the original Armenian source content — it's never translated.

## Configuration

All configuration is via environment variables (see `.env.example`); nothing
sensitive is hardcoded.

| Variable | Required | Default | Description |
|---|---|---|---|
| `BOT_TOKEN` | **yes** | — | Telegram bot token from [@BotFather](https://t.me/BotFather) |
| `BOT_USERNAME` | no | — | Your bot's `@username` (only used to strip `/cmd@YourBot` in group chats) |
| `ADMIN_CHAT_ID` | no | — | Numeric chat id to notify on every new subscription — **see "Admin subscribe notifications" below** |
| `CHANNEL_USERNAME` | no | `VeoliaJur` | Public channel to poll (no `@`, no `t.me/` prefix) |
| `POLL_INTERVAL_MS` | no | `90000` | How often to poll the channel |
| `POLL_INITIAL_DELAY_MS` | no | `5000` | Delay before the first poll after startup |
| `BOT_DB_PATH` | no | `./data/bot.db` | Path to the SQLite database file — **see "Data durability" below** |

### Admin subscribe notifications

Set `ADMIN_CHAT_ID` to get a message every time anyone subscribes:
`🔔 Jon Doe (@jondoe, id 123456789) subscribed to: Kentron — Abovyan`. Leave
it unset (the default) to disable this entirely.

The value is a numeric **chat id**, not a `@username` — Telegram bots can
only message a chat that has already interacted with them, so this only
works for a chat id that's pressed `/start` on this bot at least once
(typically your own). To find yours:

1. Open a chat with this bot (or any bot) and press `/start`.
2. Message [@userinfobot](https://t.me/userinfobot) on Telegram — it replies
   with your numeric id.
3. Set `ADMIN_CHAT_ID` to that number and restart the bot.

An invalid (non-numeric) value is logged as a warning at startup and treated
as disabled, rather than crashing the bot — unlike `BOT_TOKEN`, this setting
is optional.

## Running locally

Requires Java 21+ and Maven.

```bash
export BOT_TOKEN=your-token-from-botfather
export CHANNEL_USERNAME=VeoliaJur
mvn spring-boot:run
```

Run the tests:

```bash
mvn test
```

Build a runnable JAR:

```bash
mvn clean package
java -jar target/veolia-jur-bot.jar
```

## Deployment (plain Linux droplet, systemd, no Docker)

Because this is long-polling (no inbound HTTP), deployment needs no reverse
proxy, TLS cert, or open port — just a JVM and a systemd service.

### Data durability — important

The SQLite database holds every subscriber's language preference and street
subscriptions. **`BOT_DB_PATH` must point somewhere outside the build/output
directory and outside the git checkout**, e.g. `/opt/veolia-bot/data/bot.db` —
never `target/bot.db` or a path inside the repo clone. Deploys work by
rebuilding the JAR and restarting the service; if the DB lived inside the
build output or checkout, a redeploy would wipe every user's subscriptions.
The default (`./data/bot.db`, relative to wherever the JAR runs) is fine for
local development but should always be overridden in production.

### Steps

0. Install a Java 21 runtime on the droplet if it doesn't have one:

   ```bash
   sudo apt-get update
   sudo apt-get install -y openjdk-21-jre-headless
   ```

   If the droplet already runs other projects on an older Java version,
   installing 21 via `apt` may switch the system-wide `java` default to 21
   (check with `update-alternatives --list java` / `--config java`, and
   switch the default back if needed — `update-alternatives --config java`).
   It doesn't matter either way: [`deploy/veolia-bot.service`](deploy/veolia-bot.service)
   invokes the JDK 21 binary by its full path
   (`/usr/lib/jvm/java-21-openjdk-amd64/bin/java`), not the bare `java` command,
   so this bot is unaffected by — and never changes — whatever the system
   default `java` is. Adjust that path if your droplet's architecture isn't
   `amd64` (check with `update-alternatives --list java`).

1. Create the dedicated service + deploy accounts and directories by running
   [`deploy/provision-droplet.sh`](deploy/provision-droplet.sh) as root (see
   "Continuous deployment" below for what the `deploy` account is for):

   ```bash
   sudo bash deploy/provision-droplet.sh
   ```

2. Build the JAR locally or on the droplet and copy it into place:

   ```bash
   mvn clean package
   sudo cp target/veolia-jur-bot.jar /opt/veolia-bot/veolia-jur-bot.jar
   ```

3. Create `/opt/veolia-bot/veolia-bot.env` (mode `600`, owned by `veolia-bot`)
   with your real values, based on `.env.example`:

   ```
   BOT_TOKEN=...
   CHANNEL_USERNAME=VeoliaJur
   BOT_DB_PATH=/opt/veolia-bot/data/bot.db
   ```

4. Install the systemd unit from [`deploy/veolia-bot.service`](deploy/veolia-bot.service):

   ```bash
   sudo cp deploy/veolia-bot.service /etc/systemd/system/veolia-bot.service
   sudo systemctl daemon-reload
   sudo systemctl enable --now veolia-bot
   sudo systemctl status veolia-bot
   journalctl -u veolia-bot -f
   ```

5. **Redeploying** afterwards is just steps 2 + a restart — the DB at
   `/opt/veolia-bot/data/bot.db` is untouched:

   ```bash
   mvn clean package
   sudo cp target/veolia-jur-bot.jar /opt/veolia-bot/veolia-jur-bot.jar
   sudo systemctl restart veolia-bot
   ```

   Once continuous deployment (below) is set up, this whole step happens
   automatically on every push to `main` — this manual form is really only
   needed for the very first deploy, or if you ever want to push a build by hand.

### Continuous deployment (GitHub Actions)

[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) builds,
tests, and deploys on every push to `main`: `mvn test` gates the deploy (a
failing test never reaches the droplet), then `mvn package`, then the jar is
`scp`'d to the droplet and the service is restarted over SSH.

This intentionally reuses the plain systemd deployment above rather than
introducing Docker/a registry/a PaaS — the droplet still just runs one JAR
under systemd; GitHub Actions only automates the "copy jar, restart service"
steps from above.

**One-time setup:**

1. Run `deploy/provision-droplet.sh` (see step 1 above) — it creates a
   dedicated `deploy` system user, separate from the `veolia-bot` runtime
   account, that can only:
   - write the jar into `/opt/veolia-bot/` (not read `data/`, where the
     SQLite DB lives), and
   - run `systemctl restart veolia-bot` / `systemctl is-active veolia-bot`
     via a scoped, passwordless sudo rule — nothing else.

2. Generate a dedicated SSH keypair for CI (don't reuse your personal key):

   ```bash
   ssh-keygen -t ed25519 -f veolia_bot_deploy_key -N "" -C "veolia-bot-ci-deploy"
   ```

3. Append the **public** half to `/home/deploy/.ssh/authorized_keys` on the droplet.

4. In the GitHub repo → **Settings → Secrets and variables → Actions**, add:

   | Secret | Value |
   |---|---|
   | `DEPLOY_HOST` | the droplet's IP or hostname |
   | `DEPLOY_USER` | `deploy` |
   | `DEPLOY_SSH_KEY` | the **private** half of the keypair from step 2 |

   Then delete the local private key file — it only needs to exist in the
   GitHub secret and on the droplet's `authorized_keys` (public half).

From then on, every push to `main` deploys automatically; you can also
trigger a deploy manually from the Actions tab (`workflow_dispatch`).

## Out of scope for this version

- No web UI/dashboard — bot-only interaction.
- No source-language support beyond Armenian — the outage text itself is
  always forwarded as-is; only the bot's own UI (Armenian/English/Russian)
  and keyword transliteration are multilingual.
- No Docker requirement — a plain runnable JAR is sufficient for a droplet.
