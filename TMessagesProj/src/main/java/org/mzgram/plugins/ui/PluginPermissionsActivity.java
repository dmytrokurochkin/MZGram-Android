package org.mzgram.plugins.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mzgram.plugins.Plugin;
import org.mzgram.plugins.PluginCapabilityScan;
import org.mzgram.plugins.PluginPermissions;
import org.mzgram.plugins.PluginTrustLevel;
import org.mzgram.plugins.PluginsController;

public class PluginPermissionsActivity extends BaseFragment {

    private static final int ID_PERM_BASE = 100;
    private static final int ID_ACTIVITY_LOG = 1;
    private static final int ID_OBFUSCATION = 2;
    private static final int ID_UNSAFE = 3;

    private final String pluginId;

    private UniversalRecyclerView listView;
    private android.view.View obfuscationView;
    private android.view.View unsafeView;
    private final java.util.Set<String> expanded = new java.util.HashSet<>();
    private final java.util.HashMap<String, PluginPermissionCell> cells = new java.util.HashMap<>();
    private SlideChooseView slider;
    private final List<String> switchable = new ArrayList<>();

    public PluginPermissionsActivity(String pluginId) {
        this.pluginId = pluginId;
    }


    public static CharSequence titleOf(String perm) {
        if (perm == null) {
            return "";
        }
        switch (perm) {
            case PluginPermissions.UI: return getString(R.string.PluginPermUi);
            case PluginPermissions.MESSAGES_READ: return getString(R.string.PluginPermMessagesRead);
            case PluginPermissions.MESSAGES_SEND: return getString(R.string.PluginPermMessagesSend);
            case PluginPermissions.NETWORK: return getString(R.string.PluginPermNetwork);
            case PluginPermissions.FILES: return getString(R.string.PluginPermFiles);
            case PluginPermissions.INTENTS: return getString(R.string.PluginPermIntents);
            case PluginPermissions.SETTINGS: return getString(R.string.PluginPermSettings);
            case PluginPermissions.HOOKS: return getString(R.string.PluginPermHooks);
            case PluginPermissions.NATIVE: return getString(R.string.PluginPermNative);
            default: return PluginPermissions.describe(perm);
        }
    }

    public static CharSequence shortTitleOf(String perm) {
        if (perm == null) {
            return "";
        }
        switch (perm) {
            case PluginPermissions.MESSAGES_READ: return getString(R.string.PluginPermShortMessagesRead);
            case PluginPermissions.MESSAGES_SEND: return getString(R.string.PluginPermShortMessagesSend);
            case PluginPermissions.NETWORK: return getString(R.string.PluginPermShortNetwork);
            case PluginPermissions.FILES: return getString(R.string.PluginPermShortFiles);
            case PluginPermissions.INTENTS: return getString(R.string.PluginPermShortIntents);
            case PluginPermissions.SETTINGS: return getString(R.string.PluginPermShortSettings);
            case PluginPermissions.HOOKS: return getString(R.string.PluginPermShortHooks);
            case PluginPermissions.NATIVE: return getString(R.string.PluginPermShortNative);
            default: return titleOf(perm);
        }
    }

    public static CharSequence infoOf(String perm) {
        if (perm == null) {
            return "";
        }
        switch (perm) {
            case PluginPermissions.UI: return getString(R.string.PluginPermUiInfo);
            case PluginPermissions.MESSAGES_READ: return getString(R.string.PluginPermMessagesReadInfo);
            case PluginPermissions.MESSAGES_SEND: return getString(R.string.PluginPermMessagesSendInfo);
            case PluginPermissions.NETWORK: return getString(R.string.PluginPermNetworkInfo);
            case PluginPermissions.FILES: return getString(R.string.PluginPermFilesInfo);
            case PluginPermissions.INTENTS: return getString(R.string.PluginPermIntentsInfo);
            case PluginPermissions.SETTINGS: return getString(R.string.PluginPermSettingsInfo);
            case PluginPermissions.HOOKS: return getString(R.string.PluginPermHooksInfo);
            case PluginPermissions.NATIVE: return getString(R.string.PluginPermNativeInfo);
            default: return "";
        }
    }

    public static boolean isEnforced(String perm) {
        return !PluginPermissions.SETTINGS.equals(perm);
    }

    public static List<String> requestedFor(Plugin plugin) {
        if (plugin == null) {
            return new ArrayList<>();
        }
        final Map<String, List<String>> scan = PluginCapabilityScan.load(plugin.id);
        List<String> requested = plugin.permissionsDeclared && !PluginCapabilityScan.isObfuscated(scan)
                ? PluginPermissions.getRequested(plugin)
                : new ArrayList<>(PluginPermissions.REQUESTABLE);
        for (String perm : PluginCapabilityScan.ordered(scan)) {
            if (!requested.contains(perm)) {
                requested.add(perm);
            }
        }
        return PluginPermissions.sanitize(requested);
    }


