package com.example.myquotes;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ScrollView;

import androidx.appcompat.widget.AppCompatAutoCompleteTextView;

/**
 * An AutoCompleteTextView whose dropdown always opens below its anchor. Android puts the dropdown
 * wherever there is more room, so with the keyboard up a long list opens above the field and jumps
 * below once it gets short. Before each show this caps the list's height to the space between the
 * anchor and the bottom of the enclosing ScrollView (the top of the keyboard, since the screen pads
 * for it), so it always fits below; a list that fits keeps its natural height.
 */
public class DropDownBelowTextView extends AppCompatAutoCompleteTextView {
    private final Rect padding = new Rect();
    private FrameLayout measureParent;

    public DropDownBelowTextView(Context context) {
        super(context);
    }

    public DropDownBelowTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DropDownBelowTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void showDropDown() {
        fitBelowAnchor();
        super.showDropDown();
    }

    private void fitBelowAnchor() {
        View anchor = getDropDownAnchor() == View.NO_ID ? this : getRootView().findViewById(getDropDownAnchor());
        ScrollView scroll = enclosingScrollView();
        ListAdapter adapter = getAdapter();
        if (anchor == null || scroll == null || adapter == null) {
            setDropDownHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            return;
        }
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int anchorBottom = location[1] + anchor.getHeight() + getDropDownVerticalOffset();
        scroll.getLocationOnScreen(location);
        int below = location[1] + scroll.getHeight() - scroll.getPaddingBottom() - anchorBottom;

        int content = listHeight(adapter, anchor.getWidth());
        // A little slack for list dividers, which aren't known before the list exists.
        int slack = Math.round(8 * getResources().getDisplayMetrics().density);
        setDropDownHeight(content + slack <= below ? ViewGroup.LayoutParams.WRAP_CONTENT
                : Math.max(below - padding.top - padding.bottom, 1));
    }

    /** The list's natural height including the popup background's padding (left in {@link #padding}). */
    private int listHeight(ListAdapter adapter, int width) {
        Drawable background = getDropDownBackground();
        padding.setEmpty();
        if (background != null) background.getPadding(padding);
        if (measureParent == null) measureParent = new FrameLayout(getContext());
        int widthSpec = MeasureSpec.makeMeasureSpec(Math.max(width - padding.left - padding.right, 0),
                MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int height = padding.top + padding.bottom;
        for (int i = 0; i < adapter.getCount(); i++) {
            View row = adapter.getView(i, null, measureParent);
            row.measure(widthSpec, heightSpec);
            height += row.getMeasuredHeight();
        }
        return height;
    }

    private ScrollView enclosingScrollView() {
        for (ViewParent p = getParent(); p != null; p = p.getParent()) {
            if (p instanceof ScrollView) return (ScrollView) p;
        }
        return null;
    }
}
