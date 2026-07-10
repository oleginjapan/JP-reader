# Japanese Learning App — Architecture

## 1. What we learned from the provided files

**Vocabulary deck (`JLPT_N5-N1.apkg`)**
- 7,597 notes, one note type: `Expression | English definition | Reading | Grammar | Additional definitions`
- Reliable level signal is the note **tag** `jlpt_N1`…`jlpt_N5` (the deck folder names in this
  file are nested/cumulative and should NOT be used for level filtering).
- Counts: N5=625, N4=613, N3=1588, N2=1723, N1=3048

**Grammar deck (`JLPT_Grammar.apkg`)**
- 4,281 notes (Jtest4you), note type: `English | Japanese | Level | Meaning | Romanized | Vocab`
- `Level` field is clean (`N5`…`N1`) and matches deck names — usable directly.
- Counts: N5=237, N4=696, N3=858, N2=1229, N1=1260

Both files are standard `.apkg` (SQLite inside a zip). They get imported into AnkiDroid once,
normally — our app does not read the `.apkg` files itself at runtime. It only talks to the
already-imported decks through AnkiDroid's API.

## 2. Core architectural decision: don't fork AnkiDroid, talk to it

AnkiDroid ships a public, documented **ContentProvider** (`FlashCardsContract`,
authority `com.ichi2.anki.flashcards`) built exactly for third-party apps like this one. It lets
us:
- Query decks, note fields, and tags
- Query cards due for review, and their scheduling state
- Write review answers (ease 1–4) back into AnkiDroid, so its own SRS scheduler (SM-2 based)
  handles all retention tracking — we don't reimplement spaced repetition.

Consequence: our app is a **companion app**, not a fork. It requires AnkiDroid to be installed
(we should detect this on first launch and deep-link to the Play Store / F-Droid if missing), and
AnkiDroid's permission `com.ichi2.anki.permission.READ_WRITE_DATABASE` granted to us.

This is both less work and more correct — retention data lives in one place (AnkiDroid's own DB),
so AnkiDroid's existing stats/graphs/etc. keep working, and nothing gets out of sync.

## 3. AI story generation — corrected approach

The original request described "signing into ChatGPT/Claude/Grok with a Google account" to
generate stories. That doesn't map to anything these providers support — Google Sign-In
authenticates a *person* into a *website*, not an *app* into an *API*, and scripting a login to
scrape the consumer chat UI breaks constantly and violates those sites' Terms of Service.

Instead: each provider (OpenAI, Anthropic, xAI) has an official developer API that accepts an
**API key**. The user generates a key on the provider's site (a one-time setup step, same as
they'd do for any app) and pastes it into our Settings screen. From then on the app calls the
API directly over HTTPS — no browser automation, no scraping, fully within each provider's terms.

Settings needs:
- A field per provider to paste/store the API key (stored in `EncryptedSharedPreferences`)
- A "Test connection" button per provider that fires a minimal request and reports success/failure
- A default-provider picker (which one to use for story generation)

## 4. App structure (standalone Reading companion)

Since AnkiDroid is already installed and handles vocabulary/grammar review directly, this app
does **not** duplicate New Words / Grammar review screens. It is a single-purpose companion:

```
Home
└── Reading
    ├── pick level (auto-detected from AnkiDroid card states, or manual override)
    ├── query AnkiDroid for word states at that level + one level down:
    │     - "mature" cards (interval ≥ 21 days)   → known, safe to use freely
    │     - "young/learning" cards                 → currently being learned, prioritize
    │     - "new" (never reviewed)                 → excluded by default (not learned yet)
    ├── send to configured AI provider with a constrained prompt
    ├── render story with tap-to-reveal furigana over every kanji word
    └── log which vocab/grammar were used (tag the AnkiDroid notes,
        e.g. "used_in_reading", so future stories can favor unseen/less-practiced words)

Settings
├── AI provider keys + test connection
└── Deck/level selection & weighting for story generation
```

New Words and Grammar screens, and any local SRS logic, are intentionally out of scope — the
user reviews those directly in AnkiDroid.

## 5. Furigana rendering

Android `TextView` doesn't support HTML `<ruby>/<rt>`. We render it ourselves: each word is a small
column — a small furigana `Text` centered above a larger kanji `Text` — inside a `FlowRow` so lines
wrap naturally, matching the reference screenshot (`Turn_on.jpg`). Furigana is hidden by default
and toggles to visible on tap of that word (state per word, not global), matching "click on the
kanji to see hiragana."

## 6. Data flow for one reading session

1. User opens Reading → picks/confirms level (default: current study level)
2. App queries AnkiDroid for: vocab tagged `jlpt_<level>` and `jlpt_<level-1>`, grammar at the
   same two levels — weighted toward cards the user has NOT yet reviewed a lot ("new/struggling"
   words), per your "keep track of what words were used and remembered well" requirement
3. App builds a prompt: target level, word list, grammar list, desired length, and asks the model
   to use as many of the supplied words/grammar as natural, in valid, level-appropriate Japanese
4. Response is parsed into a story + a list of which supplied words it actually used
5. Story renders with per-word furigana toggle
6. On finishing, the actually-used words get tagged in AnkiDroid (e.g. `used_in_reading:<date>`)
   so review stats and reading-material variety both benefit from the same source of truth

## 7. Key open questions for you

- Do you already have AnkiDroid installed with these two decks imported, or should first-run
  handle import automatically (we can trigger AnkiDroid's import intent for a bundled `.apkg`)?
- Story length / difficulty controls — fixed length, or user-adjustable?
- Which AI provider(s) do you want as default options (all three, or start with one)?
