# MyQuotes — Domain Glossary

## Core domain objects

**Quote** — a single quotation with text, author, source, category, rating, favorite status, view count, and a stable integer id.

**Quote Collection** — the canonical, ordered set of all quotes the user owns. Responsible for CRUD, id assignment, persistence, and notifying observers when the set changes. Implemented as a plain Java object (not a ViewModel) held by `MyApplication` for the lifetime of the process. The authoritative source of truth for quote data.

**Reading Session** — a shuffled traversal of the Quote Collection. Tracks deck order, current position, and the currently displayed quote. Subscribes to the Quote Collection via `observeForever` and reconciles its deck reactively: new quotes append to the end, deleted quotes are removed with position adjusted, updated quotes are replaced in place. Implemented as an Android `ViewModel` scoped to `MainActivity`. Other flows (Favorites, Search results) maintain their own ordered views and do not use a Reading Session.

**Deck** — the ordered list of quotes inside a Reading Session, in the order they will be presented to the user. Initially a shuffle of the full collection; mutated in place as the collection changes.

**Reading position** — the zero-based index of the currently displayed quote within the Deck.

## Flows

**Main browse flow** — ViewPager2 in MainActivity driven by the Reading Session's Deck.

**Favorites flow** — ViewPager2 in FavoritesActivity driven by a filtered, recency-sorted view of the Quote Collection (not a Reading Session).

**Search / category flow** — list or pager in SearchActivity / CategoriesActivity driven by a filtered view of the Quote Collection.
