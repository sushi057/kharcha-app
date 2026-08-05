# Task 3 report: NPR account message parsing (Family A)

## Regex used

Exactly as given in the brief, applied with `matchEntire` under `IGNORE_CASE` + `DOT_MATCHES_ALL`:

```
^Dear\s+\w+,\s*AC\s+(?<account>\S+),\s*NPR\s+(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<direction>withdrawn|deposited)\s+on\s+(?<date>\d{2}/\d{2}/\d{4})\s+(?<time>\d{2}:\d{2}:\d{2})\s+for\s+(?<remark>.+)$
```

## Failure paths

- No regex match -> `ParseResult.Unrecognized`.
- Amount fails `parseAmount(text, Currency.NPR)` (e.g. `???`) -> `Unrecognized`, no partial transaction constructed.
- Direction token not `withdrawn`/`deposited` -> `Unrecognized` (defensive; regex alternation already constrains this).
- Date/time: split on `/` and `:`, `toIntOrNull()` each component, then construct `kotlinx.datetime.LocalDateTime(year, month, day, hour, minute, second)` inside a `try`/catch on `IllegalArgumentException`. Invalid components (e.g. `45/45/2026`) throw inside the constructor and are caught, converting to `Unrecognized` — never propagated.
- `parse()` never throws: no unguarded `!!` outside of regex named-group access (which is safe because the groups are only accessed after `matchEntire` succeeds, and the regex guarantees each named group is present), no unguarded numeric parsing (`toIntOrNull`), and the date constructor is wrapped.

## Merchant / truncation logic

- Merchant = text after `"QR Payment to "` (case-insensitive prefix check) when the trimmed remark starts with it; `null` otherwise.
- `remarkTruncated = body.length >= 155 && !remark.endsWith(".")`, implemented verbatim per the brief.

## Commands run

```
export ANDROID_HOME=/home/sushi/Android/Sdk
./gradlew :parser:test --tests '*NprAccountParsingTest*'   # red: unresolved reference SblAlertRuleset
# implemented SenderRuleset.kt, SblAlertRuleset.kt
./gradlew :parser:test --tests '*NprAccountParsingTest*'   # BUILD SUCCESSFUL, 8/8 pass
./gradlew :parser:test                                      # BUILD SUCCESSFUL, full parser suite green
```

## Deviations

None. Test file copied verbatim from the brief. `senderId` was not specified in the brief itself but is pinned in `docs/superpowers/plans/2026-08-05-kharcha-v1.md` line 20 as `"SBL_Alert"` (case-insensitive match is the consuming code's job, per that doc) — used that value for `SblAlertRuleset.senderId`.

## Concerns for Task 4

- `SblAlertRuleset.parse` currently only tries the Family A regex and falls straight to `Unrecognized` otherwise. Task 4 should add Family B (USD card) and Family C (OTP/purchase-code ignore) as additional match attempts before the final `Unrecognized` fallback — the single `if match == null -> Unrecognized` exit point makes this a clean insertion point, no restructuring needed.
- `balanceAfter` is hardcoded `null` since Family A messages in the brief carry no balance figure. If a later message family exposes balance, `ParsedTransaction` already has the field ready.
- The `\S+` account-number group will happily match anything non-whitespace up to the comma; this hasn't been an issue for the `0###15164761`-style masked accounts in the brief's fixtures but is worth double-checking against real Family B/C account formats.
