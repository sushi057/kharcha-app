# Kharcha v2 — working state

Branch: `feat/v2-redesign` (off `master` — note this repo uses `master`, not `main`). Last updated during the v2 redesign session.

**If you are picking this up cold: read this file, then `git log --oneline master..HEAD`.
The tree is clean and the branch is finished; what remains is listed under "Open question
for the user" below.**

---

## Why this work exists

The v1 app shipped and the user reviewed it harshly and specifically:

1. UI "hideous… not even the padding is matching. The buttons and the components are all terrible."
2. Chart y-axis increments "field random".
3. USD Trends / USD by Category / USD Top Merchants — scrap entirely.
4. Transactions "horrid": raw SMS text shown as the merchant ("Fund Transfer to…"), and day
   headers indistinguishable from transaction rows.
5. Unparsed (808 messages) must account for "debited by"/"credited by" and split into two
   sections: genuinely-ambiguous vs. clearly-not-transactions (OTP, password changed).
6. A manual re-sync / sync button.
7. Budgets: more visualisations, inline add/remove categories, move to the second tab.
8. An export button.

Plus: research the top expense trackers for features, and use
`github.com/nextlevelbuilder/ui-ux-pro-max-skill` for the UI.

## Approved design

Phase 0 mockup (approved by the user, verbatim: "the design looks amazing"):
<https://claude.ai/code/artifact/e0c7b9d7-7e23-493f-becd-72e6bcf5e3d8>

Source of that mockup, if it needs regenerating, is in the session scratchpad — it is a
standalone HTML file with fonts inlined as data URIs. It is a *reference*, not a build
artifact; the Compose implementation is the deliverable.

### Decisions the user made explicitly

| Question | Decision |
|---|---|
| Theme | Dark-first, with a genuine light mode (not an inversion) |
| Currency on Dashboard | **NPR only.** USD stays visible in Transactions and Budgets |
| The 808 backlog | Auto-import everything newly parseable; review after |
| Scope | One branch, phased commits |
| Subagents | Use Haiku where possible — the user's usage limit is low |

### Decisions made on our side, with reasoning

- **Credit moves from amber `#E0A94A` to sage `#8FAE4F`.** The old amber was nearly identical
  to the new gold accent `#D4A03C`, so "money arrived" and "this is a button" rendered as the
  same colour.
- **Fraunces + IBM Plex Sans are kept** rather than the mockup's Calistoga + Inter +
  JetBrains Mono. Both are already bundled offline and licensed, and Plex's `tnum` gives true
  tabular figures — which is the entire reason the mockup used a mono face. *The user was told
  this and has not objected; if they want Calistoga specifically, it must be bundled with its
  OFL licence file.*
- **Rail vs. counterparty are separate axes** in `RemarkParser`. The rail (eSewa, connectIPS,
  IBFT) is *how* money moved and becomes `channel`; the counterparty is *who* received it and
  becomes `merchant`. Collapsing them would key top-merchants, recurring detection and
  per-merchant rules on a single indistinguishable value.
- **8 category hues cannot be mutually distinguishable under dichromacy.** This is a property
  of the colour space, not a palette failure. Resolution: separate hues in L\* as well as hue,
  accept `dE >= 10 OR |dL*| >= 12` under CVD simulation, and enforce that every category
  carries an icon *and* a text label so colour is never the sole encoder.

## Design tokens (authoritative — these are in code, not just here)

`app/src/main/kotlin/com/kharcha/app/ui/theme/` — `Color.kt`, `Theme.kt`, `Type.kt`,
`Spacing.kt`, `Shape.kt`, `Motion.kt`, `CategoryVisuals.kt`.

- Dark: bg `#14100E`, surface `#1C1714`, container `#261F1A`, containerHigh `#352C25`,
  onBg `#F6EFE6`, onSurfaceVariant `#B3A08C`, accent `#D4A03C`, debit `#D8583F`, credit `#8FAE4F`
