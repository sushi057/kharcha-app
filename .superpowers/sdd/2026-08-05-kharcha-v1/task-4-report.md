# Task 4 report: USD card message parsing (Family B) and the ignore list (Family C)

## Summary

Extended `SblAlertRuleset.parse` in `parser/src/main/kotlin/com/kharcha/parser/SblAlertRuleset.kt`
to recognize USD card purchase messages (Family B) and to explicitly ignore
OTP / purchase-code messages (Family C), without modifying the already-passing
Family A (NPR account) behavior.

## Dispatch order implemented

`parse(body)`:
1. `ignoreReasonFor(body)` — checked first. Returns `ParseResult.Ignored("otp")`
   or `ParseResult.Ignored("purchase code")` if matched.
2. `parseFamilyA(body)` — NPR account debit/credit alerts (unchanged logic,
   refactored into a private function returning `ParseResult?`, `null` meaning
   "did not match, try the next family").
3. `parseFamilyB(body)` — USD card purchase alerts.
4. `ParseResult.Unrecognized` — fallback.

This matches the brief's required order: ignore list -> Family A -> Family B -> Unrecognized.
The ignore check runs before any transaction-shaped regex so the real purchase-code
message (`"Your purchase code at 02/08/2026 23:27:46 of 1.98 USD is 338558"`), which
contains a date, currency and amount, cannot be misread as a transaction.

## Regexes used

Ignore patterns:
- OTP: `Regex("is your OTP", RegexOption.IGNORE_CASE)` matched with `containsMatchIn`
  (not anchored — OTP messages can have arbitrary prefix like `"288388 is your OTP..."`).
- Purchase code: `Regex("^Your purchase code at .* is \\d+$")` matched with
  `containsMatchIn` (effectively full-string match since it's `^...$`).

Family B (card), copied verbatim from the brief, matched with `find` (not
`matchEntire`, since the real sample message has trailing text after the
regex's matched portion — `" INFO 015970020"` — which the regex does not
capture and is intentionally ignored):

```
^SBL\s+Card\s+(?<card>\S+)\s+used\s+at\s+(?<merchant>.+?)\s+for\s+USD\s+(?<amount>[\d,]+\.\d{2})\s+on\s+(?<date>\d{2}\.\d{2}\.\d{2})\s+(?<time>\d{2}:\d{2})\s+Authid\s+(?<authid>\w+)(?:\s+Remaining\s+Balance\s+after\s+txn\s+USD\s+(?<balance>[\d,]+\.\d{2}))?
```

Compiled with `IGNORE_CASE` and `DOT_MATCHES_ALL` (matching the style already
used for Family A).

## Optional balance group

`match.groups["balance"]?.value` is nullable by construction (the group is
inside a non-capturing `(?:...)?`). If present, it's run through the existing
`parseAmount(text, Currency.USD)`; if that returns `null` (malformed amount),
the whole message is `Unrecognized` rather than silently dropping the balance.
If the group is absent, `balanceAfter = null` directly — not treated as a
failure, per the brief.

## Two date formats

- Family A: `dd/MM/yyyy HH:mm:ss` — existing `parseFamilyADate` (renamed from
  `parseOccurredAt`), unchanged logic.
- Family B: `dd.MM.yy HH:mm` — new `parseFamilyBDate`. Splits on `.` for the
  date and `:` for the time (2 components, no seconds). Two-digit year `yy`
  maps to `2000 + yy`. Both functions wrap `LocalDateTime(...)` construction
  in try/catch over `IllegalArgumentException` and return `null` on invalid
  dates (e.g. day 32, month 13), which the callers turn into
  `ParseResult.Unrecognized`. No kotlinx-datetime exception escapes `parse`.

## `remark` / `merchant` / `sourceAccount` for card messages

- `sourceAccount` = the masked card token, e.g. `***5367`.
- `remark` = the merchant descriptor (kept for exact match with `parseFamilyA`'s
  existing style where `remark` carries the free-text description).
- `merchant` = same merchant descriptor string (not `null`), since for card
  messages the merchant is always known.
- `remarkTruncated` = `false` always for Family B — there's no evidence in the
  brief of a truncation concern for card messages (unlike Family A's 160-char
  SMS concatenation heuristic), so it's hardcoded false rather than guessed.

## Commands run (real output)

```
$ export ANDROID_HOME=/home/sushi/Android/Sdk
$ ./gradlew :parser:test --tests '*UsdCardParsingTest*' --tests '*IgnoredMessageTest*'
...
IgnoredMessageTest > ignores purchase code messages() FAILED
IgnoredMessageTest > ignores OTP messages() FAILED
UsdCardParsingTest > card transactions are always debits() FAILED
UsdCardParsingTest > parses a USD card purchase() FAILED
5 tests completed, 4 failed
BUILD FAILED
```

(Confirmed red before implementation — the 5th test,
`an unknown message is unrecognized, not ignored`, already passed since
`Unrecognized` was already the fallback.)

After implementation:

```
$ ./gradlew :parser:test
BUILD SUCCESSFUL in 3s
4 actionable tasks: 4 executed
```

Per-suite counts from `parser/build/test-results/test/*.xml`:
- `UsdCardParsingTest`: 2 tests, 0 failures
- `NprAccountParsingTest`: 8 tests, 0 failures (Task 3, unchanged)
- `SmokeTest`: 1 test, 0 failures
- `IgnoredMessageTest`: 3 tests, 0 failures
- `MoneyTest`: 4 tests, 0 failures

Total: 18 tests, 0 failures, 0 errors.

## Deviations from the brief

None. Both test files were copied verbatim. The card regex was copied
verbatim from the brief. Matching was done with `find` instead of
`matchEntire` because the brief's real sample message
(`"... Authid 512208 Remaining Balance after txn USD 241.22. INFO 015970020"`)
has trailing text (`". INFO 015970020"`) after what the given regex captures,
and the regex itself has no `$` anchor — using `matchEntire` would have caused
both `UsdCardParsingTest` cases to fail as `Unrecognized`. `find` matches the
brief's regex as literally given while tolerating the unmodeled suffix, which
is consistent with the brief not asking for an end anchor on Family B (Family
A's regex, in contrast, does end with `$`).

## Concerns for Task 6 (parser consumer)

- Family B's `find`-based matching means it does not require anchoring at the
  end of the message; a message with `SBL Card ... Authid ...` as a prefix of
  a larger, otherwise-nonsensical string would still be parsed as a
  transaction. This mirrors the brief's regex exactly, so it's a deliberate
  scope decision, but worth flagging since Family A is stricter (`matchEntire`,
  full anchoring).
- The ignore-list `otpPattern` is unanchored substring matching
  (`containsMatchIn` on `"is your OTP"`), so any message containing that phrase
  anywhere is ignored, even if it also happens to look like a transaction.
  This is intentional per the ordering requirement but means a hypothetical
  future SBL message that mentions "OTP" in passing while also being a real
  transaction would be silently ignored, not parsed. Flagging in case Task 6
  or later tasks want visibility into what's being ignored and why (the
  `Ignored(reason)` string is already there for that: `"otp"` /
  `"purchase code"`).
- `remarkTruncated` is hardcoded `false` for card messages since the brief
  didn't specify truncation behavior for Family B. If real-world card SMS can
  be concatenated/truncated by the carrier the way Family A's can, this may
  need revisiting.
