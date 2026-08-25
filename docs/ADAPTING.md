# Adapting Kharcha to another bank

Kharcha reads one bank's SMS alerts. Nothing about the rest of the app — storage,
categorisation, budgets, the four screens, export — knows which bank that is. Porting it
means writing one class and changing two lines, plus whatever your own message formats
turn out to need.

This is a guide to doing that on your own fork. Note that the project carries no licence
(see the README), so it is not open source in the reuse-it-freely sense; ask first if you
intend to publish or distribute something built from it.

## What is bank-specific, and what is not

Everything that knows about Siddhartha Bank lives in three places:

| Where | What it knows |
|---|---|
| `parser/…/SblAlertRuleset.kt` | The sender ID `SBL_Alert`, three message families, and the ignore list |
| `parser/…/RemarkParser.kt` | Nepali payment rails — eSewa, Khalti, connectIPS, IBFT, FonePay, IME Pay — and the `Fund Trf to …` remark shape |
| `data/…/SeedData.kt` | Starting categories and the merchant rules that fill them |

Everything else — the Room schema, dedup, the categoriser, the re-parse machinery, every
screen — works on a `ParsedTransaction` and does not care where it came from.

## 1. Collect real messages first

Do not start from the format your bank documents, or from one message you remember. Export
a few hundred of your own SMS alerts and read them. What you are looking for is the
**families**: alerts that differ enough in shape that one regex cannot cover both. SBL has
three, and they disagree on date format (`17/07/2026 12:10:01` vs `02.08.26 23:28`),
currency, and whether a balance is included at all.

While you are there, note every message from the same sender that is *not* a transaction —
OTPs, password-change notices, promos, declines. Those matter as much as the transactions.

## 2. Write the ruleset

A ruleset is small:

```kotlin
interface SenderRuleset {
    val senderId: String
    fun parse(body: String): ParseResult
}
```

`ParseResult` is one of three things, and picking the right one is the whole job:

- **`Parsed`** — you understood the message and every field is real.
- **`Ignored(reason)`** — this is definitely not a transaction. It disappears into the
  Inbox's *Ignored* tab.
- **`Unrecognized`** — you are not sure. It goes to the Inbox's *Review* tab for the user
  to deal with by hand.

Copy `SblAlertRuleset` as the shape to follow. Three conventions in it are worth keeping:

**Ignore first.** `parse()` runs the ignore list before any transaction pattern. A purchase
code message contains a date, a currency and an amount; try to parse it as a transaction
first and you will succeed, and invent a transaction that never happened.

**Ignore conservatively.** Every ignore pattern is anchored to confident phrasing —
`"\\byour available balance is\\b"`, not a bare `balance` keyword. A wrong ignore silently
drops a real transaction and the user never finds out. A wrong `Unrecognized` costs them
one tap in the Inbox. The asymmetry is not close.

**Never fill a field in with a guess.** If the amount parses but the date does not, return
`Unrecognized` for the whole message rather than defaulting the date. A transaction with an
invented field corrupts every total on the dashboard, and nothing on screen shows that it
happened.

Two details that are easy to miss:

- **Truncation.** Bank alerts run up against the 160-character SMS limit, and the remark is
  usually what gets cut. `ParsedTransaction.remarkTruncated` marks those, so merchant
  matching stays prefix-tolerant instead of treating a cut name as a different shop. Set it
  the way the families in `SblAlertRuleset` do.
- **Amounts.** Use `parseAmount(text, currency)`. It works in integer minor units and
  rejects overflow; no money in this app ever touches a `Double`.

## 3. Test it against your real messages

`parser/src/test/…` has one test class per family — `NprAccountParsingTest`,
`UsdCardParsingTest`, `IgnoredMessageTest`, `DateOnlyTransferParsingTest`. The parser module
is pure Kotlin with no Android dependencies, so these run in milliseconds:

```
./gradlew :parser:test
```

Write these before the regexes, using real message bodies with your account numbers edited
out. It is much faster than reinstalling the app to find out a pattern was wrong, and the
messages you paste in become the regression suite for the next format change the bank
ships.

## 4. Wire it in

Two edits, and the second one is a hardcoded constant that ought to read from the ruleset:

```kotlin
// app/…/di/DataModule.kt
fun provideSenderRuleset(): SenderRuleset = MyBankRuleset
```

```kotlin
// app/…/ingest/BackfillWorker.kt
private const val TARGET_SENDER = "MyBank_Alert"
```

Both the live receiver and the historical backfill filter senders through
`SenderMatching`, which tolerates a substituted or dropped separator in the alias —
`SBL_Alert` arriving as `SBL-Alert` or `SBLAlert` still matches, because underscores do not
survive every hop between the SMSC and the handset intact, and a message dropped for that
reason is dropped silently. If your bank's sender ID has no separator, none of this
affects you.

## 5. If your bank is not in NPR

`Currency` is a two-value enum, `NPR` and `USD`. Add yours there first. Formatting is
already generic — `formatMoney` renders `"${currency.name} 1,234.56"` from minor units — so
display follows automatically.

What does not follow automatically is the dashboard. It deliberately shows **one currency
only**, because adding NPR and USD into a single total produces a number that means
nothing. `DashboardViewModel` and `BudgetsViewModel` therefore filter on `Currency.NPR` in
about a dozen places; grep for it and swap in your primary currency. Transactions and the
Inbox show everything and need no change.

## 6. Rails, remarks and categories

`RemarkParser` turns a remark tail into a display merchant, a channel and a coarse kind. Its
rail list is Nepal-specific, but the principle it is built on transfers anywhere: **the rail
and the counterparty are two independent axes**. `Fund Trf to NEA ELECTRICITY ESEW` is a
payment to NEA that travelled over eSewa. Fold those together and every wallet transfer
becomes one indistinguishable "eSewa" merchant, which quietly destroys top-merchant
ranking, recurring-charge detection and per-merchant categorisation — all of which key on
merchant.

Replace `specificRails` with your own wallets and networks, and adjust `fundTrfPattern` to
whatever phrasing your bank uses for a transfer. Then rewrite `SeedData.RULES`: the shipped
merchant rules are Kathmandu shops and will match nothing where you live. The categories
themselves are generic enough to keep.

## 7. Re-parsing history

Every raw message is stored verbatim forever, precisely so that improving the parser can
reach back over messages it previously got wrong. `ReparseService.reparseAll()` re-runs the
current ruleset over all of them and preserves hand-set categories.

There is no "re-parse now" button, though — today it fires when you save a category
correction as a rule, from the Transactions screen. Porting the parser is exactly the case
that wants one, so either add a Settings action calling `reparseAll()`, or during
development just clear app data and let the backfill re-import from the SMS inbox.

## Checklist

- [ ] Real messages collected, families identified, non-transactions listed
- [ ] Tests written from real bodies, one class per family
- [ ] `MyBankRuleset` passing them, ignore list first and conservative
- [ ] `DataModule.provideSenderRuleset` and `BackfillWorker.TARGET_SENDER` updated
- [ ] Currency added and the dashboard/budget filters switched, if not NPR
- [ ] `RemarkParser` rails and `SeedData.RULES` replaced for your market
- [ ] Installed on a real handset, SMS permission granted, backfill checked against the
      Inbox tab — anything sitting in *Review* is a family you have not covered yet