- Light: bg `#FAF7F2`, surface `#FFFFFF`, container `#F1EAE0`, containerHigh `#E7DCCD`,
  onBg `#241C16`, accent `#A16207`, debit `#B0402B`, credit `#5C7A2E`
- Spacing 4/8/12/16/24/32; gutter 16dp; card padding 14dp; min touch 48dp
- Shape: card 16dp, small 10dp, pill 999dp. Elevation is **tonal (MD3), never drop shadows**
- Motion: easing `cubic-bezier(.16, 1, .3, 1)`; spring damping .75 / stiffness 90; press scale .97

`PaletteUsageTest` fails the build if a composable reads `KharchaColors`/`KharchaTypography`
directly instead of `MaterialTheme.colorScheme`/`typography`. This is deliberate — v1 drew
Neutral95 text on a Neutral95 background in light mode exactly that way.

## Status

**All four screens, Settings and export are built, committed and playtested on an
emulator.** `./gradlew test` is green across `:parser`, `:data` and `:app`.

What v2 changed, in the order it was built:

- parser: the `debited by`/`credited by` family, `RemarkParser` (rail and counterparty as
  separate axes), an expanded ignore list, and separator-tolerant sender matching
- the v2 design tokens, and CSV/JSON export
- nav reordered to **Dashboard · Budgets · Transactions · Inbox**, with "Unparsed"
  relabelled "Inbox" (the route string stays `unparsed`, so saved back-stack state survives)
- all four screens rebuilt on the design system, plus a theme toggle
- "Sync now" made real through `BackfillGate.rescan()`
- Settings reachable, redrawn on the system, and its export corrected

The branch was squash-merged to `master`, so these are one commit there rather than the
seven they were on the branch.

Settings is reached from the gear in the Dashboard app bar, not the bottom bar — a
fifth tab would crowd four that are visited daily. Its route is `SETTINGS_ROUTE` in
`KharchaNavHost`, pushed on top of the current tab so Back returns to it.

### Verified on a device

Emulator, Medium_Phone_API_36.1, 24 Aug 2026: SMS → `SmsReceiver` → `IngestWorker` →
Room → UI; "Sync now" importing a backlog; categorising a transaction and the choice
propagating to Budgets and to the export; export through the SAF picker producing a
correct CSV on disk; light and dark both legible on every screen.

### Not verified on real hardware

The permission walkthrough on a fresh install, backfill against a real OEM SMS
provider, budget notifications actually posting, multipart (>160 char) reassembly, and
the Room 1→2 migration over an existing database.

## Not built, deliberately

Net worth, investment accounts, bill negotiation, multi-user sharing — all require data the
SMS alerts do not carry. Sankey cash-flow diagrams are unreadable at phone width; the
in/out/net bar conveys the same thing.

## Open question for the user

The six new ignore rules (password changed, balance enquiry, card activation, promos,
declines, login alerts) were written against **invented** SBL phrasing — no real samples were
available. They are deliberately narrow so a mismatch fails safe (message stays
`Unrecognized` rather than being wrongly discarded), but they may never fire on real messages.
Promo detection currently requires the literal string `T&C Apply`.

**Ask the user for ~30 raw unparsed message bodies and retune `SblAlertRuleset` against them.**
This is the single highest-value outstanding item.

## Verification stance

Three of the agent workstreams reported success while containing a real defect:

- the parser collapsing every wallet transfer to one merchant,
- an export CSV-injection guard that wrapped `=SUM(...)` in quotes — which does nothing, since
  spreadsheets strip quotes on import and evaluate anyway (fix: prefix with `'`),
- a colour test measuring raw gamma-encoded sRGB Euclidean distance while calling itself
  perceptual, with a threshold that would have passed a palette of near-identical colours.

All three were caught by reading the code, not the summary. **Do not commit agent output on
the strength of its own report.** Run the tests yourself and read the diff.

## Finishing

Done: Settings route registered, full suite green, app launched and driven on an emulator.
What is left is not code — it is the message samples the open question above asks for.
