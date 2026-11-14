# App Store listing for Sevens

Draft text to paste into App Store Connect. Character limits are Apple's; counts are in brackets.
Items marked **TODO** need a decision or a detail from you.

## Name and subtitle

- **App name** (30 max): **TODO**. Plain "Sevens" is very likely taken. Options:
  - `Sevens: Spades Rule` [19]
  - `Sevens - Spade Rules Card Game` [30]
  - `Spade Sevens` [12]
- **Subtitle** (30 max): `Classic cards, spades rule` [26]
- **Name under the icon:** Sevens (already set in the project)

## Promotional text (170 max)

> The classic card game Sevens, played the spade-ruled way: no card goes down until its spade does. Free to play against three computer opponents, with no ads. [157]

## Description

> Sevens is the classic card game of building each suit out from its 7. This version plays by the spade-ruled house rules: a card in any other suit can only be played once the same card in spades is on the table. Spades set the pace, and holding the right spade at the right time decides the round.
>
> Play against three computer opponents. Get rid of your cards first, and leave everyone else counting the points still in their hands. Lowest score wins.
>
> HOW IT PLAYS
> • Whoever holds the 7 of spades opens the round.
> • Every suit builds outward from its 7, up to the king and down to the ace.
> • Hearts, clubs and diamonds have to wait for their spade.
> • Nothing to play? Ask the player before you for a card. They choose which one you get.
> • When someone asks you, hand over your worst card.
>
> OPTIONAL JOKERS
> Add one or two jokers. A joker stands in for any playable card, and the real card it replaced becomes dead: unplayable and worth 30 points to whoever is stuck holding it. Pass it on while you can.
>
> FREE TO PLAY
> • Short (50) and Standard (100) games against three computer opponents
> • How to play pages and a guided practice round for new players
> • Haptic feedback on iPhone
> • iPhone and iPad, with a wide table layout for iPad in landscape
> • Plays entirely offline
> • No ads, no accounts, no data collected
>
> THE FULL GAME (one-time purchase)
> • Jokers: play with one or two
> • Long (200) and Epic (500) games
> • Game history that shows whether you are trending better over your last 10 games
>
> You can also tip your dealer. Tips are optional and unlock nothing extra.

## In-app purchases

Create these in App Store Connect with exactly these product IDs; the app looks them up by ID. Prices are suggestions.

| Product ID | Type | Reference name | Price | Display name | Description |
|---|---|---|---|---|---|
| `sevens.fullgame` | Non-consumable | Full Game | $2.99 | Full Game | Jokers, Long and Epic games, and your game history. |
| `sevens.tip.small` | Consumable | Small tip | $0.99 | Small tip | A small thank-you to the dealer. |
| `sevens.tip.medium` | Consumable | Generous tip | $2.99 | Generous tip | A generous thank-you to the dealer. |
| `sevens.tip.large` | Consumable | Big tip | $4.99 | Big tip | A very generous thank-you to the dealer. |

Notes:

- Selling anything needs the Paid Apps agreement, with banking and tax details, in App Store Connect.
- Each purchase needs a review screenshot; the store screen in the app works for all four.
- Turn on Family Sharing for the Full Game if you want one purchase to cover a family.
- The app already has the required "Restore purchases" button on its store screen.

## Keywords (100 max, comma separated, no spaces)

`sevens,fan tan,parliament,card,cards,spades,jokers,solitaire,offline,classic,family,bots,domino` [95]

## What's New (version 1.0)

> First release.

## Categories

- **Primary:** Games
- **Game subcategory:** Card
- **Secondary (optional):** Games > Family

## URLs

- **Support URL:** **TODO** the published address of `docs/index.html`
- **Privacy Policy URL:** **TODO** the published address of `docs/privacy.html`
- **Marketing URL:** optional, can be left blank

## Copyright

`2026 Chris Ubick` (App Store Connect adds the © symbol itself)

## App Privacy answers

- **Do you or your third-party partners collect data from this app?** No.
- Result shown on the store page: "Data Not Collected".

This matches the app: it makes no network requests, has no analytics, ads or accounts, and keeps game history only on the device.

## Age rating questionnaire

Answer "None" or "No" to everything. In particular:

- Simulated gambling: None. Sevens is a card game with no betting, chips or wagering.
- Contests, unrestricted web access, user-generated content, messaging: No.

Expected result: the lowest age rating.

## Export compliance

The project already declares that the app uses no non-exempt encryption, so App Store Connect should not ask on upload.

## Notes for App Review

> Sevens is a single-player card game against three computer opponents. It needs no account and no network connection.
>
> To see the free features quickly: on the start menu, tap "Practice round" for a short guided hand and "How to play" for the rules. To play a full game, choose "Short · 50" and tap "Deal".
>
> In-app purchases are on the store screen, opened with "Unlock full game" on the start menu or by tapping any option marked with a lock. The Full Game purchase unlocks jokers, the Long and Epic game lengths, and the History screen. The three tips are consumable, optional, and unlock nothing. "Restore purchases" is on the same screen.

## Screenshots

Captured in `AppStore/screenshots/`:

- `iphone-6.9/` at 1290 × 2796, for the 6.9-inch iPhone slot
- `ipad-13/` at 2064 × 2752, for the 13-inch iPad slot

Suggested order: table, joker placement, practice round, how to play, history, menu.

## Still to decide

- **App name** from the options above, or your own.
- **Bundle ID.** The project still uses `app.sevens`. Pick one you own, for example `com.ubick.sevens`, and it needs changing in the project before the first upload.
- **Support email** for the support page.
- **Where to publish** the two pages in `docs/`.
