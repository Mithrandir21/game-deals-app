# ADR 0001 — Deal source: migrate CheapShark → IsThereAnyDeal (ITAD)

- **Status:** Accepted
- **Date:** 2026-06-02
- **Deciders:** project owner
- **Epic:** [#205](https://github.com/Mithrandir21/game-deals-android-app/issues/205)
- **Spike:** [#206](https://github.com/Mithrandir21/game-deals-android-app/issues/206) (resolved)

## Context

The app currently sources all deal/price/store/release data from the **CheapShark** API. We ran a
spike (#206) to evaluate **IsThereAnyDeal (ITAD)** as a replacement, comparing data coverage,
regional support, price history, and the Steam-appID → IGDB enrichment bridge the app relies on.

## Decision

**Phased full replace** of CheapShark with **ITAD**, kept behind a generalized, source-neutral
`DealsSource` seam so the provider can be swapped (or fall back) without touching callers.

## Comparison

| Capability              | CheapShark            | ITAD                                                  |
| ----------------------- | --------------------- | ----------------------------------------------------- |
| Stores                  | ~12, US-centric       | 30+ (PlayStation/Xbox/Ubisoft/Fanatical…)             |
| Regional pricing        | US-only               | `country` param, ~40 regions                          |
| Price history           | cheapest-ever + date  | full time-series (`/games/history/v2`) + per-store lows |
| Bundles / subscriptions | none                  | yes                                                   |
| Vouchers                | none                  | yes                                                   |
| Waitlist / collection   | email alerts          | OAuth waitlist + owned-games                          |
| Steam appID → IGDB bridge | yes                 | yes (`/games/lookup?appid=`)                          |

## Rationale

ITAD wins on data and integrates better: it keeps the IGDB bridge and adds real price history,
bundles, and regional pricing. The migration is **not** a drop-in:

- Game ids are **UUIDs** → a Room schema migration is required (favourites preserved by
  re-resolving title / Steam appID).
- Deals expose **direct affiliate URLs** (no `cheapshark.com/redirect` indirection).
- ITAD has **no releases endpoint** → Home "new releases" moves to IGDB `first_release_date`.
- An **API key + caching** are required; respect HTTP 429 + `Retry-After`.

## Consequences / caveats

ITAD's Terms of Service impose obligations we must honor (verbatim):

- "You MUST NOT make an app that could be considered a competition to IsThereAnyDeal or
  IsThereAnyDeal projects."
- "You MUST NOT use this API to directly or indirectly help the competition of IsThereAnyDeal."
- "You MAY use this API for commercial purposes IF the resulting app is available to public."
- "We reserve the right to deny you access to the API at any point without notice."
- "You MUST NOT change provided data in any way [incl.] remove affiliate tags from the URLs."

**Mitigation:** keep the source strictly behind a neutral `DealsSource` interface so CheapShark
(or another provider) remains a cheap fallback if ITAD access is revoked, and never strip affiliate
tags from deal URLs. Add an ITAD attribution link in the UI.

## Phased rollout (see epic #205)

- **Phase 0** — Decouple the seam (`DealsSource`, url-in-model, neutral `Store`). Still CheapShark,
  no behavior change.
- **Phase 1** — `:remote:itad` module + API key, implementing `DealsSource` (#207).
- **Phase 2** — Swap DI + Room migration; move releases to IGDB.
- **Phase 3** — Real price-history chart (#208), bundles, regional pricing (#212).
- **Phase 4** — Remove `:remote:cheapshark`.

## Outcome

Proceed with the phased full replace. Spike #206 is closed.
