# Kharcha — SMS-driven expense tracker (design)

Date: 2026-08-05
Status: approved for planning

## Problem

Siddhartha Bank (SBL) has no API. It does send an SMS alert for every account
movement and every card transaction, from the sender ID `SBL_Alert`. Those SMS
are the only machine-readable record available, so the app treats the SMS inbox
as its source of truth: read the messages, parse them into transactions,
categorize them, and visualize the result.

Android-only, single user, on-device only. No backend, no account, no network
dependency.

## Constraints and decisions

| Decision | Choice | Why |
|---|---|---|
| Platform | Native Android, Kotlin + Jetpack Compose | SMS receiver, background work and Room are first-class; no cross-platform need |
| Storage | On-device Room/SQLite | Bank data never leaves the phone; no server to run |
| Ingestion | Live `RECEIVE_SMS` receiver + one-time historical inbox backfill | Start with history rather than an empty app |
| Distribution | Sideload / personal build | `READ_SMS`/`RECEIVE_SMS` are restricted permissions; Play Store would need a policy exemption |
| Currency | Multi-currency from day one | The NPR account and the USD card coexist in the same inbox |
| UI style | Per the `impeccable` guidelines (github.com/pbakaus/impeccable) | Fetched and read before any UI work begins |

Out of scope for v1: eSewa/Khalti/other wallets, other banks, cloud sync,
web dashboard, multi-user. The parser is structured per-sender so adding a
second source later is additive, but no second source is built now.

## Message formats

All from sender ID `SBL_Alert`. Three families.

### Family A — NPR account movement

```
Dear SUVASH, AC 0###15164761, NPR 2,984.00 withdrawn on 17/07/2026 12:10:01 for QR Payment to JAWALAKHEL HANKOOK SARANG RESTAU
Dear SUVASH, AC 0###15164761, NPR 24,920.44 deposited on 17/07/2026 05:41:30 for Int.Pd:14-04-2026 to 16-07-2026
Dear SUVASH, AC 0###15164761, NPR 1,495.23 withdrawn on 17/07/2026 05:41:26 for WTax.Pd:14-04-2026to 16-07-2026
Dear SUVASH, AC 0###15164761, NPR 8.00 withdrawn on 03/08/2026 11:32:05 for cIPS Fund Trf Charge
Dear SUVASH, AC 0###15164761, NPR 1,015,625.00 withdrawn on 03/08/2026 11:32:05 for GLOBAL /Shambhu Nath/
```

Fields: masked account, currency `NPR`, amount (thousands separators),
direction (`withdrawn` = debit, `deposited` = credit), timestamp
`dd/MM/yyyy HH:mm:ss`, and a free-text remark after `for`.

The remark carries all the semantic signal and **is truncated by SMS length
limits** (`…RESTAU`, `MinisoJK - Sales;Sales`). Merchant matching must
therefore be prefix-tolerant and must never assume a complete merchant name.

### Family B — USD card transaction

```
SBL Card ***5367 used at SPACESHIP.COM* NRXD3L, US for USD 1.98 on 02.08.26 23:28 Authid 512208 Remaining Balance after txn USD 241.22. INFO 015970020
```

Fields: masked card, merchant descriptor, country, currency `USD`, amount,
timestamp `dd.MM.yy HH:mm` (note: different format from Family A), auth id,
and a post-transaction balance that Family A does not provide. Always a debit.

### Family C — ignore list

```
288388 is your OTP to get CVV for your Virtual eCom Card. Please do not share this OTP with others.
Your purchase code at 02/08/2026 23:27:46 of 1.98 USD is 338558
```

OTPs and purchase codes are matched explicitly and discarded. They must not
reach the unparsed inbox, or that inbox becomes noise and stops being useful.

Anything from `SBL_Alert` matching neither A, B, nor C is stored and surfaced
in an **Unparsed inbox** so a format change is visible rather than silent.

## Architecture

Three Gradle modules, each independently testable.

### `:parser` — pure Kotlin, zero Android dependencies

The core of the app and the part that must be correct. No Android types means
the whole thing is covered by fast JVM unit tests.

