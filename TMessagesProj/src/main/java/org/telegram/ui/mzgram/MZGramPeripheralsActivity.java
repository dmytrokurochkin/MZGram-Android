/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Extended peripherals settings screen (Point 13 of OWNER-DECISIONS.md).
 * Manages Tor companion, badges, Nothing Glyph, custom emoji fonts,
 * online synced lyrics, and icon packs catalog with license verification.
 */

package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.messenger.mzgram.peripherals.MZGramBadges;
import org.telegram.messenger.mzgram.peripherals.MZGramEmojiFonts;
import org.telegram.messenger.mzgram.peripherals.MZGramIconPacksCatalog;
import org.telegram.messenger.mzgram.peripherals.MZGramNothingGlyph;
import org.telegram.messenger.mzgram.peripherals.MZGramTorCompanion;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

public class MZGramPeripheralsActivity extends UniversalFragment {

    private static final int BUTTON_TOR_ENABLED = 1;
    private static final int BUTTON_BADGES_ENABLED = 10;
    private static final int BUTTON_SELF_BADGE = 11;
    private static final int BUTTON_GLYPH_ENABLED = 20;
    private static final int BUTTON_GLYPH_KEY = 21;
    private static final int BUTTON_GLYPH_BRIGHTNESS = 22;
    private static final int BUTTON_EMOJI_FONT_ENABLED = 30;
    private static final int BUTTON_EMOJI_FONT_FILE = 31;
    private static final int BUTTON_LYRICS_ONLINE = 40;
    private static final int BUTTON_ICON_CATALOG_ENABLED = 50;
    private static final int BUTTON_ICON_CATALOG_VIEW = 51;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.MZGramPeripheralsTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        // Section: Tor Companion
        items.add(UItem.asHeader(getString(R.string.MZGramTorHeader)));
        items.add(UItem.asCheck(BUTTON_TOR_ENABLED, getString(R.string.MZGramTorCompanion)).setChecked(MZGramConfig.torCompanionEnabled));
        items.add(UItem.asShadow(getString(R.string.MZGramTorInfo)));

