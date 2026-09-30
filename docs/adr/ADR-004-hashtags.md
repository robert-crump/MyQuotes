# ADR-004: Hashtags replace the single Category

**Status:** Accepted (2026-09-30, issue #49; split from #48)

## Context

A quote had one free-text Category. A quote often belongs in several places at once (a Twain line is both humor and courage), so users had to pick one. The Category set also kept names the user had added but never used, next to the ones in use, and nothing stopped "Love" and "love" from existing side by side.

## Decision

1. **Many tags per quote.** `Quote.getTags()` is a list, never null, normalized, deduplicated ignoring case and always alphabetical. `getCategory`/`setCategory` are removed, with no deprecated shim.
2. **One token, stored without `#`.** A tag is Unicode letters, digits and `_`. It is stored bare and displayed as `#Tag`; a typed leading `#` is dropped. The rules live in one pure class, `Hashtag`.
3. **One spelling per tag.** Tags compare ignoring case, and the spelling that already exists in the collection wins, when typing, on add/edit, on rename (which then merges), and when loading or importing (the first spelling found wins).
4. **The Hashtag set is in-use-only.** It is the union of the tags on all quotes. There is no stored list and no Add action. `CategoryStore`/`SharedPreferencesCategoryStore` are deleted and the `CategoryPrefs/saved_categories` key is removed on startup. Renaming and deleting go through one batched `QuoteCollection.replaceTag` each.
5. **Format versions.** `QuoteCodec` writes `"tags": [...]` and no `"category"`, at envelope version 2. `BackupDocument` goes to version 3. Decoding uses `"tags"` when present, and otherwise converts `"category"` (invalid characters dropped, words joined in CamelCase: `"Life Wisdom"` → `LifeWisdom`; empty → no tags). Old prefs, v1/v2 backups and bare arrays all still load. `QuoteCollection.loadFromStore` saves once when the store held the old format, so the store is upgraded on the first start.
6. **Tapped tags search exactly.** `QuoteQuery` gets an `exact` flag (carried in intents and bundles): a tapped tag matches whole tags ignoring case, with no 3-character minimum. Typed queries keep contains matching, per tag, ignoring a leading `#`.

## Consequences

- **Downgrade compatibility is dropped.** An older build reading a v3 backup or the upgraded store finds no `"category"` and loses the tags (quotes themselves still load). Keeping a `"category"` copy would have meant choosing one tag to write back, and it would silently drift from the tags.
- Category names that no quote used are gone after the upgrade. That is intended: a tag exists while a quote has it.
- Share and notification text don't include tags.
- Tag suggestions while editing are a separate follow-up issue; the Add/Edit field leaves room for a "Suggested:" row.

## Alternatives rejected

**Keep Category and add tags.** Two overlapping ways to group quotes, and the Category would just be a privileged tag.
**Keep a stored tag list.** It brought back the "names without quotes" state and a second source of truth to keep in sync on rename and delete.
**Store tags with `#`.** Every comparison and the JSON would carry a character that is only presentation.