```
parse(sender: String, body: String, receivedAt: Instant): ParseResult
```

`ParseResult` is one of `Parsed(ParsedTransaction)`, `Ignored(reason)`, or
`Unrecognized`. A `SenderRuleset` holds the ordered patterns for one sender ID;
`SblAlertRuleset` implements families A, B and C. Adding a sender later means
adding a ruleset, not touching the engine.

`ParsedTransaction` carries: account/card identifier, currency, minor-unit
amount (`Long`, never `Double` — money is never floating point), direction,
occurred-at timestamp, raw remark, optional merchant, optional balance-after.

Every message line quoted in this document becomes a test case.

### `:data` — Room

| Table | Purpose |
|---|---|
| `raw_messages` | Every ingested SMS, verbatim, with a content hash. Never deleted. |
| `transactions` | One row per parsed transaction, FK to its raw message |
| `categories` | User-editable, seeded with a sensible default set |
| `rules` | Remark/merchant pattern → category, with priority |
| `budgets` | Per-category monthly limit and alert threshold |

Keeping raws forever is what makes re-parsing possible: when a rule improves,
history is regenerated rather than lost. Re-parse is idempotent — it rebuilds
`transactions` from `raw_messages` while preserving user overrides.

Dedup key is a hash of (sender, body, timestamp), so backfill and the live
receiver can overlap without creating duplicates.

### `:app` — Compose UI + ingestion

**Ingestion.** A `BroadcastReceiver` on `RECEIVE_SMS` does nothing but enqueue
a WorkManager job — parsing never runs on the receiver's main-thread budget.
A one-time backfill worker reads the SMS ContentProvider for all historical
`SBL_Alert` messages on first launch, guarded by the same dedup hash.

**Categorization.** Rules match against the remark tail with prefix-tolerant
comparison. Bank-generated entries are classified by built-in seed rules:
`WTax.Pd` and `*Charge` → Fees, `Int.Pd` → Income. Any transaction can be
re-categorized by hand; doing so offers to persist the choice as a rule.

**Exclude-from-spending.** A per-transaction toggle. Large transfers (the
`GLOBAL /Shambhu Nath/` case, over ten lakh) are legitimate account movements
but not spending, and would otherwise destroy the scale of every chart.
Excluded transactions stay visible in the list, marked, but leave all
aggregates.

**Screens.** Dashboard (month-to-date spend, category breakdown, trend, top
merchants), Transactions (list, filter, search, edit, manual entry),
Budgets (per-category limit with a local notification on threshold crossing),
Unparsed inbox.

## Data flow

```
SMS arrives ─▶ BroadcastReceiver ─▶ WorkManager job
                                         │
                          store raw_message (dedup by hash)
                                         │
                                   :parser.parse()
                          ┌──────────────┼──────────────┐
                      Parsed          Ignored       Unrecognized
                         │             (drop)            │
                  apply rules ─▶ transaction      unparsed inbox
                         │
                  budget check ─▶ notification
```

## Error handling

- A parse failure is a first-class outcome (`Unrecognized`), never an
  exception and never a silent drop.
- A malformed amount or date inside an otherwise-matching message downgrades
  the whole message to `Unrecognized` rather than storing a wrong number. A
  wrong transaction is worse than a missing one.
- Missing SMS permission degrades to manual entry with a clear in-app prompt;
  the app remains usable.
- Backfill is resumable and idempotent; interruption costs nothing.

## Testing

- `:parser` — JVM unit tests over every known message shape, including the
  truncation cases, both date formats, and the full ignore list. Table-driven
  so a new sample is one line.
- `:data` — Room instrumented tests for dedup, re-parse idempotency, and
  preservation of user overrides across a re-parse.
- `:app` — Compose UI tests for the transaction list and manual entry;
  the receiver→worker path tested with a synthetic SMS intent.

## Success criteria

1. Every message in the three screenshots parses to the correct amount,
   direction, timestamp and remark.
2. First launch backfills the full `SBL_Alert` history without duplicates.
3. A new SMS appears as a categorized transaction without opening the app.
4. Improving a rule re-categorizes history without losing manual overrides.
5. Bank fees and interest do not appear as discretionary spending.
