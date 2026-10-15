# ADR-005: A hashtag filter replaces the exact tag query

**Status:** Accepted (2026-10-15, issue #51). Supersedes ADR-004 decision 6.

## Context

ADR-004 made a tapped tag an *exact query*: the search text became `#Tag`, scoped to Hashtags, with an `exact` flag. Every result matched on its tag, so every result line was the tag line (`#Life #Stoic`) and the list showed no quote text. A tag could not be combined with typed text, and there was no way to find the quotes that have no hashtag yet.

## Decision

1. **The hashtag filter is part of `QuoteQuery`, next to the text.** It is off, one tag (a whole tag, compared ignoring case) or "no hashtag". `forTag(tag)` is an empty text with that filter, and `untagged()` is the "no hashtag" filter. `withText` and `scopedTo` keep the filter. `withTag`, `withUntagged` and `withoutTagFilter` change it.
2. **The filter combines with the text.** A quote matches when it passes the filter and, if the text is at least 3 characters, the text matches as before. The filter alone makes a query active, with no minimum length.
3. **With only the filter, the result line is the start of the quote.** The tag line still appears when typed text matches a tag and not the quote text.
4. **`exact` is removed.** Typed text always matches by contains, including on the Hashtags scope. Bundles and intents carry the filter as `search_tag` (`""` for "no hashtag", absent when off). The old `search_exact` extra is no longer read. A saved search state from an older build comes back as the typed text `#Tag`, which still finds the tag.
5. **UI.** The search shows the filter as a chip under the field. The chip's × removes it, and tapping the chip changes it. The filter dialog's "Hashtag…" button picks "No hashtag" or a tag, each with its count. Tag taps on cards, in the quote dialog and in Statistics open the search with the filter set and an empty field.

## Consequences

- One tag at a time. Several tags (AND/OR) would need a different chip row and was not asked for.
- The Hashtags screen gains a "No hashtag · N" entry, and the tag sheet's "Show N quotes" uses the same filter.

## Alternatives rejected

**Keep the exact query and only change its snippet.** That fixes the result lines but still can't combine a tag with text or express "no hashtag".