    @Override
    public View createView(Context context) {
        cells.clear();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.PluginPermissions));
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        if (plugin != null) {
            actionBar.setSubtitle(plugin.getDisplayName());
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        fragmentView = contentView;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        switchable.clear();
        Plugin plugin = PluginsController.getInstance().getPlugin(pluginId);
        if (plugin == null) {
            items.add(UItem.asShadow(getString(R.string.PluginPermissionsGone)));
            return;
        }
        PluginCapabilityScan.ensureScanned(plugin, () -> {
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
        boolean legacy = !plugin.permissionsDeclared;
        List<String> requested = requestedFor(plugin);
        int level = PluginTrustLevel.getLevel(pluginId);

        items.add(UItem.asHeader(getString(R.string.PluginLevelHeader)));
        items.add(UItem.asCustom(levelSlider(level)));
        items.add(UItem.asShadow(getString(levelFooter(level))));

        items.add(UItem.asHeader(getString(R.string.PluginPermissionsHeader)));
        if (requested.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.PluginPermissionsNothing)));
        }

        List<String> unenforced = new ArrayList<>();
        boolean hooks = false;
        final Map<String, List<String>> capabilities = PluginCapabilityScan.load(pluginId);
        if (PluginCapabilityScan.isObfuscated(capabilities)) {
            if (obfuscationView == null) {
                android.widget.FrameLayout frame = new android.widget.FrameLayout(getContext());
                frame.addView(PluginInstallBottomSheet.createObfuscationWarning(getContext(),
                                PluginCapabilityScan.obfuscationEvidence(capabilities)),
                        org.telegram.ui.Components.LayoutHelper.createFrame(
                                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                                android.view.Gravity.TOP, 16, 4, 16, 12));
                obfuscationView = frame;
            }
            items.add(UItem.asCustom(ID_OBFUSCATION, obfuscationView));
        }
        if (PluginPermissions.isUnsafeMode()) {
            if (unsafeView == null) {
                android.widget.FrameLayout frame = new android.widget.FrameLayout(getContext());
                frame.addView(PluginInstallBottomSheet.createWarningBox(getContext(),
                                getString(R.string.PluginsUnsafeMode),
                                getString(R.string.PluginsUnsafeModeActive), null),
                        org.telegram.ui.Components.LayoutHelper.createFrame(
                                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                                android.view.Gravity.TOP, 16, 4, 16, 12));
                unsafeView = frame;
            }
            items.add(UItem.asCustom(ID_UNSAFE, unsafeView));
        }
        List<String> enforced = new ArrayList<>();
        for (String perm : requested) {
            if (!isEnforced(perm)) {
                unenforced.add(perm);
                continue;
            }
            enforced.add(perm);
        }
        for (int i = 0; i < enforced.size(); i++) {
            final String perm = enforced.get(i);
            hooks |= PluginPermissions.isDangerous(perm);
            final boolean allowed = PluginTrustLevel.allows(level, perm);
            PluginPermissionCell cell = cells.get(perm);
            if (cell == null) {
                cell = new PluginPermissionCell(getContext(), PluginPermissionCell.TYPE_SWITCH);
                cells.put(perm, cell);
            }
            final PluginPermissionCell row = cell;
            cell.set(perm, titleOf(perm), infoOf(perm),
                    PluginCapabilityScan.evidenceOf(capabilities, perm),
                    i < enforced.size() - 1);
            cell.setChecked(allowed && PluginPermissions.has(pluginId, perm), false);
            cell.setEnabledState(allowed);
            cell.setExpanded(expanded.contains(perm), false);
            cell.setOnExpandChanged(() -> {
                if (row.isExpanded()) {
                    expanded.add(perm);
                } else {
                    expanded.remove(perm);
                }
            });
            cell.setOnToggle(!allowed ? null : () -> {
                togglePermission(perm);
                row.setChecked(PluginPermissions.has(pluginId, perm), true);
            });
            items.add(UItem.asCustom(ID_PERM_BASE + switchable.size(), cell));
            switchable.add(perm);
        }

        CharSequence note = null;
        if (hooks) {
            note = getString(R.string.PluginPermissionsHooksWarning);
        } else if (legacy) {
            note = getString(R.string.PluginPermissionsLegacyInfo);
        } else if (!unenforced.isEmpty()) {
            List<CharSequence> titles = new ArrayList<>();
            for (String perm : unenforced) {
                titles.add(titleOf(perm));
            }
            note = LocaleController.formatString(
                    R.string.PluginPermissionsUnenforcedInfo, TextUtils.join(", ", titles));
        }
        items.add(UItem.asShadow(note));

        items.add(UItem.asHeader(getString(R.string.PluginActivityHeader)));
        items.add(UItem.asButton(ID_ACTIVITY_LOG, R.drawable.msg_log,
                getString(R.string.PluginActivityLog)));
        items.add(UItem.asShadow(getString(R.string.PluginActivityLogInfo)));
    }

    private View levelSlider(int level) {
        if (slider == null) {
            slider = new SlideChooseView(getContext(), getResourceProvider()) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP
                            && event.getY() < AndroidUtilities.dp(34)) {
                        showLevelInfo(labelIndexAt(this, event.getX()), this);
                        return true;
                    }
                    return super.onTouchEvent(event);
                }
            };
            slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            slider.setCallback(index -> {
                if (index == PluginTrustLevel.getLevel(pluginId)) {
                    return;
                }
                PluginTrustLevel.setLevel(pluginId, index);
                applyNow();
                if (listView != null) {
                    listView.adapter.update(true);
                }
            });
        }
        slider.setOptions(level,
                getString(R.string.PluginLevelIsolated),
                getString(R.string.PluginLevelGated),
                getString(R.string.PluginLevelTrusted));
        return slider;
    }

    private static int labelIndexAt(View view, float x) {
        int side = AndroidUtilities.dp(22);
        int span = Math.max(1, view.getMeasuredWidth() - side * 2);
        int index = Math.round((x - side) / (span / 2f));
        return Math.max(PluginTrustLevel.ISOLATED, Math.min(PluginTrustLevel.TRUSTED, index));
    }

    private void showLevelInfo(int level, View anchor) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        int start = text.length();
        text.append(getString(levelTitle(level)));
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append("\n").append(getString(levelInfo(level)));

        ItemOptions options = ItemOptions.makeOptions(this, anchor)
                .addText(text, 13);
        if (level != PluginTrustLevel.getLevel(pluginId)) {
            options.add(R.drawable.msg_select, getString(R.string.PluginLevelApply), () -> {
                PluginTrustLevel.setLevel(pluginId, level);
                applyNow();
                if (listView != null) {
                    listView.adapter.update(true);
                }
            });
        }
        options.setGravity(Gravity.LEFT).show();
    }

    private static int levelTitle(int level) {
        switch (level) {
            case PluginTrustLevel.ISOLATED: return R.string.PluginLevelIsolated;
            case PluginTrustLevel.TRUSTED: return R.string.PluginLevelTrusted;
            default: return R.string.PluginLevelGated;
        }
    }

    private static int levelInfo(int level) {
        switch (level) {
            case PluginTrustLevel.ISOLATED: return R.string.PluginLevelIsolatedInfo;
            case PluginTrustLevel.TRUSTED: return R.string.PluginLevelTrustedInfo;
            default: return R.string.PluginLevelGatedInfo;
        }
    }

    private int levelFooter(int level) {
        switch (level) {
            case PluginTrustLevel.ISOLATED:
                return R.string.PluginLevelIsolatedFooter;
            case PluginTrustLevel.TRUSTED:
                return PluginPermissions.has(pluginId, PluginPermissions.HOOKS)
                        || PluginPermissions.has(pluginId, PluginPermissions.NATIVE)
                        ? R.string.PluginLevelTrustedFooter
                        : R.string.PluginLevelTrustedFooterIdle;
            default:
                return R.string.PluginLevelGatedFooter;
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ACTIVITY_LOG) {
            presentFragment(new PluginActivityLogActivity(pluginId));
            return;
        }
        int index = item.id - ID_PERM_BASE;
        if (index < 0 || index >= switchable.size()) {
            return;
        }
        togglePermission(switchable.get(index));
    }

    private void togglePermission(String perm) {
        if (!PluginTrustLevel.allows(pluginId, perm)) {
            return;
        }
        if (PluginPermissions.has(pluginId, perm)) {
            PluginPermissions.revoke(pluginId, perm);
        } else {
            PluginPermissions.grant(pluginId, perm);
        }
        if (PluginPermissions.isDangerous(perm) && listView != null) {
            listView.adapter.update(true);
        }
        applyNow();
    }

    private void applyNow() {
        PluginsController controller = PluginsController.getInstance();
        Plugin plugin = controller.getPlugin(pluginId);
        if (plugin == null) {
            return;
        }
        if (!plugin.enabled) {
            if (getContext() != null) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info,
                                getString(R.string.PluginPermissionsEnableFirst))
                        .show();
            }
            return;
        }
        controller.reloadPlugin(pluginId, () -> {
            if (getContext() != null) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, getString(R.string.PluginPermissionsApplied))
                        .show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(false);
        }
    }
}
