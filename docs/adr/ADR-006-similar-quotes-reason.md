# ADR-006: Similar quotes as a hashtag suggestion reason, with paraphrase-multilingual-MiniLM-L12-v2

**Status:** Accepted (2026-11-06, issue #61; parent #58). Wired into the app in #62 (Quote Embeddings).

## Context

Hashtag suggestions (#50, #52) find reasons in shared words, the author, the source and co-occurring tags. A quote that says the same thing in other words, or in another language, gets nothing. #58 decided to add one more reason based on on-device sentence embeddings and k nearest neighbours, and to let a bake-off on the user's real collection choose the model (#59 eval, #60 embedding pipeline).

## Decision

1. **Reason: kNN over tagged quotes.** For the quote being judged, take the `KNN_K` = 10 tagged quotes with the highest cosine similarity, ignoring any below `KNN_MIN_SIMILARITY` = 0.40. Untagged quotes are never neighbours, because a missing tag is missing data, not evidence against it. Tag T is a reason when at least `KNN_MIN_WITH_TAG` = 3 neighbours have it. The evidence is "withTag of the neighbours counted" (`Evidence.similar`), ranked by share and then count, like every other counted reason. A quote is never its own neighbour. Neighbours' rejected tags don't count. Eligibility is unchanged (the tag must be on ≥ 5 quotes). A quote without a vector has no such reason.
2. **Model: paraphrase-multilingual-MiniLM-L12-v2** (int8 ONNX, `@2c4055b1`, 384 dimensions, no prefix).
3. **Long quotes are cut off at 128 tokens.** No chunking.
4. **Similar-quotes evidence ranks with the counted reasons,** not below them.

## Numbers

Leave-one-out on the user's backup (451 quotes, 412 tagged, 39 untagged), top 3. Recall is "eligible" recall. Coverage of untagged quotes is the coverage the success bar uses, since tagged coverage starts at 93.4%.

| | P@3 | R@3 | coverage tagged | coverage untagged |
|---|---|---|---|---|
| Current model (baseline, = #59) | 34.9% | 47.0% | 93.4% | 46.2% |
| **MiniLM, k=10 m=3 floor 0.40** | **35.0%** (+0.1) | **58.2%** (+11.1) | 98.3% | 87.2% (+41.0) |
| MiniLM, k=10 m=3 floor 0.50 | 36.3% | 54.4% | 96.4% | 59.0% |
| MiniLM, k=10 m=3 no floor | 34.0% | 58.5% | 99.5% | 97.4% |
| e5-small, k=10 m=3 floor 0.86 (its best) | 34.8% | 53.6% | 96.8% | 64.1% |
| e5-small, k=10 m=3 floor 0.84 | 33.1% | 55.2% | 98.5% | 84.6% |

- **Success bar** (recall +5 or untagged coverage +10, with precision −5 at most): both models pass, and MiniLM is ahead at every setting.
- **Sweep:** k ∈ {5, 10, 20}, m ∈ {2, 3, 4}, six floors per model, each with and without ranking below the counted reasons. 108 sets per model. Ranking by "passes the bar, then P+R", k=10 m=3 came out on top for both models. m=2 adds about 1 point of recall but costs 2.5–4 points of precision. Ranking below the counted reasons was slightly worse in every set (about −0.5 precision, −1 recall).
- **Floor:** e5's cosines are squeezed together (nearest neighbour median 0.88, 10th nearest 0.86), so its floor has a narrow useful band. MiniLM's spread out (median 0.57 and 0.46), so 0.40 cleanly drops the weakest neighbourhoods. The floor is tuned for MiniLM, and another model needs its own.
- **Manual spot-check:** 20 random suggestions that the new reason adds and that the eval counts as wrong, because the quote lacks the tag. The user judged 11 of 20 as right, i.e. a missing tag rather than a wrong suggestion, including 6 of 8 on untagged quotes. The noes were all on broad tags (#Priorities ×3, #Productivity ×3, #Work, #Decisions, #TimeManagement ×1 each). So measured precision understates real precision (for the baseline as well).
- **Truncation:** 57 of 451 quotes exceed MiniLM's 128 tokens (60 for e5). The median quote is 56 tokens. The long ones have a median of 184, quartiles 138–236 and a maximum of 472, and 48 of the 57 would need only 2 pieces. Chunking was measured: pieces packed at sentence ends, each embedded, then averaged weighted by token count. For MiniLM at the chosen setting it changed recall by +0.1 (one tag) and precision by 0. For e5 it hurt (about −2.5 precision, −3.5 recall). The first ~90 words carry what matters for the tag.
- **Cost:** a full leave-one-out pass with all 108 sets takes about 12 s on a desktop JVM. Brute-force dot products are enough at this size.

## Consequences

- `HashtagSuggester.fit(quotes, vectors)` takes a read-only map from quote id to normalized vector. `fit(quotes)` is the same with no vectors, so existing callers behave exactly as before until the app computes vectors. `suggest(..., vector)` and `explain(..., vector)` take the judged text's vector. `Explanation.similar` and `similarQuoteIds` (most similar first) are there for the UI.
- The app must store a vector per quote, keyed by `Embedder.modelId()` so a model change invalidates them, and embed the text being edited. Done in #62 (`QuoteEmbeddings`, the model shipped uncompressed in the APK, +118 MB).
- Broad tags (#Priorities, #Productivity, #Work) collect most of the doubtful additions. If that grates in use, a per-tag rule is the lever. It was not part of this decision.
- The eval (`HashtagSuggestionEvalReport.similarQuotesBakeOff`) reruns everything, including the chunking comparison, from cached vectors in `app/build/embedding-cache`.

## Alternatives rejected

**multilingual-e5-small.** Same size and dimensions, but lower recall at every precision. Its narrow cosine range makes the floor fragile, and chunking hurts it.

**EmbeddingGemma-300M.** Only a fallback if neither candidate passed. Not needed, and it is about 2.5× larger.

**Gemini Nano** (#58). Too slow to rank every quote on every collection change, and not deterministic, so suggestions could change without the collection changing and the eval couldn't judge it.

**Tag centroids** (one mean vector per tag, suggest the nearest tags). A broad tag like #Life spans many topics, and its mean sits near everything and near nothing. The reason also couldn't point at concrete quotes ("3 of 10 similar quotes" can). Not measured.

**Weighted votes** (neighbours weighted by similarity). An unweighted count keeps the evidence in the same "withTag of total" form as every other reason, so it ranks and reads the same with no special case. Weights would need their own threshold and an explanation that isn't a count. The floor already removes weak neighbours. Not measured.

**Chunking long quotes.** Measured, no gain for MiniLM (see Numbers). It would also roughly double the embedding work for long quotes in the app.

## Addendum: zero-shot reason for small tags, measured and rejected (#64, 2026-11-07)

**Question.** Can a tag on 1–4 quotes, below `MIN_QUOTES_PER_TAG`, be suggested from its name alone, so that a new tag finds its older quotes? Two reasons were tried, both ranked below every other reason:
- **Stem:** the quote contains the tag's name (the existing name match, opened up to small tags).
- **Meaning:** the quote's MiniLM vector is close to the vector of the tag's name, i.e. cosine ≥ floor and among the N quotes without the tag that are closest to the name. The name was either bare and CamelCase split ("life wisdom") or in a template ("A quote about life wisdom.").

**Gate.** P@3 may drop by at most 5 points against the #61 model, **and** the user judges at least 10 of 20 spot-check items right. The items come from the per-tag "Suggested for" pass, and a missing tag counts as right.

**Numbers** (`HashtagSuggestionEvalReport.zeroShotReason`, 451 quotes; only 35 tag occurrences are on 1–4 other quotes):
- **P@3:** every one of the 49 variants passes (worst −1.3).
- **Stem:** top-3 recall for small tags 2.9%. The per-tag pass finds 17% of hidden quotes, with 5% measured precision.
- **Best meaning variant** (bare, floor 0.40, N=5): P@3 −0.1, top-3 recall for small tags 8.6%. The per-tag pass finds 28.6% of hidden quotes, with a mean size of 2.1 and 13.5% measured precision.
- **Stem with meaning** was worse than meaning alone in every set.
- **Spot-check:** stem 8 of 20, meaning 8 of 20. **Both fail** the 10 of 20 bar.

**Decision.** Neither ships. `HashtagSuggester.ZeroShot` stays at `OFF` and is used only by the eval, the same way the `Knn` sweep options are kept, so the measurement can be rerun when the collection has more small tags. The 35-occurrence sample is small, and 8 of 20 is close to the bar. If the reason is retried, start from the template name at floor 0.50, the variant with the highest measured small-tag precision, and spot-check it.
