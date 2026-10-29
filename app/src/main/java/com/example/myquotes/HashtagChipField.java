package com.example.myquotes;

import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * The Add/Edit "Hashtags" field ({@code hashtag_field} in {@code activity_add_edit_quote}): the
 * quote's tags as input chips, alphabetical, followed by the text being typed. Space, Enter, comma
 * or a dropdown pick commits the text as a chip; characters a tag can't hold are dropped while
 * typing; a tag the quote already has is ignored, and one that exists elsewhere in the collection
 * keeps its spelling. A chip's × removes it; Backspace in the empty field highlights the last
 * chip, a second Backspace removes it. The dropdown lists existing tags starting with the input,
 * most-used first, at most 3, with counts; empty input (or a lone {@code #}) has none. It opens below
 * the field ({@link DropDownBelowTextView}); while the field has focus the form is scrolled to put
 * it at the top, with blank space added below the form when it is too short to scroll that far.
 */
final class HashtagChipField {
    private static final int MAX_SUGGESTIONS = 3;

    private final Context context;
    private final ChipGroup group;
    private final AutoCompleteTextView input;
    private final TextView label;
    private final View underline;
    private final View field;
    private final ScrollView scroll;
    private final View form;
    private final int formPaddingBottom;
    private final Hashtags hashtags;
    private final FieldSuggestionAdapter adapter;
    private final List<String> tags = new ArrayList<>();
    private boolean rewriting;
    private Chip highlighted;
    private Runnable onTagsChanged;

    HashtagChipField(View root, LifecycleOwner owner, QuoteCollection collection, Hashtags hashtags) {
        context = root.getContext();
        group = root.findViewById(R.id.chip_group_hashtags);
        input = root.findViewById(R.id.edit_text_hashtags);
        label = root.findViewById(R.id.label_hashtags);
        underline = root.findViewById(R.id.hashtag_underline);
        field = root;
        scroll = enclosingScrollView(root);
        form = scroll.getChildAt(0);
        formPaddingBottom = form.getPaddingBottom();
        this.hashtags = hashtags;

        adapter = new FieldSuggestionAdapter(context);
        adapter.setField(QuoteQuery.Field.HASHTAGS);
        adapter.setLimit(MAX_SUGGESTIONS);
        input.setAdapter(adapter);
        collection.getQuoteList().observe(owner, quotes -> adapter.setQuotes(new ArrayList<>(quotes)));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!rewriting) onTyped(s);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                commitPending();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DEL || event.getAction() != KeyEvent.ACTION_DOWN
                    || input.length() > 0 || tags.isEmpty()) {
                return false;
            }
            if (highlighted != null) {
                remove((String) highlighted.getTag());
            } else {
                highlight((Chip) group.getChildAt(tags.size() - 1));
            }
            return true;
        });
        input.setOnItemClickListener((parent, view, position, id) -> commitPending());
        input.setOnFocusChangeListener((v, hasFocus) -> {
            int color = resolveColor(hasFocus ? androidx.appcompat.R.attr.colorPrimary
                    : com.google.android.material.R.attr.colorOnSurfaceVariant);
            label.setTextColor(color);
            underline.setBackgroundColor(color);
            if (hasFocus) {
                liftField();
            } else {
                highlight(null);
                setFormExtraBottom(0);
            }
        });
        // The keyboard coming or going resizes the form's viewport: lift again. Scrolling moves the
        // space below the field: refit the open dropdown to it.
        scroll.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (b - t != oldB - oldT && input.hasFocus()) liftField();
        });
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (input.isPopupShowing()) input.showDropDown();
        });
        // A tap on the field outside the chips and the text goes to the text.
        group.setOnClickListener(v -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    /** Replaces the chips; clears the text being typed. */
    void setTags(List<String> newTags) {
        tags.clear();
        tags.addAll(Hashtag.normalizeAll(newTags));
        setInputText("");
        render();
    }

    /** Runs after every change to the chips. */
    void setOnTagsChangedListener(Runnable listener) {
        onTagsChanged = listener;
    }

    /** Adds {@code tag} as a chip (a suggestion tap); ignored if the quote already has it. */
    void addTag(String tag) {
        add(tag);
    }

    /** The committed chips (alphabetical). */
    ArrayList<String> getTags() {
        return new ArrayList<>(tags);
    }

    /** The chips plus the text being typed, as Save would store them. */
    List<String> tagsWithPending() {
        List<String> all = new ArrayList<>(tags);
        String pending = Hashtag.normalize(input.getText().toString());
        if (!pending.isEmpty() && !Hashtag.containsIgnoreCase(all, pending)) {
            all.add(hashtags.canonical(pending));
        }
        return Hashtag.normalizeAll(all);
    }

    /** Commits the text being typed as a chip (Save does this before reading the tags). */
    void commitPending() {
        String pending = input.getText().toString();
        setInputText("");
        add(pending);
    }

    void dismissDropDown() {
        input.dismissDropDown();
    }

    /**
     * Commits everything before the last separator and drops characters a tag can't hold (a
     * leading {@code #} is kept). Edits {@code s} in place rather than calling setText, which
     * would reset the keyboard's input state and lose the next keystroke.
     */
    private void onTyped(Editable s) {
        highlight(null);
        rewriting = true;
        int separator = -1;
        for (int i = s.length() - 1; i >= 0 && separator < 0; i--) {
            if (isSeparator(s.charAt(i))) separator = i;
        }
        if (separator >= 0) {
            String committed = s.subSequence(0, separator).toString();
            s.delete(0, separator + 1);
            for (String part : committed.split("[\\s,]")) add(part);
        }
        for (int i = s.length(); i > 0; ) {
            int cp = Character.codePointBefore(s, i);
            int start = i - Character.charCount(cp);
            if (!Hashtag.isTagChar(cp) && !(cp == '#' && start == 0)) s.delete(start, i);
            i = start;
        }
        rewriting = false;
    }

    private static boolean isSeparator(char c) {
        return c == ',' || Character.isWhitespace(c);
    }

    private void add(String raw) {
        String tag = Hashtag.normalize(raw);
        if (tag.isEmpty() || Hashtag.containsIgnoreCase(tags, tag)) return;
        tags.add(hashtags.canonical(tag));
        tags.sort(Hashtag.ORDER);
        render();
    }

    private void remove(String tag) {
        int index = Hashtag.indexOf(tags, tag);
        if (index < 0) return;
        tags.remove(index);
        render();
    }

    private void setInputText(String text) {
        rewriting = true;
        input.setText(text);
        input.setSelection(text.length());
        rewriting = false;
    }

    /** One chip per tag, in order, before the text field (always the group's last child). */
    private void render() {
        highlighted = null;
        group.removeViews(0, group.getChildCount() - 1);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);
            Chip chip = (Chip) inflater.inflate(R.layout.chip_hashtag, group, false);
            chip.setText(Hashtag.display(tag));
            chip.setTag(tag);
            chip.setCloseIconContentDescription("Remove " + Hashtag.display(tag));
            chip.setOnCloseIconClickListener(v -> remove(tag));
            group.addView(chip, i);
        }
        adapter.setExcludedTags(tags);
        if (onTagsChanged != null) onTagsChanged.run();
    }

    /** Highlights {@code chip} as the next Backspace's target; null clears the highlight. */
    private void highlight(Chip chip) {
        if (highlighted != null) {
            highlighted.setChecked(false);
            highlighted.setCheckable(false);
        }
        highlighted = chip;
        if (chip != null) {
            chip.setCheckable(true);
            chip.setChecked(true);
        }
    }

    /** Scrolls the form so the field sits at the top, leaving the dropdown room below it. */
    private void liftField() {
        Rect bounds = new Rect();
        field.getDrawingRect(bounds);
        scroll.offsetDescendantRectToMyCoords(field, bounds);
        int top = bounds.top;
        int viewport = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        int natural = form.getHeight() - (form.getPaddingBottom() - formPaddingBottom);
        setFormExtraBottom(Math.max(0, top + viewport - natural));
        // After the padding is laid out; not smooth, since the text keeping its cursor in view
        // would cancel the animation halfway.
        scroll.post(() -> scroll.scrollTo(0, top));
    }

    private void setFormExtraBottom(int extra) {
        if (form.getPaddingBottom() == formPaddingBottom + extra) return;
        form.setPadding(form.getPaddingLeft(), form.getPaddingTop(), form.getPaddingRight(),
                formPaddingBottom + extra);
    }

    private static ScrollView enclosingScrollView(View view) {
        for (ViewParent p = view.getParent(); p != null; p = p.getParent()) {
            if (p instanceof ScrollView) return (ScrollView) p;
        }
        throw new IllegalStateException("The hashtag field must sit in a ScrollView");
    }

    private int resolveColor(int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }
}
