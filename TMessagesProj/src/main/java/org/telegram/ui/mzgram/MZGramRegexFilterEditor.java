/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The editor of one message filter rule (MZGramRegexFilters): the
 * expression, "ignore letter case", "hide when NOT found" and "rule is on",
 * with a field to try the rule on any text before it is saved. An
 * expression that is broken or can take very long is not saved; the editor
 * says why. Opened from the rules screen and from the message menu, where
 * it also offers to keep the rule for this chat only.
 *
 * Idea from Nagram / Nekogram RegexFilterEditActivity (GPL-3.0); written
 * anew for MZGram.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.MZGramRegexFilters;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public final class MZGramRegexFilterEditor {

    private static final int PREFILL = 60;

    private MZGramRegexFilterEditor() {
    }

    /** Text for what is wrong with an expression, or null when it can be saved. */
    public static String problemText(String pattern, boolean caseInsensitive) {
        switch (MZGramRegexFilters.problemOf(pattern, caseInsensitive)) {
            case MZGramRegexFilters.PROBLEM_EMPTY:
                return getString(R.string.MZGramRegexFilterEmpty);
            case MZGramRegexFilters.PROBLEM_TOO_LONG:
                return getString(R.string.MZGramRegexFilterTooLong);
            case MZGramRegexFilters.PROBLEM_SYNTAX: {
                String detail = MZGramRegexFilters.syntaxDetail(pattern, caseInsensitive);
                return TextUtils.isEmpty(detail) ? getString(R.string.MZGramRegexFilterSyntax)
                        : getString(R.string.MZGramRegexFilterSyntax) + "\n" + detail;
            }
            case MZGramRegexFilters.PROBLEM_SLOW:
                return getString(R.string.MZGramRegexFilterSlow);
            default:
                return null;
        }
    }

    /** The message menu offers "Add to filters" for other people's messages with text while the filters are on. */
    public static boolean canAddFrom(MessageObject message) {
        if (!MZGramConfig.regexFilters || message == null || message.messageOwner == null || message.isOutOwner()
                || message.isSponsored() || TextUtils.isEmpty(message.messageOwner.message)) {
            return false;
        }
        long dialogId = message.getDialogId();
        return dialogId != 0 && dialogId != UserConfig.getInstance(message.currentAccount).getClientUserId();
    }

    /** The first line of the message text, at most PREFILL characters, as an expression that finds it. */
    public static String prefillFrom(MessageObject message) {
        String text = message == null || message.messageOwner == null ? null : message.messageOwner.message;
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        text = text.trim();
        int line = text.indexOf('\n');
        if (line > 0) {
            text = text.substring(0, line).trim();
        }
        if (text.length() > PREFILL) {
            text = text.substring(0, PREFILL).trim();
        }
        return MZGramRegexFilters.quote(text);
    }

    private static EditTextBoldCursor input(Activity activity, String hint, String text, boolean multiline) {
        EditTextBoldCursor input = new EditTextBoldCursor(activity);
        input.lineYFix = true;
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText));
        input.setHintText(hint);
        input.setText(text == null ? "" : text);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) {
            input.setMinLines(2);
            input.setMaxLines(5);
            input.setGravity(Gravity.TOP | Gravity.START);
        } else {
            input.setSingleLine(true);
        }
        input.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        input.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        input.setBackground(null);
        input.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        return input;
    }

    private static CheckBoxCell check(Activity activity, String text, boolean checked) {
        CheckBoxCell cell = new CheckBoxCell(activity, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, null);
        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setText(text, "", checked, false);
        cell.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked(), true));
        return cell;
    }

    /**
     * Opens the editor.
     *
     * @param dialogId  the list the rule belongs to (MZGramRegexFilters.COMMON for the common one)
     * @param index     the rule to change, or -1 for a new rule
     * @param prefill   expression of a new rule, may be null
     * @param chatId    when not 0, a new rule offers "only in this chat" for this chat (checked)
     * @param onSaved   run after the rule is saved or deleted, may be null
     */
    public static void show(BaseFragment fragment, long dialogId, int index, String prefill, long chatId, Runnable onSaved) {
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        ArrayList<MZGramRegexFilters.Rule> list = MZGramRegexFilters.rules(dialogId);
        MZGramRegexFilters.Rule rule = index >= 0 && index < list.size() ? list.get(index) : null;
        final boolean isNew = rule == null;

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor pattern = input(activity, getString(R.string.MZGramRegexFilterPatternHint),
                isNew ? prefill : rule.pattern, true);
        pattern.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MZGramRegexFilters.MAX_PATTERN)});
        pattern.setSelection(pattern.getText().length());
        content.addView(pattern, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 4));

        TextView problem = new TextView(activity);
        problem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        problem.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        content.addView(problem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 4));

        CheckBoxCell caseCell = check(activity, getString(R.string.MZGramRegexFilterCaseInsensitive), isNew || rule.caseInsensitive);
        content.addView(caseCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        CheckBoxCell reversedCell = check(activity, getString(R.string.MZGramRegexFilterReversed), !isNew && rule.reversed);
        content.addView(reversedCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        CheckBoxCell enabledCell = check(activity, getString(R.string.MZGramRegexFilterEnabled), isNew || rule.enabled);
        content.addView(enabledCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        CheckBoxCell chatCell = null;
        if (isNew && chatId != 0) {
            chatCell = check(activity, getString(R.string.MZGramRegexFilterThisChat), true);
            content.addView(chatCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        }

        EditTextBoldCursor sample = input(activity, getString(R.string.MZGramRegexFilterSampleHint), "", true);
        content.addView(sample, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 4));
        TextView result = new TextView(activity);
        result.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        result.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        content.addView(result, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));

        Runnable refresh = () -> {
            String p = pattern.getText() == null ? "" : pattern.getText().toString();
            String why = TextUtils.isEmpty(p) ? null : problemText(p, caseCell.isChecked());
            problem.setText(why == null ? "" : why);
            problem.setVisibility(why == null ? android.view.View.GONE : android.view.View.VISIBLE);
            String s = sample.getText() == null ? "" : sample.getText().toString();
            if (TextUtils.isEmpty(p) || TextUtils.isEmpty(s) || why != null) {
                result.setText(getString(R.string.MZGramRegexFilterSampleInfo));
                result.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            } else if (MZGramRegexFilters.test(p, caseCell.isChecked(), reversedCell.isChecked(), s)) {
                result.setText(getString(R.string.MZGramRegexFilterSampleHidden));
                result.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            } else {
                result.setText(getString(R.string.MZGramRegexFilterSampleShown));
                result.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            }
        };
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        };
        pattern.addTextChangedListener(watcher);
        sample.addTextChangedListener(watcher);
        caseCell.setOnClickListener(v -> {
            caseCell.setChecked(!caseCell.isChecked(), true);
            refresh.run();
        });
        reversedCell.setOnClickListener(v -> {
            reversedCell.setChecked(!reversedCell.isChecked(), true);
            refresh.run();
        });
        refresh.run();

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);

        final CheckBoxCell onlyChat = chatCell;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(getString(isNew ? R.string.MZGramRegexFilterNew : R.string.MZGramRegexFilterEdit));
        builder.makeCustomMaxHeight();
        builder.setView(scroll);
        builder.setPositiveButton(getString(R.string.MZGramSave), (dialog, which) -> {
            String p = pattern.getText() == null ? "" : pattern.getText().toString();
            String why = problemText(p, caseCell.isChecked());
            if (why != null) {
                problem.setText(why);
                problem.setVisibility(android.view.View.VISIBLE);
                AndroidUtilities.shakeViewSpring(pattern);
                return;
            }
            MZGramRegexFilters.Rule made = new MZGramRegexFilters.Rule(p, caseCell.isChecked(), reversedCell.isChecked(), enabledCell.isChecked());
            if (isNew) {
                long target = onlyChat != null && onlyChat.isChecked() ? chatId : dialogId;
                if (!MZGramRegexFilters.addRule(target, made)) {
                    problem.setText(getString(R.string.MZGramRegexFilterFull));
                    problem.setVisibility(android.view.View.VISIBLE);
                    return;
                }
            } else {
                MZGramRegexFilters.replaceRule(dialogId, index, made);
            }
            dialog.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
        });
        builder.setNegativeButton(getString(R.string.MZGramCancel), (dialog, which) -> dialog.dismiss());
        if (!isNew) {
            builder.setNeutralButton(getString(R.string.MZGramRegexFilterDelete), (dialog, which) -> {
                MZGramRegexFilters.removeRule(dialogId, index);
                dialog.dismiss();
                if (onSaved != null) {
                    onSaved.run();
                }
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setDismissDialogByButtons(false);
        dialog.setOnDismissListener(d -> AndroidUtilities.hideKeyboard(pattern));
        dialog.setOnShowListener(d -> {
            pattern.requestFocus();
            AndroidUtilities.showKeyboard(pattern);
        });
        fragment.showDialog(dialog);
        if (!isNew) {
            android.view.View delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (delete instanceof TextView) {
                ((TextView) delete).setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        }
    }
}
