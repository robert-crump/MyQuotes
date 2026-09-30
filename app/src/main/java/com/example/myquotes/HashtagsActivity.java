package com.example.myquotes;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myquotes.databinding.ActivityHashtagsBinding;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Hashtag set as a wrapping flow of tags with their quote counts. A tap opens the exact tag
 * search; long-press starts multi-selection with a contextual bar: rename (exactly one; merging
 * into an existing tag asks first) and delete (removes the tags from their quotes, never the
 * quotes). There is no Add action: a tag exists while a quote has it.
 */
public class HashtagsActivity extends AppCompatActivity {
    private static final String STATE_SELECTED = "selected_hashtags";

    private ActivityHashtagsBinding binding;
    private Hashtags hashtags;
    private HashtagsAdapter adapter;
    // Selection mode is "some tag selected": the app bar turns into a contextual bar
    // (count, close, rename for exactly one, delete) and Back clears the selection.
    private final OnBackPressedCallback exitSelection = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            adapter.clearSelection();
        }
    };
    // Selection saved across recreation, applied once the first Hashtag set arrives.
    private ArrayList<String> pendingSelection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.Theme_MyQuotes_NoActionBar);

        binding = ActivityHashtagsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.apply(this, binding.statusBarScrim);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Hashtags");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        hashtags = MyApplication.getInstance().getHashtags();

        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(this);
        layoutManager.setFlexWrap(FlexWrap.WRAP);
        binding.hashtagsRecyclerView.setLayoutManager(layoutManager);

        adapter = new HashtagsAdapter(new HashtagsAdapter.Listener() {
            @Override
            public void onHashtagClick(String tag) {
                startActivity(QuoteQuery.forTag(tag).toIntent(HashtagsActivity.this));
            }

            @Override
            public void onSelectionChanged(Set<String> selected) {
                renderSelectionMode(selected);
            }
        });
        binding.hashtagsRecyclerView.setAdapter(adapter);
        getOnBackPressedDispatcher().addCallback(this, exitSelection);

        if (savedInstanceState != null) {
            pendingSelection = savedInstanceState.getStringArrayList(STATE_SELECTED);
        }
        hashtags.getHashtags().observe(this, tags -> {
            adapter.submit(tags, hashtags.quoteCounts());
            binding.hashtagsEmpty.setVisibility(tags.isEmpty() ? View.VISIBLE : View.GONE);
            if (pendingSelection != null) {
                adapter.setSelected(pendingSelection);
                pendingSelection = null;
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_SELECTED, new ArrayList<>(adapter.getSelected()));
    }

    private void renderSelectionMode(Set<String> selected) {
        boolean selecting = !selected.isEmpty();
        exitSelection.setEnabled(selecting);

        int barColor = Color.TRANSPARENT; // the AppBarLayout's own appBarColor shows through
        if (selecting) {
            TypedValue value = new TypedValue();
            getTheme().resolveAttribute(R.attr.selectionBarColor, value, true);
            barColor = value.data;
        }
        binding.toolbar.setBackgroundColor(barColor);
        binding.statusBarScrim.setBackgroundColor(barColor);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(selecting ? selected.size() + " selected" : "Hashtags");
            actionBar.setHomeAsUpIndicator(selecting ? R.drawable.ic_close : 0);
            actionBar.setHomeActionContentDescription(selecting ? "Clear selection" : null);
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!adapter.getSelected().isEmpty()) {
            getMenuInflater().inflate(R.menu.menu_hashtag_selection, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem rename = menu.findItem(R.id.action_rename_hashtag);
        if (rename != null) rename.setVisible(adapter.getSelected().size() == 1);
        return super.onPrepareOptionsMenu(menu);
    }

    private void showRenameDialog(String oldTag) {
        EditText input = new EditText(this);
        input.setText(oldTag);
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // The tag rules while typing: only letters, digits and _ get in.
        input.setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            String kept = Hashtag.normalize(source.subSequence(start, end).toString());
            return kept.length() == end - start ? null : kept;
        }});

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int marginHorizontal = (int) (20 * getResources().getDisplayMetrics().density);
        params.leftMargin = marginHorizontal;
        params.rightMargin = marginHorizontal;
        input.setLayoutParams(params);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rename " + Hashtag.display(oldTag))
                .setView(container)
                .setPositiveButton("Rename", (dialogInterface, which) ->
                        rename(oldTag, Hashtag.normalize(input.getText().toString())))
                .setNegativeButton("Cancel", null)
                .create();

        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );

        dialog.show();

        input.requestFocus();
        input.selectAll();
    }

    /** Renames, or asks first when {@code newTag} already exists and the two would merge. */
    private void rename(String oldTag, String newTag) {
        if (newTag.isEmpty() || newTag.equals(oldTag)) return;
        String target = hashtags.mergeTarget(oldTag, newTag);
        if (target == null) {
            hashtags.rename(oldTag, newTag);
            adapter.clearSelection();
            return;
        }
        Integer count = hashtags.quoteCounts().get(oldTag);
        int quotes = count != null ? count : 0;
        new AlertDialog.Builder(this)
                .setTitle("Merge hashtags")
                .setMessage("Merge " + Hashtag.display(oldTag) + " into " + Hashtag.display(target)
                        + " (" + quotes + (quotes == 1 ? " quote" : " quotes") + ")?")
                .setPositiveButton("Merge", (dialog, which) -> {
                    hashtags.rename(oldTag, target);
                    adapter.clearSelection();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteDialog(Set<String> tags) {
        List<String> names = new ArrayList<>();
        for (String tag : tags) names.add(Hashtag.display(tag));
        int quotes = hashtags.quotesWithAny(tags);

        new AlertDialog.Builder(this)
                .setTitle(tags.size() == 1 ? "Delete hashtag" : "Delete hashtags")
                .setMessage("Remove " + String.join(", ", names) + " from " + quotes
                        + (quotes == 1 ? " quote" : " quotes") + "?\n\nThe quotes are kept.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    for (String tag : tags) hashtags.delete(tag);
                    adapter.clearSelection();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        Set<String> selected = adapter.getSelected();
        if (id == android.R.id.home) {
            if (selected.isEmpty()) finish();
            else adapter.clearSelection();
            return true;
        } else if (id == R.id.action_rename_hashtag && selected.size() == 1) {
            showRenameDialog(selected.iterator().next());
            return true;
        } else if (id == R.id.action_delete_hashtags && !selected.isEmpty()) {
            showDeleteDialog(selected);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
