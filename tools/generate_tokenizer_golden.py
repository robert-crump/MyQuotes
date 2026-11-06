"""Writes the golden token IDs for the Java XLM-R tokenizer (#60).

One-time script, not part of the build. Runs the official Hugging Face tokenizer on sample
sentences and writes the expected IDs (no truncation) to the test fixture. Run it after
`./gradlew downloadEmbeddingModels`:

    pip install tokenizers
    python tools/generate_tokenizer_golden.py

The model directory defaults to the Gradle cache; pass another one as the first argument.
"""
import json
import os
import sys

import tokenizers
from tokenizers import Tokenizer

MODELS = {
    "e5": "multilingual-e5-small/761b726dd34fb83930e26aab4e9ac3899aa1fa78",
    "minilm": "paraphrase-multilingual-MiniLM-L12-v2/2c4055b12046f11709e9df2c122e59ffbdc2f900",
}

LONG = (
    "Es war einmal ein kleines Mädchen, das lebte mit seiner Mutter in einem Häuschen am Rande "
    "eines großen Waldes. It was the best of times, it was the worst of times, it was the age of "
    "wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of "
    "incredulity, it was the season of Light, it was the season of Darkness, it was the spring of "
    "hope, it was the winter of despair. Nel mezzo del cammin di nostra vita mi ritrovai per una "
    "selva oscura, ché la diritta via era smarrita. Longtemps, je me suis couché de bonne heure. "
) * 3

SENTENCES = [
    "",
    " ",
    "Hello world",
    "The only thing we have to fear is fear itself.",
    "To be, or not to be: that is the question.",
    "  Leading and trailing spaces  ",
    "Several    spaces   between words",
    "Line one\nLine two",
    "Windows line\r\nbreak",
    "Paragraph one.\n\nParagraph two.",
    "Tab\tseparated\tvalues",
    "Non breaking space",
    "Zero​width​space and soft­hyphen",
    "Was mich nicht umbringt, macht mich stärker.",
    "Die Grenzen meiner Sprache bedeuten die Grenzen meiner Welt.",
    "Straße, Größe, Äpfel, Öl, Übermut – ß und ẞ",
    "Donaudampfschifffahrtsgesellschaftskapitänsmützenabzeichen",
    "„Deutsche Anführungszeichen“ und »Guillemets«",
    "Nel mezzo del cammin di nostra vita",
    "Perché l'amore è più forte dell'odio?",
    "L'enfer, c'est les autres.",
    "Je pense, donc je suis — René Descartes",
    "Ça déjà été où? À côté de l'église, naïve Noël.",
    "“Curly quotes” and ‘single ones’ and it’s",
    "Wait... what?! (Really?) [Yes] {No} <maybe>",
    "Ellipsis… em—dash en–dash minus − and hyphen-ated",
    "100% of $5 = €4,50 & 3 × 4 ÷ 2 ≈ 6 ± 1",
    "e-mail: someone@example.com, web: https://example.com/a?b=c#d",
    "#Hashtag @mention ~tilde ^caret |pipe \backslash",
    "I ❤️ quotes 😀😀 and 👍🏽 and 👨‍👩‍👧 and 🇩🇪",
    "©️ ®️ ™ ① ② ⅷ ½ ² ℃",
    "Decomposed: é ä ô ñ",
    "Ligatures: ﬁ ﬂ ﬃ and ﬁ́",
    "Fullwidth: ＡＢＣ　１２３！",
    "Halfwidth katakana: ｶﾞｷﾞ",
    "日本語のテキスト、中文文本。",
    "Привет, мир! Γειά σου Κόσμε!",
    "مرحبا بالعالم",
    "한국어 텍스트 각",
    "Hieroglyphs 𓀀𓀁𓀂 unknown",
    "Control\u0007char and \u0000null",
    "ALL CAPS SHOUTING",
    "MiXeD CaSe WoRdS",
    "Numbers 1234567890 3.14159 1,000,000",
    "query: Was mich nicht umbringt, macht mich stärker.",
    "a",
    "!!!",
    "Ünïcödé ëvérÿwhérè",
    " line separator paragraph separator",
    "﻿BOM at start",
    LONG,
]


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.expanduser("~"), ".gradle", "caches", "myquotes-embedding-models")
    tokenizers_by_model = {}
    for key, path in MODELS.items():
        tokenizer = Tokenizer.from_file(os.path.join(base, path, "tokenizer.json"))
        tokenizer.no_truncation()
        tokenizer.no_padding()
        tokenizers_by_model[key] = tokenizer

    cases = []
    for text in SENTENCES:
        case = {"text": text}
        for key, tokenizer in tokenizers_by_model.items():
            case[key] = tokenizer.encode(text).ids
        cases.append(case)

    out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "test", "resources",
                       "embedding", "tokenizer-golden.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"tokenizers": tokenizers.__version__, "cases": cases}, f, ensure_ascii=True)
        f.write("\n")
    print(f"Wrote {len(cases)} cases to {os.path.normpath(out)}")


if __name__ == "__main__":
    main()
