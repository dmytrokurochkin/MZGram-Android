/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The code of an added plugin, read-only: the .plugin file as text in a
 * fixed-width font, coloured as Python by Telegram's own code highlighting
 * (the one of code blocks in messages), scrolling both ways. The menu
 * copies the whole code. Reading the code never runs it. It also shows
 * a file that is not added yet, from the prompt before adding.
 *
 * Based on exteraless plugins/ui/components/PluginFileViewer.java (GPL-3.0):
 * the idea of a built-in viewer of the plugin's code; written anew for
 * MZGram as a plain screen instead of an Instant View page.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mzgram.plugins.MZGramPluginFormat;
import org.telegram.messenger.mzgram.plugins.MZGramPlugins;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.utils.code.highlight.CodeHighlighting;

import java.io.File;

public class MZGramPluginSourceActivity extends BaseFragment {

    private static final int MENU_COPY = 1;
    // Longer code is shown without colours: colouring it would take too long
    private static final int HIGHLIGHT_LIMIT = 256 * 1024;
    private static final String LANGUAGE = "python";

    private final String pluginId;
    // A file not added yet, or null for the added plugin
    private final File file;
    private final Runnable onClose;
    private TextView textView;
    private String source;

    public MZGramPluginSourceActivity(String pluginId) {
        this(pluginId, null, null);
    }

    /** The code of a file not added yet; onClose runs when the screen is closed. */
    public MZGramPluginSourceActivity(String pluginId, File file, Runnable onClose) {
        this.pluginId = pluginId;
        this.file = file;
        this.onClose = onClose;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (onClose != null) {
            AndroidUtilities.runOnUIThread(onClose);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(pluginId + MZGramPluginFormat.EXTENSION);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_COPY) {
                    copy();
                }
            }
        });
        final ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_COPY, R.drawable.msg_copy).setContentDescription(getString(R.string.MZGramCopy));

        final FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        textView = new TextView(context);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setPadding(dp(14), dp(12), dp(14), dp(12));
        textView.setTextIsSelectable(true);
        textView.setText(getString(R.string.MZGramLoading));

        // Lines keep their length: the code scrolls sideways instead of wrapping
        final HorizontalScrollView sideways = new HorizontalScrollView(context);
        sideways.addView(textView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(sideways, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fragmentView = frame;
        load();
        return fragmentView;
    }

    private void load() {
        Utilities.globalQueue.postRunnable(() -> {
            final String text = file != null ? MZGramPlugins.readSource(file) : MZGramPlugins.readSource(pluginId);
            AndroidUtilities.runOnUIThread(() -> show(text));
        });
    }

    private void show(String text) {
        if (textView == null || isFinishing()) {
            return;
        }
        source = text;
        if (text == null) {
            textView.setText(getString(R.string.MZGramPluginFileFailed));
            return;
        }
        textView.setText(text);
        if (text.length() <= HIGHLIGHT_LIMIT) {
            CodeHighlighting.highlightEditable(text, LANGUAGE, highlighted -> {
                if (textView != null && !isFinishing() && text.equals(source)) {
                    textView.setText(highlighted);
                }
            });
        }
    }

    private void copy() {
        if (source == null) {
            return;
        }
        // Very long code may not fit the clipboard; addToClipboard says so
        if (AndroidUtilities.addToClipboard(source)) {
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.MZGramTextCopied)).show();
        } else {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.MZGramPluginCopyFailed)).show();
        }
    }
}
