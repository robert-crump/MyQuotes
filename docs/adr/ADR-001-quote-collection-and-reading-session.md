# ADR-001: Split QuoteViewModel into QuoteCollection and ReadingSession

**Status:** Accepted  
**Date:** 2026-05-24

## Context

`QuoteViewModel` mixed two distinct concerns: the canonical quote store (CRUD, persistence) and a shuffled reading session (deck order, position, currently displayed quote). An implicit invariant — the shuffled list must stay in sync with the canonical list — was enforced by hand in four places (`addQuote`, `updateQuote`, `deleteQuoteById`, `setQuoteList`). The shuffled list also leaked to callers via `getShuffledQuoteList()`.

## Decision

**QuoteCollection** is a plain Java object (not a ViewModel) held by `MyApplication` for the lifetime of the process. It owns the canonical list, CRUD operations, id assignment, persistence, and view-count recording. It is the single source of truth for quote data.

**ReadingSession** is an Android `ViewModel` scoped to `MainActivity`. It owns the shuffled deck, current position, and the currently displayed quote. It subscribes to `QuoteCollection.getQuoteList()` via `observeForever` and reconciles the deck reactively: new quotes append to the end, deleted quotes are removed with position adjusted, updated quotes are replaced in place.

## Consequences

- The sync invariant lives in one place (`ReadingSession.onCollectionChanged`) and is testable by mutating the collection and asserting deck state.
- `MainActivity` observes `ReadingSession.getDeck()` directly; the 100ms `postDelayed` hacks are gone because `setValue` is synchronous.
- `FavoritesActivity`, `SearchActivity`, `CategoriesActivity`, `StatisticsActivity`, and `SettingsActivity` talk directly to `QuoteCollection` — none need a reading session.
- `MyAppViewModelStoreOwner` is deleted: it was only needed to give `QuoteViewModel` application scope, which `QuoteCollection` achieves simply by living in `MyApplication`.

## Alternatives rejected

**Keep QuoteCollection as a ViewModel** — unnecessary; it has no UI lifecycle concerns. Plain object with `MutableLiveData` is sufficient and removes `ViewModelProvider` boilerplate.

**ReadingSession as application-scoped singleton** — wrong scope. The session belongs to `MainActivity`'s lifecycle. Scoping it to the application would mean a stale session after the Activity is recreated.
