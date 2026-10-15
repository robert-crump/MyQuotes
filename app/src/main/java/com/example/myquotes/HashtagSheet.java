package com.example.myquotes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/**
 * One tag's bottom sheet on the Hashtags screen (#51), opened by a tap: Show N quotes (the
 * search with the tag filter), Rename, Delete, and what the suggester learned about the tag (#52:
 * every word and author that gets it suggested, with counts; read-only). Rename and delete are the
 * Hashtags screen's own dialogs ({@link Host}); the sheet closes before they open. The model is
 * fitted on the whole collection when the sheet opens.
 */
public class HashtagSheet extends BottomSheetDialogFragment {
    static final String TAG = "HashtagSheet";
    private static final String ARG_TAG = "hashtag";

    /** The Hashtags screen: rename and delete with its confirmation dialogs. */
    interface Host {
        void renameHashtag(String tag);
        void deleteHashtag(String tag);
    }

    static HashtagSheet newInstance(String tag) {
        HashtagSheet sheet = new HashtagSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TAG, tag);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_hashtag, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String tag = requireArguments().getString(ARG_TAG);
        List<Quote> quotes = MyApplication.getInstance().getQuoteCollection().getCurrentList();
        Integer count = Hashtags.counts(quotes).get(tag);
        if (count == null) { // renamed or deleted while the sheet was away (recreation)
            dismiss();
            return;
        }

        ((TextView) view.findViewById(R.id.sheet_hashtag_name)).setText(Hashtag.display(tag));
        TextView show = view.findViewById(R.id.sheet_show_quotes);
        show.setText("Show " + HashtagSuggestionText.quotes(count));
        show.setOnClickListener(v -> {
            dismiss();
            startActivity(QuoteQuery.forTag(tag).toIntent(requireContext()));
        });
        view.findViewById(R.id.sheet_rename).setOnClickListener(v -> {
            dismiss();
            ((Host) requireActivity()).renameHashtag(tag);
        });
        view.findViewById(R.id.sheet_delete).setOnClickListener(v -> {
            dismiss();
            ((Host) requireActivity()).deleteHashtag(tag);
        });

        HashtagSuggester.TagProfile profile = HashtagSuggester.fit(quotes).profile(tag);
        LinearLayout suggestions = view.findViewById(R.id.sheet_suggestions);
        for (String text : HashtagSuggestionText.suggestions(profile)) suggestions.addView(paragraph(text));
    }

    private TextView paragraph(String text) {
        TextView paragraph = new TextView(requireContext());
        paragraph.setText(text);
        paragraph.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        int gap = Math.round(4 * getResources().getDisplayMetrics().density);
        paragraph.setPadding(0, gap, 0, gap);
        return paragraph;
    }
}
