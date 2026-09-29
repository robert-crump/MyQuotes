package com.example.myquotes;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.myquotes.databinding.ItemSettingsRowBinding;

/**
 * One Settings row ({@code item_settings_row}): icon, title, optional summary, and either a tap
 * action or a switch that a tap anywhere on the row toggles.
 */
final class SettingsRow {
    private final ItemSettingsRowBinding binding;
    private CompoundButton.OnCheckedChangeListener switchListener;

    SettingsRow(ItemSettingsRowBinding binding, @DrawableRes int icon, @StringRes int title) {
        this.binding = binding;
        binding.rowIcon.setImageResource(icon);
        binding.rowTitle.setText(title);
    }

    SettingsRow onClick(View.OnClickListener action) {
        binding.getRoot().setOnClickListener(action);
        return this;
    }

    /** Turns the row into a switch row; {@code listener} hears user toggles only. */
    SettingsRow withSwitch(boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        binding.rowSwitch.setVisibility(View.VISIBLE);
        binding.rowSwitch.setChecked(checked);
        switchListener = listener;
        binding.rowSwitch.setOnCheckedChangeListener(listener);
        binding.getRoot().setOnClickListener(v -> binding.rowSwitch.toggle());
        // The inert switch is hidden from TalkBack; the row announces itself as the switch.
        binding.getRoot().setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Switch.class.getName());
                info.setCheckable(true);
                info.setChecked(binding.rowSwitch.isChecked());
            }
        });
        return this;
    }

    /** Reflects a state without firing the switch listener (and its side effects). */
    void setCheckedSilently(boolean checked) {
        binding.rowSwitch.setOnCheckedChangeListener(null);
        binding.rowSwitch.setChecked(checked);
        binding.rowSwitch.setOnCheckedChangeListener(switchListener);
    }

    void setSummary(CharSequence summary) {
        binding.rowSubtitle.setText(summary);
        binding.rowSubtitle.setVisibility(summary.length() == 0 ? View.GONE : View.VISIBLE);
    }

    void setSummary(@StringRes int summary) {
        setSummary(binding.getRoot().getContext().getText(summary));
    }
}