        // Section: Custom Badges
        items.add(UItem.asHeader(getString(R.string.MZGramBadgesHeader)));
        items.add(UItem.asCheck(BUTTON_BADGES_ENABLED, getString(R.string.MZGramCustomBadges)).setChecked(MZGramConfig.customBadgesEnabled));
        if (MZGramConfig.customBadgesEnabled) {
            long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            MZGramBadges.Badge b = MZGramBadges.getBadge(selfId);
            String badgeName = b != null ? b.text : getString(R.string.MZGramBadgeNone);
            items.add(UItem.asButton(BUTTON_SELF_BADGE, getString(R.string.MZGramSelfBadge), badgeName));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramBadgesInfo)));

        // Section: Nothing Glyph
        items.add(UItem.asHeader(getString(R.string.MZGramGlyphHeader)));
        items.add(UItem.asCheck(BUTTON_GLYPH_ENABLED, getString(R.string.MZGramNothingGlyph)).setChecked(MZGramConfig.nothingGlyphEnabled));
        if (MZGramConfig.nothingGlyphEnabled) {
            String keyMasked = TextUtils.isEmpty(MZGramConfig.nothingGlyphApiKey) ? "" : "••••••••";
            items.add(UItem.asButton(BUTTON_GLYPH_KEY, getString(R.string.MZGramNothingGlyphApiKey), keyMasked));
            String brightStr = MZGramConfig.nothingGlyphBrightness + "%";
            items.add(UItem.asButton(BUTTON_GLYPH_BRIGHTNESS, getString(R.string.MZGramNothingGlyphBrightness), brightStr));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramGlyphInfo)));

        // Section: Custom Emoji Fonts
        items.add(UItem.asHeader(getString(R.string.MZGramEmojiFontHeader)));
        items.add(UItem.asCheck(BUTTON_EMOJI_FONT_ENABLED, getString(R.string.MZGramCustomEmojiFont)).setChecked(MZGramConfig.customEmojiFontEnabled));
        if (MZGramConfig.customEmojiFontEnabled) {
            boolean hasFont = MZGramEmojiFonts.hasCustomEmojiFont();
            String fontStatus = hasFont ? getString(R.string.MZGramEmojiFontInstalled) : getString(R.string.MZGramEmojiFontNone);
            items.add(UItem.asButton(BUTTON_EMOJI_FONT_FILE, getString(R.string.MZGramPickEmojiFont), fontStatus));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramEmojiFontInfo)));

        // Section: Synced Lyrics
        items.add(UItem.asHeader(getString(R.string.MZGramLyricsHeader)));
        items.add(UItem.asCheck(BUTTON_LYRICS_ONLINE, getString(R.string.MZGramSyncedLyricsOnline)).setChecked(MZGramConfig.syncedLyricsOnline));
        items.add(UItem.asShadow(getString(R.string.MZGramSyncedLyricsOnlineInfo)));

        // Section: Icon Packs Catalog
        items.add(UItem.asHeader(getString(R.string.MZGramIconCatalogHeader)));
        items.add(UItem.asCheck(BUTTON_ICON_CATALOG_ENABLED, getString(R.string.MZGramIconPacksCatalog)).setChecked(MZGramConfig.iconPacksCatalogEnabled));
        if (MZGramConfig.iconPacksCatalogEnabled) {
            String countStr = String.valueOf(MZGramIconPacksCatalog.getApprovedCount());
            items.add(UItem.asButton(BUTTON_ICON_CATALOG_VIEW, getString(R.string.MZGramViewIconPacksCatalog), countStr));
        }
        items.add(UItem.asShadow(getString(R.string.MZGramIconCatalogInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }

        if (item.id == BUTTON_TOR_ENABLED) {
            if (!MZGramConfig.torCompanionEnabled && !MZGramTorCompanion.isCompanionInstalled()) {
                showTorInstallDialog(activity);
            } else {
                MZGramConfig.toggleTorCompanionEnabled();
                MZGramTorCompanion.applyTorProxySettings(MZGramConfig.torCompanionEnabled);
                updateList();
            }
        } else if (item.id == BUTTON_BADGES_ENABLED) {
            MZGramConfig.toggleCustomBadgesEnabled();
            updateList();
        } else if (item.id == BUTTON_SELF_BADGE) {
            showSelfBadgeDialog(activity);
        } else if (item.id == BUTTON_GLYPH_ENABLED) {
            MZGramConfig.toggleNothingGlyphEnabled();
            updateList();
        } else if (item.id == BUTTON_GLYPH_KEY) {
            showGlyphKeyDialog(activity);
        } else if (item.id == BUTTON_GLYPH_BRIGHTNESS) {
            showGlyphBrightnessDialog(activity);
        } else if (item.id == BUTTON_EMOJI_FONT_ENABLED) {
            MZGramConfig.toggleCustomEmojiFontEnabled();
            updateList();
        } else if (item.id == BUTTON_EMOJI_FONT_FILE) {
            showEmojiFontOptions(activity);
        } else if (item.id == BUTTON_LYRICS_ONLINE) {
            MZGramConfig.toggleSyncedLyricsOnline();
            updateList();
        } else if (item.id == BUTTON_ICON_CATALOG_ENABLED) {
            MZGramConfig.toggleIconPacksCatalogEnabled();
            updateList();
        } else if (item.id == BUTTON_ICON_CATALOG_VIEW) {
            showIconCatalogDialog(activity);
        }
    }

    private void updateList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void showTorInstallDialog(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramTorCompanion))
                .setMessage(getString(R.string.MZGramTorInstallPrompt))
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    MZGramConfig.toggleTorCompanionEnabled();
                    MZGramTorCompanion.applyTorProxySettings(MZGramConfig.torCompanionEnabled);
                    updateList();
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showSelfBadgeDialog(Activity activity) {
        CharSequence[] badges = new CharSequence[]{
                MZGramBadges.BADGE_SUPPORTER,
                MZGramBadges.BADGE_VERIFIED,
                MZGramBadges.BADGE_DEVELOPER,
                MZGramBadges.BADGE_BETA,
                MZGramBadges.BADGE_DONOR,
                getString(R.string.MZGramBadgeNone)
        };
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramSelfBadge))
                .setItems(badges, (dialog, which) -> {
                    long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                    if (which == badges.length - 1) {
                        MZGramBadges.setBadge(selfId, null, 0);
                    } else {
                        MZGramBadges.setBadge(selfId, badges[which].toString(), 0xFF4CAF50);
                    }
                    updateList();
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showGlyphKeyDialog(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        EditTextBoldCursor editText = new EditTextBoldCursor(activity);
        editText.setTextSize(16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!TextUtils.isEmpty(MZGramConfig.nothingGlyphApiKey)) {
            editText.setText(MZGramConfig.nothingGlyphApiKey);
        }
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 8, 24, 8));

        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramNothingGlyphApiKey))
                .setMessage(getString(R.string.MZGramNothingGlyphKeyPrompt))
                .setView(container)
                .setPositiveButton(getString(R.string.MZGramOk), (dialog, which) -> {
                    MZGramConfig.setNothingGlyphApiKey(editText.getText().toString());
                    updateList();
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showGlyphBrightnessDialog(Activity activity) {
        CharSequence[] options = new CharSequence[]{"25%", "50%", "75%", "100%"};
        int[] vals = new int[]{25, 50, 75, 100};
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramNothingGlyphBrightness))
                .setItems(options, (dialog, which) -> {
                    MZGramConfig.setNothingGlyphBrightness(vals[which]);
                    updateList();
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showEmojiFontOptions(Activity activity) {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.MZGramEmojiFontReset)
        };
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramCustomEmojiFont))
                .setItems(options, (dialog, which) -> {
                    MZGramEmojiFonts.removeCustomEmojiFont();
                    updateList();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.MZGramEmojiFontResetSuccess)).show();
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    private void showIconCatalogDialog(Activity activity) {
        List<MZGramIconPacksCatalog.CatalogItem> packs = MZGramIconPacksCatalog.getApprovedPacks();
        CharSequence[] names = new CharSequence[packs.size()];
        for (int i = 0; i < packs.size(); i++) {
            MZGramIconPacksCatalog.CatalogItem item = packs.get(i);
            // Telegram's own text: pack name and license display
            names[i] = item.name + " (" + item.license + ", " + item.iconsCount + ")";
        }
        new AlertDialog.Builder(activity)
                .setTitle(getString(R.string.MZGramIconCatalogApprovedTitle))
                .setItems(names, (dialog, which) -> {
                    MZGramIconPacksCatalog.CatalogItem selected = packs.get(which);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.info, selected.name + " (" + selected.license + ")").show();
                })
                .setNegativeButton(getString(R.string.MZGramCancel), null)
                .show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
