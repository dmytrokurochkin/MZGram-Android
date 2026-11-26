package org.mzgram.settings;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.LocaleController.formatString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsLayout;

import org.mzgram.appearance.AppearanceConfig;
import org.mzgram.appearance.AvatarCornersPreviewCell;
import org.mzgram.appearance.AvatarCornersSeekBar;
import org.mzgram.appearance.ChatListPreviewCell;
import org.mzgram.appearance.FabShapeCell;
import org.mzgram.appearance.FoldersPreviewCell;
import org.mzgram.icons.IconPacksActivity;
import org.mzgram.pillstack.PillStackSettingsActivity;
import org.mzgram.core.CoreConfig;
import org.mzgram.core.config.ConfigItem;
import org.mzgram.core.helpers.AppRestartHelper;
import org.mzgram.core.settings.BaseCoreSettingsActivity;
import org.mzgram.core.settings.CoreEmojiSettingsActivity;
import org.mzgram.core.ui.cells.HeaderCell;
import org.mzgram.ext.ExtConfig;

import org.mzgram.chats.StartupFolder;
import org.mzgram.core.folder.FolderIconHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Экран «Оформление» раздела mzGram — визуальный порт AppearancePreferencesActivity
 * из MZGram. Живые превью (аватарки, список чатов, папки) портированы 1:1 из
 * MZGram 10.10.1 в пакет {@link org.mzgram.appearance}.
 *
 * Настройки, у которых в MZGram уже есть аналог, привязаны к существующим ConfigItem
 * (ExtConfig / CoreConfig). Чисто визуальные настройки, которых в MZGram нет,
 * хранятся в {@link AppearanceConfig} и помечены в отчёте как «только UI».
 */
public class MZGramAppearanceActivity extends BaseCoreSettingsActivity {

    private static final int TYPE_AVATAR_CORNERS = 100;
    private static final int TYPE_CHAT_LIST = 101;
    private static final int TYPE_FOLDERS = 102;
    private static final int TYPE_SECTION_SLIDER = 103;
    /** Сворачиваемая группа со счётчиком и шевроном. */
    private static final int TYPE_EXPANDABLE_SWITCH = 104;
    /** Круглая галочка внутри группы. */
    private static final int TYPE_ROUND_CHECK = 105;
    /** Две карточки-превью формы плавающей кнопки. */
    private static final int TYPE_FAB_SHAPE = 106;

    // Appearance
    private int appearanceHeaderRow;
    private int fabShapeRow;
    private int useSystemFontsRow;
    private int gooeyAvatarRow;
    private int customThemesRow;
    private int appearanceDividerRow;

    // Sections (UI only)
    private int sectionsHeaderRow;
    private int sectionRadiusRow;
    private int separateHeadersRow;
    private int dividerStyleRow;
    private int sectionsDividerRow;

    // Blur
    private int blurHeaderRow;
    private int glassOutlineRow;
    private int glassMessageMenuRow;
    private int forceBlurRow;
    private int disableAvatarBlurRow;
    private int blurDividerRow;

    // Avatar corners
    private int avatarCornersPreviewRow;
    private int singleCornerRadiusRow;
    private int avatarsDividerRow;

    // Chat list
    private int chatListHeaderRow;
    private int chatListPreviewRow;
    private int forceSnowRow;
    private int centerTitleRow;
    // Material Design 3: сворачиваемая группа и пять вложенных стилей.
    private int md3GroupRow;
    private int md3LoadingRow;
    private int md3SliderRow;
    private int md3SwitchRow;
    private int md3ChatHeaderRow;
    private int md3NavBarRow;
    private int md3ListItemsRow;
    private int md3PlayerRow;
    private int md3MiniPlayerRow;
    private int md3SurfacesRow = -1;
    private int md3DialogsRow = -1;
    private int md3BarsRow = -1;
    private int md3InputRow = -1;
    private boolean md3Expanded;
    private int headerHeaderRow = -1;
    private int headerStyleRow = -1;
    private int searchStyleRow = -1;
    private int folderTabsRow = -1;
    private int headerDividerRow = -1;
    private int iosGroupRow;
    private int iosNavBarRow;
    private int iosChatHeaderRow;
    private boolean iosExpanded;
    private int senderMiniAvatarsRow;
    private int titleTextRow;
    private int chatListDividerRow;

    // Folders
    private int foldersHeaderRow;
    private int foldersPreviewRow;
    private int tabTitleStyleRow;
    private int startupFolderRow;
    private int tabCounterRow;
    private int foldersDividerRow;

    // Links
    private int linksHeaderRow;
    private int appNavigationRow;
    private int iconPacksRow;
    private int emojiSetsRow;
    private int pillStackRow;
    private int playerStyleRow;
    private int hidingRow;
    private int linksDividerRow;

    private AvatarCornersPreviewCell avatarCornersPreviewCell;
    private ChatListPreviewCell chatListPreviewCell;
    private FoldersPreviewCell foldersPreviewCell;
    private FabShapeCell fabShapeCell;

    /**
     * Отложенный rebuild после слайдера радиуса секций. MZGram
     * (handleSectionRadiusChange :335-343) зовёт rebuildFragments на каждое изменение,
     * но у него слайдер — Material-виджет с редкими колбэками; наш SeekBarView отдаёт
     * значение на каждый dp, и пересборка всех фрагментов на каждом шаге даёт рывки.
     */
    private final Runnable sectionRadiusRebuild = this::rebuildAll;

    public MZGramAppearanceActivity() {
        super();
        AppearanceConfig.init();
    }

    @Override
    protected List<CollapsibleGroup> collapsibleGroups() {
        return Arrays.asList(
                new CollapsibleGroup(() -> md3Expanded, expanded -> md3Expanded = expanded),
                new CollapsibleGroup(() -> iosExpanded, expanded -> iosExpanded = expanded));
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        avatarCornersPreviewRow = addRow("avatarCorners");
        singleCornerRadiusRow = addRow("singleCornerRadius");
        avatarsDividerRow = addRow();

        chatListHeaderRow = addRow("chatListHeader");
        chatListPreviewRow = addRow("chatListPreview");
        forceSnowRow = addRow("forceSnow");
        centerTitleRow = addRow("centerTitle");
        senderMiniAvatarsRow = addRow("senderMiniAvatars");
        titleTextRow = addRow("titleText");
        chatListDividerRow = addRow();

        // MZGram: the chat list header — standard or compact, search and folder tabs.
        headerHeaderRow = addRow("MZHeaderSection");
        headerStyleRow = addRow("MZHeaderStyle");
        searchStyleRow = addRow("MZSearchStyle");
        folderTabsRow = addRow("MZShowFolderTabs");
        headerDividerRow = addRow();

        foldersHeaderRow = addRow("foldersHeader");
        foldersPreviewRow = addRow("foldersPreview");
        tabTitleStyleRow = addRow("tabTitleStyle");
        startupFolderRow = getMessagesController().getDialogFilters().size() > 1 ? addRow("startupFolder") : -1;
        tabCounterRow = addRow("tabCounter");
        foldersDividerRow = addRow();

        // Порядок как в 12.9.0: строки-переходы идут сразу после «Chat Folders»,
        // до секции общего вида.
        linksHeaderRow = addRow("linksHeader");
        appNavigationRow = addRow("appNavigation");
        iconPacksRow = addRow("iconPacks");
        emojiSetsRow = addRow("emojiSets", "EmojiSets");
        pillStackRow = addRow("pillStack");
        playerStyleRow = addRow("playerStyle");
        hidingRow = addRow("hiding");
        linksDividerRow = addRow();

        appearanceHeaderRow = addRow("appearanceHeader");
        fabShapeRow = addRow("fabShape");
        useSystemFontsRow = addRow("useSystemFonts");
        // Material Design 3 — сворачиваемая группа, как у MZGram
        // (AppearancePreferencesActivity: asMZExpandableSwitch + пять asRoundCheckbox).
        // Прежние отдельные селекторы «стиль переключателей» и «стиль слайдеров»
        // стали двумя галочками внутри неё.
        md3GroupRow = addRow("md3Styles");
        if (md3Expanded) {
            md3SurfacesRow = addRow("MZMaterialSurfaces");
            md3DialogsRow = addRow("MZMaterialDialogs");
            md3BarsRow = addRow("MZMaterialBars");
            md3InputRow = addRow("MZMaterialInput");
            md3LoadingRow = addRow("md3Loading");
            md3SliderRow = addRow("md3Slider");
            md3SwitchRow = addRow("md3Switch");
            md3ChatHeaderRow = addRow("md3ChatHeader");
            md3NavBarRow = addRow("md3NavBar");
            md3ListItemsRow = addRow("md3ListItems");
            md3PlayerRow = addRow("md3Player");
            md3MiniPlayerRow = addRow("md3MiniPlayer");
        } else {
            md3LoadingRow = md3SliderRow = md3SwitchRow = md3ChatHeaderRow = md3NavBarRow = md3ListItemsRow = -1;
            md3PlayerRow = md3MiniPlayerRow = -1;
            md3SurfacesRow = md3DialogsRow = md3BarsRow = md3InputRow = -1;
        }
        iosGroupRow = addRow("iosStyles");
        if (iosExpanded) {
            iosNavBarRow = addRow("iosNavBar");
            iosChatHeaderRow = addRow("iosChatHeader");
        } else {
            iosNavBarRow = iosChatHeaderRow = -1;
        }
        gooeyAvatarRow = addRow("gooeyAvatar");
        customThemesRow = addRow("customThemes");
        appearanceDividerRow = addRow();

        sectionsHeaderRow = addRow("sectionsHeader");
        sectionRadiusRow = addRow("sectionRadius");
        separateHeadersRow = addRow("separateHeaders");
        dividerStyleRow = addRow("dividerStyle");
        sectionsDividerRow = addRow();

        blurHeaderRow = addRow("blurHeader");
        // MZGram: with solid Material surfaces there is no glass and no blur to tune.
        boolean solid = AppearanceConfig.materialSurfaces();
        glassOutlineRow = solid ? -1 : addRow("glassOutline");
        // The glass message menu draws over chat blur, which solid surfaces turn off.
        glassMessageMenuRow = solid ? -1 : addRow("glassMessageMenu");
        forceBlurRow = solid ? -1 : addRow("forceBlur");
        disableAvatarBlurRow = addRow("disableAvatarBlur");
        blurDividerRow = addRow();

    }

    // ---- MZGram: the screen opens as a list of its sections ----

    @Override
    protected boolean mzUsesSections() {
        return true;
    }

    @Override
    protected BaseCoreSettingsActivity mzCreateSectionPage() {
        return new MZGramAppearanceActivity();
    }

    /** "md3ChatHeader" and "iosChatHeader" are switches (the chat header style), not section headers. */
    @Override
    protected boolean mzIsSectionHeader(String key) {
        return super.mzIsSectionHeader(key) && !"md3ChatHeader".equals(key) && !"iosChatHeader".equals(key);
    }

    /** The links already open screens of their own: they stay on the list. */
    @Override
    protected boolean mzSectionInline(String section) {
        return "linksHeader".equals(section);
    }

    /** The header texts; "Appearance" inside Appearance is the style section, named so on its row. */
    @Override
    protected int mzSectionTitleRes(String section) {
        switch (section) {
            case MZ_TOP:
                return R.string.MZAppearanceAvatarCorners;
            case "chatListHeader":
                return R.string.MZAppearanceChatList;
            case "MZHeaderSection":
                return R.string.MZHeaderSection;
            case "foldersHeader":
                return R.string.MZAppearanceFolders;
            case "appearanceHeader":
                return R.string.MZSectionStyle;
            case "sectionsHeader":
                return R.string.MZAppearanceSections;
            case "blurHeader":
                return R.string.MZAppearanceBlur;
            default:
                return 0;
        }
    }

    @Override
    protected int mzSectionIcon(String section) {
        switch (section) {
            case MZ_TOP:
                return R.drawable.msg_contacts;
            case "chatListHeader":
                return R.drawable.msg_list;
            case "MZHeaderSection":
                return R.drawable.msg_topics;
            case "foldersHeader":
                return R.drawable.msg_folders;
            case "appearanceHeader":
                return R.drawable.msg_palette;
            case "sectionsHeader":
                return R.drawable.msg_customize;
            case "blurHeader":
                return R.drawable.msg_blur_radial;
            default:
                return R.drawable.msg_theme;
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MZAppearanceTitle);
    }

    @Override
    public int getSearchGuid() {
        return 21000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_theme;
    }

    @Override
    public String getSearchPrefix() {
        return "MZAppearance";
    }

    @Override
    protected String getKey() {
        return "mzgram_appearance";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    /**
     * Пересобрать список строк. Нужен там, где меняется их состав: базовый класс
     * зовёт updateRows() только в onFragmentCreate, поэтому одного
     * notifyDataSetChanged недостаточно.
     */
    private void rebuildRowsAndNotify() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void rebuildAll() {
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
    }

    private void showPowerSaverNotice() {
        if (!LiteMode.isPowerSaverApplied() || getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.info,
                        formatString(R.string.MZAppearancePowerSaverActive, LiteMode.getPowerSaverLevel()),
                        getString(R.string.MZAppearancePowerSaverSettings),
                        () -> presentFragment(new LiteModeSettingsActivity()))
                .show();
    }

    /**
     * Пересобрать чужие экраны и обновить этот.
     *
     * rebuildAllFragmentViews(false, ...) намеренно пропускает последний фрагмент
     * стека — то есть ровно тот, который открыт. Свои строки поэтому обновляем
     * сами: галочки внутри группы и счётчик «N/8» ставятся при привязке, а стиль
     * переключателей и слайдеров читается при отрисовке.
     */
    private void notifyRow(int row) {
        if (listAdapter != null && row >= 0) {
            listAdapter.notifyItemChanged(row);
        }
    }

    private void rebuildAllAndSelf(View clicked, boolean checked) {
        if (clicked instanceof org.telegram.ui.Cells.CheckBoxCell) {
            ((org.telegram.ui.Cells.CheckBoxCell) clicked).setChecked(checked, true);
        }
        if (listAdapter != null && md3GroupRow >= 0) {
            listAdapter.notifyItemChanged(md3GroupRow);
        }
        if (listAdapter != null && iosGroupRow >= 0) {
            listAdapter.notifyItemChanged(iosGroupRow);
        }
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                listView.getChildAt(i).invalidate();
            }
        }
        rebuildAll();
    }

    private void showRestartHint() {
        if (getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.info, getString(R.string.MZAppearanceNeedRestart),
                        getString(R.string.MZAppearanceRestartNow),
                        () -> {
                            Activity activity = getParentActivity();
                            if (activity != null) {
                                AppRestartHelper.triggerRebirth(activity,
                                        new Intent(activity, LaunchActivity.class));
                            }
                        })
                .show();
    }

    private static final int SEARCH_FIELD = 0;
    private static final int SEARCH_BUTTON = 1;
    private static final int SEARCH_HIDDEN = 2;

    private CharSequence[] searchStyleOptions() {
        return new CharSequence[]{
                getString(R.string.MZSearchStyleField),
                getString(R.string.MZSearchStyleButton),
                getString(R.string.MZSearchStyleHidden)
        };
    }

    /** Search bar, button or hidden. The compact header always shows search as a button. */
    private static int searchStyleIndex() {
        if (AppearanceConfig.hideDialogsSearch()) {
            return SEARCH_HIDDEN;
        }
        if (AppearanceConfig.compactDialogsHeader() || ExtConfig.INSTANCE.getHideDialogsSearchField().Bool()) {
            return SEARCH_BUTTON;
        }
        return SEARCH_FIELD;
    }

    private void onMaterialSurfacesChanged() {
        // Glass and blur are set up when screens are created, so they need a restart.
        rebuildRowsAndNotify();
        showRestartHint();
    }

    private void showSelector(int position, String title, CharSequence[] items, ConfigItem item, Runnable after) {
        if (getParentActivity() == null) {
            return;
        }
        showChoice(title, items, item.Int(), which -> {
            item.setConfigInt(which);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(position);
            }
            if (after != null) {
                after.run();
            }
        });
    }

    /** MZGram: a Material single-choice dialog, with a radio button on the current option. */
    private void showChoice(String title, CharSequence[] items, int selected, java.util.function.IntConsumer onPick) {
        if (getParentActivity() == null) {
            return;
        }
        showDialog(org.mzgram.settings.utils.MZChoice.create(getParentActivity(), title, items, selected,
                (dialog, which) -> onPick.accept(which)));
    }

    private CharSequence[] titleTextOptions() {
        return new CharSequence[]{
                getString(R.string.MZAppearanceTitleTextApp),
                getString(R.string.MZAppearanceTitleTextUsername),
                getString(R.string.MZAppearanceTitleTextName),
                getString(R.string.FilterChats),
                getString(R.string.MZAppearanceTitleTextCustom)
        };
    }

    private void showTitleTextSelector(Runnable onChanged) {
        if (getParentActivity() == null) {
            return;
        }
        showChoice(getString(R.string.MZAppearanceTitleText), titleTextOptions(), AppearanceConfig.titleText.Int(), which -> {
            if (which == AppearanceConfig.TITLE_TEXT_CUSTOM) {
                showCustomTitleDialog(onChanged);
            } else {
                setTitleText(which, onChanged);
            }
        });
    }

    private void setTitleText(int value, Runnable onChanged) {
        AppearanceConfig.titleText.setConfigInt(value);
        if (listAdapter != null) {
            listAdapter.notifyItemChanged(titleTextRow);
        }
        onChanged.run();
    }

    private void showCustomTitleDialog(Runnable onChanged) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        ConfigItem customTitle = ExtConfig.INSTANCE.getCustomTitle();

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.lineYFix = true;
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        if (!customTitle.defaultValue.equals(customTitle.String())) {
            editText.setText(customTitle.String());
        }
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintColor(getThemedColor(Theme.key_groupcreate_hintText));
        editText.setHintText(getString(R.string.MZAppearanceTitleTextApp));
        editText.setFocusable(true);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setBackground(null);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_text_RedRegular));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        editText.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 24f, 0f, 24f, 10f));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.MZAppearanceTitleText));
        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setWidth(AndroidUtilities.dp(292));
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> {
            String value = editText.getText() == null ? "" : editText.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                customTitle.setConfigString((String) customTitle.defaultValue);
                setTitleText(0, onChanged);
            } else {
                customTitle.setConfigString(value);
                setTitleText(AppearanceConfig.TITLE_TEXT_CUSTOM, onChanged);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            editText.requestFocus();
            editText.setSelection(editText.length());
            AndroidUtilities.showKeyboard(editText);
        });
        showDialog(dialog, d -> AndroidUtilities.hideKeyboard(editText));
    }

    /**
     * Порядок пунктов «Folder Title» — «Names with Icons», «Names only», «Icons only»;
     * у CoreConfig.tabsTitleType значения 0 TEXT, 1 ICON, 2 MIX.
     * Массив переводит индекс диалога в значение конфига.
     */
    private static final int[] TAB_TITLE_ORDER = {2, 0, 1};

    private CharSequence startupFolderName(MessagesController.DialogFilter filter) {
        return filter.isDefault() ? getString(R.string.FilterAllChats) : filter.name;
    }

    private CharSequence startupFolderValue() {
        int mode = StartupFolder.getMode(currentAccount);
        if (mode == StartupFolder.MODE_LAST) {
            return getString(R.string.MZStartupFolderLast);
        }
        ArrayList<MessagesController.DialogFilter> filters = getMessagesController().getDialogFilters();
        int index = StartupFolder.targetIndex(currentAccount, filters);
        return index >= 0 ? startupFolderName(filters.get(index)) : getString(R.string.MZStartupFolderFirst);
    }

    private void showStartupFolderDialog(int position) {
        if (getParentActivity() == null) {
            return;
        }
        ArrayList<Integer> modes = new ArrayList<>();
        ArrayList<CharSequence> titles = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        modes.add(StartupFolder.MODE_FIRST);
        titles.add(getString(R.string.MZStartupFolderFirst));
        icons.add(R.drawable.msg_folders);
        modes.add(StartupFolder.MODE_LAST);
        titles.add(getString(R.string.MZStartupFolderLast));
        icons.add(R.drawable.msg_recent);
        for (MessagesController.DialogFilter filter : getMessagesController().getDialogFilters()) {
            if (filter.isDefault() && CoreConfig.hideAllTab.Bool()) {
                continue;
            }
            modes.add(filter.id);
            titles.add(startupFolderName(filter));
            icons.add(filter.isDefault() ? R.drawable.filter_all : FolderIconHelper.getTabIcon(filter.emoticon));
        }
        int[] iconIds = new int[icons.size()];
        for (int i = 0; i < iconIds.length; i++) {
            iconIds[i] = icons.get(i);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.MZStartupFolder));
        builder.setItems(titles.toArray(new CharSequence[0]), iconIds, (dialog, which) -> {
            StartupFolder.setMode(currentAccount, modes.get(which));
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(position);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        int selected = modes.indexOf(StartupFolder.getMode(currentAccount));
        if (selected >= 0) {
            int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueText);
            dialog.setItemColor(selected, accent, accent);
        }
    }

    private CharSequence[] tabTitleOptions() {
        return new CharSequence[]{
                getString(R.string.MZAppearanceTabTitleStyleTextWithIcons),
                getString(R.string.MZAppearanceTabTitleStyleTextOnly),
                getString(R.string.MZAppearanceTabTitleStyleIconsOnly)
        };
    }

    /** Значение конфига -> индекс в {@link #tabTitleOptions()}. */
    private static int tabTitleIndex(int configValue) {
        for (int i = 0; i < TAB_TITLE_ORDER.length; i++) {
            if (TAB_TITLE_ORDER[i] == configValue) {
                return i;
            }
        }
        return 0;
    }

    private void onDividerStyleChanged() {
        // 0 — скрыт, 1 — линия, 2 — сегменты. Скрытый привязываем к реальному ExtConfig.hideDividers,
        // чтобы не разъезжался экран CoreGeneralSettingsActivity.
        ExtConfig.INSTANCE.getHideDividers().setConfigBool(AppearanceConfig.dividerStyle.Int() == 0);
        // Theme.getColor читает закешированное значение — сбросить кэш обязательно.
        AppearanceConfig.invalidateDividerStyle();
        // «Сегменты» рисуются раздельными карточками, и заголовок обязан быть своей карточкой,
        // иначе секция склеивается — настройка дожимается принудительно.
        if (AppearanceConfig.dividerStyle.Int() == AppearanceConfig.DIVIDER_SEGMENTS
                && !AppearanceConfig.separateHeaders.Bool()) {
            AppearanceConfig.separateHeaders.setConfigBool(true);
        }
        if (listAdapter != null) {
            listAdapter.notifyItemChanged(separateHeadersRow);
        }
        // Цвет разделителя лежит в общей теме,
        // без applyCommonTheme новые значения не подхватят уже созданные ячейки.
        Theme.applyCommonTheme();
        if (listView != null) {
            listView.invalidate();
            listView.invalidateItemDecorations();
        }
        invalidatePreviews();
        rebuildAll();
    }

    /**
     * Новый радиус надо занести в декоратор
     * списка и перерисовать его, иначе на текущем экране ничего не меняется.
     */
    private void onSectionRadiusChanged(int value) {
        AppearanceConfig.sectionRadius.setConfigInt(value);
        if (listView != null) {
            listView.setSections(AndroidUtilities.dp(12), AndroidUtilities.dp(value), true);
            listView.invalidate();
            listView.invalidateItemDecorations();
        }
        AndroidUtilities.cancelRunOnUIThread(sectionRadiusRebuild);
        AndroidUtilities.runOnUIThread(sectionRadiusRebuild, 350);
    }

    /** Все живые превью экрана — их надо дёргать на смене темы и стиля разделителя. */
    private void invalidatePreviews() {
        if (avatarCornersPreviewCell != null) avatarCornersPreviewCell.invalidate();
        if (chatListPreviewCell != null) chatListPreviewCell.invalidate();
        if (foldersPreviewCell != null) foldersPreviewCell.invalidate();
        if (fabShapeCell != null) fabShapeCell.invalidate();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == iconPacksRow) {
            presentFragment(new IconPacksActivity());
            return;
        } else if (position == pillStackRow) {
            presentFragment(new PillStackSettingsActivity());
            return;
        } else if (position == playerStyleRow) {
            org.mzgram.player.PlayerSheet.openStyleEditor(this);
            return;
        } else if (position == appNavigationRow) {
            presentFragment(new MZGramAppNavigationActivity());
            return;
        } else if (position == emojiSetsRow) {
            presentFragment(new CoreEmojiSettingsActivity());
            return;
        } else if (position == hidingRow) {
            presentFragment(new MZGramChatsActivity(MZGramChatsActivity.SCREEN_HIDING));
            return;
        } else if (position == md3GroupRow) {
            md3Expanded = !md3Expanded;
            rebuildRowsAndNotify();
            return;
        } else if (position == md3SurfacesRow) {
            AppearanceConfig.materialSurfaces.setConfigBool(!AppearanceConfig.materialSurfaces.Bool());
            onMaterialSurfacesChanged();
            rebuildAllAndSelf(view, AppearanceConfig.materialSurfaces.Bool());
            return;
        } else if (position == md3DialogsRow) {
            AppearanceConfig.materialDialogs.setConfigBool(!AppearanceConfig.materialDialogs.Bool());
            rebuildAllAndSelf(view, AppearanceConfig.materialDialogs.Bool());
            return;
        } else if (position == md3BarsRow) {
            AppearanceConfig.materialBars.setConfigBool(!AppearanceConfig.materialBars.Bool());
            rebuildAllAndSelf(view, AppearanceConfig.materialBars.Bool());
            return;
        } else if (position == md3InputRow) {
            AppearanceConfig.materialInput.setConfigBool(!AppearanceConfig.materialInput.Bool());
            rebuildAllAndSelf(view, AppearanceConfig.materialInput.Bool());
            return;
        } else if (position == headerStyleRow) {
            showSelector(position, getString(R.string.MZHeaderStyle), new CharSequence[]{
                    getString(R.string.MZHeaderStyleStandard),
                    getString(R.string.MZHeaderStyleCompact)
            }, AppearanceConfig.dialogsHeaderStyle, () -> {
                // The compact header has no search bar, so the Search row's value changes too.
                notifyRow(searchStyleRow);
                rebuildAll();
            });
            return;
        } else if (position == searchStyleRow) {
            showChoice(getString(R.string.MZSearchStyle), searchStyleOptions(), searchStyleIndex(), which -> {
                AppearanceConfig.hideDialogsSearch.setConfigBool(which == SEARCH_HIDDEN);
                ExtConfig.INSTANCE.getHideDialogsSearchField().setConfigBool(which != SEARCH_FIELD);
                if (which == SEARCH_FIELD && AppearanceConfig.compactDialogsHeader()) {
                    // The search bar only exists in the standard header.
                    AppearanceConfig.dialogsHeaderStyle.setConfigInt(AppearanceConfig.HEADER_STANDARD);
                    notifyRow(headerStyleRow);
                }
                notifyRow(position);
                rebuildAll();
            });
            return;
        } else if (position == folderTabsRow) {
            AppearanceConfig.hideFolderTabs.setConfigBool(!AppearanceConfig.hideFolderTabs.Bool());
            if (view instanceof org.telegram.ui.Cells.TextCheckCell) {
                ((org.telegram.ui.Cells.TextCheckCell) view).setChecked(!AppearanceConfig.hideFolderTabs.Bool());
            }
            rebuildAll();
            return;
        } else if (position == md3LoadingRow) {
            AppearanceConfig.newLoadingStyle.setConfigBool(!AppearanceConfig.newLoadingStyle.Bool());
            rebuildAllAndSelf(view, AppearanceConfig.newLoadingStyle.Bool());
            return;
        } else if (position == md3SliderRow) {
            // Стиль слайдера читается в SeekBarView.getEffectiveSliderStyle() на каждой отрисовке,
            // стиль переключателя — в Switch на каждой; перезапуск не нужен, хватает пересборки
            // вьюх — так же делает CoreGeneralSettingsActivity.
            ExtConfig.INSTANCE.getSliderStyle().setConfigInt(
                    isMd3(ExtConfig.INSTANCE.getSliderStyle().Int()) ? 0 : STYLE_MD3);
            if (avatarCornersPreviewCell != null) {
                avatarCornersPreviewCell.invalidate();
            }
            rebuildAllAndSelf(view, isMd3(ExtConfig.INSTANCE.getSliderStyle().Int()));
            return;
        } else if (position == md3SwitchRow) {
            ExtConfig.INSTANCE.getSwitchStyle().setConfigInt(
                    isMd3Switch(ExtConfig.INSTANCE.getSwitchStyle().Int()) ? 0 : SWITCH_STYLE_M3);
            rebuildAllAndSelf(view, isMd3Switch(ExtConfig.INSTANCE.getSwitchStyle().Int()));
            return;
        } else if (position == md3ChatHeaderRow) {
            AppearanceConfig.newChatHeaderStyle.setConfigBool(!AppearanceConfig.newChatHeaderStyle.Bool());
            rebuildAllAndSelf(view, AppearanceConfig.newChatHeaderStyle.Bool());
            return;
        } else if (position == md3NavBarRow) {
            boolean enable = !md3NavBarShown();
            AppearanceConfig.newNavigationBarStyle.setConfigBool(enable);
            if (enable) {
                AppearanceConfig.iosNavigationBarStyle.setConfigBool(false);
                notifyRow(iosNavBarRow);
                leaveFloatingBottomNavigation();
            }
            rebuildAllAndSelf(view, enable);
            return;
        } else if (position == md3ListItemsRow) {
            AppearanceConfig.m3ListItems.setConfigBool(!AppearanceConfig.m3ListItems.Bool());
            onM3ListItemsChanged();
            rebuildAllAndSelf(view, AppearanceConfig.m3ListItems.Bool());
            return;
        } else if (position == md3PlayerRow) {
            AppearanceConfig.md3Player.setConfigBool(!AppearanceConfig.md3Player.Bool());
            rebuildAllAndSelf(view, AppearanceConfig.md3Player.Bool());
            return;
        } else if (position == md3MiniPlayerRow) {
            AppearanceConfig.md3MiniPlayer.setConfigBool(!AppearanceConfig.md3MiniPlayer.Bool());
            rebuildAllAndSelf(view, AppearanceConfig.md3MiniPlayer.Bool());
            return;
        } else if (position == iosGroupRow) {
            iosExpanded = !iosExpanded;
            rebuildRowsAndNotify();
            return;
        } else if (position == iosNavBarRow) {
            boolean enable = !AppearanceConfig.iosNavigationBarStyle.Bool();
            AppearanceConfig.iosNavigationBarStyle.setConfigBool(enable);
            if (enable) {
                AppearanceConfig.newNavigationBarStyle.setConfigBool(false);
                notifyRow(md3NavBarRow);
            }
            rebuildAllAndSelf(view, enable);
            return;
        } else if (position == iosChatHeaderRow) {
            AppearanceConfig.iosChatHeader.setConfigBool(!AppearanceConfig.iosChatHeader.Bool());
            rebuildAllAndSelf(view, AppearanceConfig.iosChatHeader.Bool());
            return;
        } else if (position == dividerStyleRow) {
            showSelector(position, getString(R.string.MZAppearanceDividerStyle), new CharSequence[]{
                    getString(R.string.MZAppearanceDividerHidden),
                    getString(R.string.MZAppearanceDividerLine),
                    getString(R.string.MZAppearanceDividerSegments)
            }, AppearanceConfig.dividerStyle, this::onDividerStyleChanged);
            return;
        } else if (position == glassOutlineRow) {
            showSelector(position, getString(R.string.MZAppearanceGlassOutline), new CharSequence[]{
                    getString(R.string.MZAppearanceGlassOutlineGlare),
                    getString(R.string.MZAppearanceGlassOutlineSolid),
                    getString(R.string.MZAppearanceGlassOutlineHidden)
            }, AppearanceConfig.glassOutlineStyle, null);
            return;
        } else if (position == startupFolderRow) {
            showStartupFolderDialog(position);
            return;
        } else if (position == tabTitleStyleRow) {
            if (getParentActivity() == null) {
                return;
            }
            // Порядок пунктов диалога свой, значения CoreConfig.tabsTitleType тоже
            // (0 TEXT, 1 ICON, 2 MIX),
            // поэтому индекс диалога отображается через TAB_TITLE_ORDER.
            int currentTabTitle = -1;
            for (int i = 0; i < TAB_TITLE_ORDER.length; i++) {
                if (TAB_TITLE_ORDER[i] == CoreConfig.tabsTitleType.Int()) {
                    currentTabTitle = i;
                }
            }
            showChoice(getString(R.string.MZAppearanceTabTitleStyle), tabTitleOptions(), currentTabTitle, which -> {
                CoreConfig.tabsTitleType.setConfigInt(TAB_TITLE_ORDER[clamp(which, TAB_TITLE_ORDER.length)]);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(position);
                }
                if (foldersPreviewCell != null) {
                    foldersPreviewCell.updateTabTitle(true);
                    foldersPreviewCell.updateTabIcons(true);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            });
            return;
        } else if (position == tabCounterRow) {
            showSelector(position, getString(R.string.MZAppearanceTabCounter), new CharSequence[]{
                    getString(R.string.MZAppearanceTabCounterAll),
                    getString(R.string.MZAppearanceTabCounterUnmuted),
                    getString(R.string.MZAppearanceTabCounterOff)
            }, ExtConfig.INSTANCE.getIgnoreUnreadCount(), () -> {
                if (foldersPreviewCell != null) {
                    foldersPreviewCell.updateTabCounter(true);
                }
                showRestartHint();
            });
            return;
        } else if (position == centerTitleRow) {
            // Обычный переключатель, а не селектор из четырёх пунктов:
            // центровка либо есть, либо нет.
            AppearanceConfig.setCenterTitle(!AppearanceConfig.centerTitle());
            if (view instanceof org.telegram.ui.Cells.TextCheckCell) {
                ((org.telegram.ui.Cells.TextCheckCell) view)
                        .setChecked(AppearanceConfig.INSTANCE.centerTitle());
            }
            if (chatListPreviewCell != null) {
                chatListPreviewCell.updateCentered(true);
            }
            rebuildAll();
            return;
        } else if (position == titleTextRow) {
            Runnable onTitleTextChanged = () -> {
                if (chatListPreviewCell != null) {
                    chatListPreviewCell.updateTitle(true);
                    // Эмодзи-статус стоит вплотную к заголовку, и его позиция зависит от длины
                    // текста.
                    chatListPreviewCell.updateStatus(true);
                }
                getNotificationCenter().postNotificationName(
                        NotificationCenter.currentUserPremiumStatusChanged);
                // Сам заголовок списка чатов ставится один раз в DialogsActivity.createView
                // (:3645), по уведомлению он не переустанавливается — нужна пересборка вьюх.
                rebuildAll();
            };
            showTitleTextSelector(onTitleTextChanged);
            return;
        } else if (position == forceSnowRow) {
            boolean enabled = CoreConfig.actionBarDecoration.Int() != 1;
            CoreConfig.actionBarDecoration.setConfigInt(enabled ? 1 : 0);
            ExtConfig.INSTANCE.getChatDecoration().setConfigInt(enabled ? 1 : 0);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            rebuildAll();
            if (enabled) {
                showPowerSaverNotice();
            }
            return;
        } else if (position == forceBlurRow) {
            boolean enabled = !LiteMode.isEnabledSetting(LiteMode.FLAG_CHAT_BLUR);
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, enabled);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            rebuildAll();
            if (enabled) {
                showPowerSaverNotice();
            }
            return;
        } else if (position == glassMessageMenuRow) {
            boolean enabled = AppearanceConfig.glassMessageMenu.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            // Стеклянное меню рисуется поверх
            // блюра, и без него настройка не даёт ничего видимого, поэтому предлагаем включить.
            if (enabled && !LiteMode.isEnabledSetting(LiteMode.FLAG_CHAT_BLUR) && getParentActivity() != null) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info,
                                getString(R.string.MZAppearanceGlassMessageMenuBlurOff),
                                getString(R.string.Enable),
                                () -> {
                                    LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
                                    notifyRow(forceBlurRow);
                                    rebuildAll();
                                    showPowerSaverNotice();
                                })
                        .show();
            } else if (enabled) {
                showPowerSaverNotice();
            }
            return;
        } else if (position == separateHeadersRow
                && AppearanceConfig.sectionsSeparatedHeadersForced()) {
            // При «Сегментах» строка нарисована выключенной,
            // клик по ней ничего не меняет.
            return;
        }

        ConfigItem item = null;
        boolean rebuild = false;
        boolean restart = false;
        boolean clearTypefaces = false;

        if (position == useSystemFontsRow) {
            item = CoreConfig.typeface;
            restart = true;
            clearTypefaces = true;
        } else if (position == gooeyAvatarRow) {
            item = AppearanceConfig.gooeyAvatarAnimation;
        } else if (position == customThemesRow) {
            item = AppearanceConfig.customThemes;
        } else if (position == separateHeadersRow) {
            item = AppearanceConfig.separateHeaders;
        } else if (position == disableAvatarBlurRow) {
            item = ExtConfig.INSTANCE.getDisableAvatarBlur();
            rebuild = true;
        } else if (position == singleCornerRadiusRow) {
            item = AppearanceConfig.singleCornerRadius;
            rebuild = true;
        } else if (position == senderMiniAvatarsRow) {
            item = AppearanceConfig.senderMiniAvatars;
        }

        if (item == null) {
            return;
        }

        boolean value = item.toggleConfigBool();
        if (clearTypefaces) {
            AndroidUtilities.clearTypefaceCache();
        }
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(value);
        }
        if (rebuild) {
            rebuildAll();
        }
        if (restart) {
            showRestartHint();
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_EXPANDABLE_SWITCH:
                    view = new org.telegram.ui.Cells.TextCheckCell2(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_ROUND_CHECK: {
                    // Тип 4 — круглая галочка с отступом под вложенный пункт,
                    // ровно как у MZGram в UniversalAdapter (view type 35).
                    org.telegram.ui.Cells.CheckBoxCell checkBoxCell =
                            new org.telegram.ui.Cells.CheckBoxCell(mContext, 4, 21, resourcesProvider);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked,
                            Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = checkBoxCell;
                    break;
                }
                case TYPE_AVATAR_CORNERS:
                    avatarCornersPreviewCell = new AvatarCornersPreviewCell(mContext,
                            MZGramAppearanceActivity.this::rebuildAll);
                    avatarCornersPreviewCell.setNeedDivider(true);
                    view = avatarCornersPreviewCell;
                    break;
                case TYPE_CHAT_LIST:
                    chatListPreviewCell = new ChatListPreviewCell(mContext);
                    view = chatListPreviewCell;
                    break;
                case TYPE_FOLDERS:
                    foldersPreviewCell = new FoldersPreviewCell(mContext, resourcesProvider);
                    view = foldersPreviewCell;
                    break;
                case TYPE_FAB_SHAPE:
                    fabShapeCell = new FabShapeCell(mContext,
                            MZGramAppearanceActivity.this::rebuildAll);
                    fabShapeCell.setNeedDivider(true);
                    view = fabShapeCell;
                    break;
                case TYPE_DETAIL_SETTINGS: {
                    // Базовый класс делает такую же ячейку, но многострочной; MZGram
                    // (asButtonWithSubtext(..., 64, 60) :456-458) держит ровно 64 dp.
                    TextDetailSettingsCell detailCell = new TextDetailSettingsCell(mContext);
                    detailCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    detailCell.setMultilineDetail(false);
                    view = detailCell;
                    break;
                }
                case TYPE_SECTION_SLIDER:
                    AvatarCornersSeekBar slider = new AvatarCornersSeekBar(mContext,
                            MZGramAppearanceActivity.this::onSectionRadiusChanged,
                            0, AppearanceConfig.AVATAR_CORNERS_MAX,
                            getString(R.string.MZAppearanceSectionRadius),
                            getString(R.string.MZAppearanceSectionRadiusOff),
                            getString(R.string.MZAppearanceSectionRadiusMax));
                    slider.setValueSuffix("dp");
                    slider.setValue(AppearanceConfig.sectionRadius.Int());
                    slider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = slider;
                    break;
                default:
                    return super.onCreateViewHolder(parent, viewType);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == appearanceHeaderRow) {
                        cell.setText(getString(R.string.MZAppearanceGeneral));
                    } else if (position == sectionsHeaderRow) {
                        cell.setText(getString(R.string.MZAppearanceSections));
                    } else if (position == blurHeaderRow) {
                        cell.setText(getString(R.string.MZAppearanceBlur));
                    } else if (position == chatListHeaderRow) {
                        cell.setText(getString(R.string.MZAppearanceChatList));
                    } else if (position == foldersHeaderRow) {
                        cell.setText(getString(R.string.MZAppearanceFolders));
                    } else if (position == headerHeaderRow) {
                        cell.setText(getString(R.string.MZHeaderSection));
                    } else if (position == linksHeaderRow) {
                        cell.setText(getString(R.string.MZAppearanceInterface));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    // Ячейки переиспользуются, поэтому «включённость» надо возвращать явно:
                    // иначе строка, побывавшая заблокированной, останется полупрозрачной.
                    cell.setEnabled(position != separateHeadersRow
                            || !AppearanceConfig.sectionsSeparatedHeadersForced(), null);
                    if (position == centerTitleRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceCenterTitle),
                                AppearanceConfig.INSTANCE.centerTitle(), true);
                    } else if (position == folderTabsRow) {
                        cell.setTextAndCheck(getString(R.string.MZShowFolderTabs),
                                !AppearanceConfig.hideFolderTabs(), false);
                    } else if (position == useSystemFontsRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceUseSystemFonts), CoreConfig.typeface.Bool(), true);
                    } else if (position == gooeyAvatarRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceGooeyAvatar), AppearanceConfig.gooeyAvatarAnimation.Bool(), true);
                    } else if (position == customThemesRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceCustomThemes), AppearanceConfig.customThemes.Bool(), false);
                    } else if (position == separateHeadersRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceSeparateHeaders), AppearanceConfig.sectionsSeparatedHeaders(), true);
                    } else if (position == glassMessageMenuRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.MZAppearanceGlassMessageMenu), getString(R.string.MZAppearanceGlassMessageMenuInfo), AppearanceConfig.glassMessageMenu.Bool(), true, true);
                    } else if (position == forceBlurRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceForceBlur), LiteMode.isEnabledSetting(LiteMode.FLAG_CHAT_BLUR), true);
                    } else if (position == disableAvatarBlurRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceDisableAvatarBlur), ExtConfig.INSTANCE.getDisableAvatarBlur().Bool(), false);
                    } else if (position == singleCornerRadiusRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceSingleCornerRadius), AppearanceConfig.singleCornerRadius.Bool(), false);
                    } else if (position == forceSnowRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.MZAppearanceForceSnow), getString(R.string.MZAppearanceForceSnowInfo), CoreConfig.actionBarDecoration.Int() == 1, true, true);
                    } else if (position == senderMiniAvatarsRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceSenderMiniAvatars), AppearanceConfig.senderMiniAvatars.Bool(), false);
                    }
                    break;
                }
                case TYPE_EXPANDABLE_SWITCH: {
                    org.telegram.ui.Cells.TextCheckCell2 cell =
                            (org.telegram.ui.Cells.TextCheckCell2) holder.itemView;
                    // Иначе выключенная группа горит красным: Switch по умолчанию
                    // идёт в «разрешительных» цветах экрана прав участника.
                    cell.useStandardSwitchColors();
                    if (position == iosGroupRow) {
                        cell.setTextAndCheck(getString(R.string.MZAppearanceIosDesign),
                                iosSelectedCount() > 0, true);
                        cell.setCollapseArrow(iosSelectedCount() + "/" + IOS_STYLE_COUNT, !iosExpanded,
                                MZGramAppearanceActivity.this::toggleAllIosStyles);
                        break;
                    }
                    cell.setTextAndCheck(getString(R.string.MZAppearanceMaterialDesign3),
                            md3SelectedCount() > 0, true);
                    // Правая зона (76 dp за разделителем) — сам переключатель, как у MZGram:
                    // туда уходит клик по мастер-тумблеру. Тело строки сворачивает группу.
                    cell.setCollapseArrow(md3SelectedCount() + "/" + MD3_STYLE_COUNT, !md3Expanded,
                            MZGramAppearanceActivity.this::toggleAllMd3StylesFromCell);
                    break;
                }
                case TYPE_ROUND_CHECK: {
                    org.telegram.ui.Cells.CheckBoxCell cell =
                            (org.telegram.ui.Cells.CheckBoxCell) holder.itemView;
                    if (position == md3SurfacesRow) {
                        cell.setText(getString(R.string.MZMaterialSurfaces), "",
                                AppearanceConfig.materialSurfaces.Bool(), true, true);
                    } else if (position == md3DialogsRow) {
                        cell.setText(getString(R.string.MZMaterialDialogs), "",
                                AppearanceConfig.materialDialogs.Bool(), true, true);
                    } else if (position == md3BarsRow) {
                        cell.setText(getString(R.string.MZMaterialBars), "",
                                AppearanceConfig.materialBars.Bool(), true, true);
                    } else if (position == md3InputRow) {
                        cell.setText(getString(R.string.MZMaterialInput), "",
                                AppearanceConfig.materialInput.Bool(), true, true);
                    } else if (position == md3LoadingRow) {
                        cell.setText(getString(R.string.MZAppearanceNewLoadingStyle), "",
                                AppearanceConfig.newLoadingStyle.Bool(), true, true);
                    } else if (position == md3SliderRow) {
                        cell.setText(getString(R.string.MZAppearanceSliderStyle), "",
                                isMd3(ExtConfig.INSTANCE.getSliderStyle().Int()), true, true);
                    } else if (position == md3SwitchRow) {
                        cell.setText(getString(R.string.MZAppearanceSwitchStyle), "",
                                isMd3Switch(ExtConfig.INSTANCE.getSwitchStyle().Int()), true, true);
                    } else if (position == md3ChatHeaderRow) {
                        cell.setText(getString(R.string.MZAppearanceNewChatHeaderStyle), "",
                                AppearanceConfig.newChatHeaderStyle.Bool(), true, true);
                    } else if (position == md3NavBarRow) {
                        cell.setText(getString(R.string.MZAppearanceNewNavigationBarStyle), "",
                                md3NavBarShown(), true, true);
                    } else if (position == md3ListItemsRow) {
                        cell.setText(getString(R.string.MZAppearanceM3ListItems), "",
                                AppearanceConfig.m3ListItems.Bool(), true, true);
                    } else if (position == md3PlayerRow) {
                        cell.setText(getString(R.string.MZAppearanceMd3Player), "",
                                AppearanceConfig.md3Player.Bool(), true, true);
                    } else if (position == md3MiniPlayerRow) {
                        cell.setText(getString(R.string.MZAppearanceMd3MiniPlayer), "",
                                AppearanceConfig.md3MiniPlayer.Bool(), false, true);
                    } else if (position == iosNavBarRow) {
                        cell.setText(getString(R.string.MZAppearanceIosNavigationBarStyle), "",
                                AppearanceConfig.iosNavigationBarStyle.Bool(), true, true);
                    } else if (position == iosChatHeaderRow) {
                        cell.setText(getString(R.string.MZAppearanceIosChatHeader), "",
                                AppearanceConfig.iosChatHeader.Bool(), false, true);
                    }
                    cell.setPad(1);
                    // По умолчанию ячейка этого типа красит текст серым; у MZGram
                    // вложенные пункты того же цвета, что и обычные строки.
                    cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == headerStyleRow) {
                        String[] v = {getString(R.string.MZHeaderStyleStandard), getString(R.string.MZHeaderStyleCompact)};
                        cell.setTextAndValue(getString(R.string.MZHeaderStyle), v[clamp(AppearanceConfig.dialogsHeaderStyle.Int(), v.length)], true);
                    } else if (position == searchStyleRow) {
                        CharSequence[] v = searchStyleOptions();
                        cell.setTextAndValue(getString(R.string.MZSearchStyle), v[searchStyleIndex()], true);
                    } else if (position == dividerStyleRow) {
                        String[] v = {getString(R.string.MZAppearanceDividerHidden), getString(R.string.MZAppearanceDividerLine), getString(R.string.MZAppearanceDividerSegments)};
                        cell.setTextAndValue(getString(R.string.MZAppearanceDividerStyle), v[clamp(AppearanceConfig.dividerStyle.Int(), v.length)], false);
                    } else if (position == glassOutlineRow) {
                        String[] v = {getString(R.string.MZAppearanceGlassOutlineGlare), getString(R.string.MZAppearanceGlassOutlineSolid), getString(R.string.MZAppearanceGlassOutlineHidden)};
                        cell.setTextAndValue(getString(R.string.MZAppearanceGlassOutline), v[clamp(AppearanceConfig.glassOutlineStyle.Int(), v.length)], true);
                    } else if (position == startupFolderRow) {
                        cell.setTextAndValue(getString(R.string.MZStartupFolder), startupFolderValue(), true);
                    } else if (position == tabTitleStyleRow) {
                        CharSequence[] v = tabTitleOptions();
                        cell.setTextAndValue(getString(R.string.MZAppearanceTabTitleStyle), v[tabTitleIndex(CoreConfig.tabsTitleType.Int())], true);
                    } else if (position == tabCounterRow) {
                        String[] v = {getString(R.string.MZAppearanceTabCounterAll),
                                getString(R.string.MZAppearanceTabCounterUnmuted),
                                getString(R.string.MZAppearanceTabCounterOff)};
                        cell.setTextAndValue(getString(R.string.MZAppearanceTabCounter), v[clamp(ExtConfig.INSTANCE.getIgnoreUnreadCount().Int(), v.length)], false);
                    } else if (position == titleTextRow) {
                        CharSequence[] v = titleTextOptions();
                        int titleText = clamp(AppearanceConfig.titleText.Int(), v.length);
                        cell.setTextAndValue(getString(R.string.MZAppearanceTitleText),
                                titleText == AppearanceConfig.TITLE_TEXT_CUSTOM ? ExtConfig.INSTANCE.getCustomTitle().String() : v[titleText], false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    // MZGram: the links are the same Material list item as the section rows
                    // (icon, title, one-line summary, 64 dp, text at 72 dp).
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == appNavigationRow) {
                        cell.setTextAndIcon(getString(R.string.MZAppearanceNavigation), R.drawable.msg_newphone, true);
                        cell.setSubtitle(getString(R.string.MZAppearanceNavigationSub));
                    } else if (position == iconPacksRow) {
                        cell.setTextAndIcon(getString(R.string.MZAppearanceIconPacks), R.drawable.msg_sticker, true);
                        cell.setSubtitle(getString(R.string.MZAppearanceIconPacksInfo));
                    } else if (position == emojiSetsRow) {
                        cell.setTextAndIcon(getString(R.string.EmojiSets), R.drawable.msg_emoji_smiles, true);
                        cell.setSubtitle(getString(CoreConfig.useSystemEmoji.Bool()
                                ? R.string.MZAppearanceUseSystemEmoji : R.string.MZAppearanceEmojiSetsInfo));
                    } else if (position == pillStackRow) {
                        cell.setTextAndIcon(getString(R.string.MZAppearancePillStack), R.drawable.outline_header_search, true);
                        cell.setSubtitle(getString(R.string.MZAppearancePillStackInfo));
                    } else if (position == playerStyleRow) {
                        cell.setTextAndIcon(getString(R.string.MZPlayerStyleTitle), R.drawable.msg_filled_data_music, true);
                        cell.setSubtitle(getString(R.string.MZPlayerStyleInfo));
                    } else if (position == hidingRow) {
                        cell.setTextAndIcon(getString(R.string.MZAppearanceHiding), R.drawable.msg_archive_hide, false);
                        cell.setSubtitle(getString(R.string.MZAppearanceHidingInfo));
                    }
                    cell.heightDp = 64;
                    cell.requestLayout();
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    // Скруглённый «хвост» списка положен последней тени, а она у нас после блока
                    // Blur, а не после строк-переходов.
                    boolean bottom = position == blurDividerRow;
                    if (position == appearanceDividerRow) {
                        cell.setText(getString(R.string.MZAppearanceCustomThemesInfo));
                    } else if (position == blurDividerRow) {
                        cell.setText(getString(AppearanceConfig.materialSurfaces() ? R.string.MZSolidPanelsBlurInfo : R.string.MZAppearanceBlurInfo));
                    } else if (position == avatarsDividerRow) {
                        cell.setText(getString(R.string.MZAppearanceSingleCornerRadiusInfo));
                    } else if (position == chatListDividerRow) {
                        cell.setText(getString(R.string.MZAppearanceChatListInfo));
                    } else if (position == foldersDividerRow) {
                        cell.setText(getString(R.string.MZAppearanceFoldersInfo));
                    } else if (position == headerDividerRow) {
                        cell.setText(getString(R.string.MZHeaderInfo));
                    } else {
                        cell.setText(null);
                    }
                    cell.setBackground(Theme.getThemedDrawable(mContext,
                            bottom ? R.drawable.greydivider_bottom : R.drawable.greydivider,
                            Theme.key_windowBackgroundGrayShadow));
                    break;
                }
            }
        }

        /**
         * Свои типы ячеек базовый адаптер считает некликабельными.
         *
         * Для превью это верно, а группа Material Design 3 и галочки внутри неё
         * обработчики имеют: RecyclerListView не только не доводил до них клик,
         * но и звал setEnabled(false) на строке (onChildAttachedToWindow) — из-за
         * чего вся группа выглядела погашенной.
         */
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int type = holder.getItemViewType();
            if (type == TYPE_EXPANDABLE_SWITCH || type == TYPE_ROUND_CHECK) {
                return true;
            }
            return super.isEnabled(holder);
        }

        @Override
        public int getItemViewType(int position) {
            final int mzType = mzSectionItemViewType(position);
            if (mzType != -1) {
                return mzType;
            }
            if (position == avatarCornersPreviewRow) {
                return TYPE_AVATAR_CORNERS;
            } else if (position == chatListPreviewRow) {
                return TYPE_CHAT_LIST;
            } else if (position == foldersPreviewRow) {
                return TYPE_FOLDERS;
            } else if (position == sectionRadiusRow) {
                return TYPE_SECTION_SLIDER;
            } else if (position == appearanceHeaderRow || position == sectionsHeaderRow
                    || position == blurHeaderRow || position == chatListHeaderRow
                    || position == foldersHeaderRow || position == linksHeaderRow
                    || position == headerHeaderRow) {
                return TYPE_HEADER;
            } else if (position == appearanceDividerRow || position == sectionsDividerRow
                    || position == blurDividerRow || position == avatarsDividerRow
                    || position == chatListDividerRow || position == foldersDividerRow
                    || position == linksDividerRow || position == headerDividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == fabShapeRow) {
                return TYPE_FAB_SHAPE;
            } else if (position == appNavigationRow || position == iconPacksRow
                    || position == emojiSetsRow || position == pillStackRow || position == playerStyleRow || position == hidingRow) {
                return TYPE_TEXT;
            } else if (position == md3GroupRow || position == iosGroupRow) {
                return TYPE_EXPANDABLE_SWITCH;
            } else if (position == md3LoadingRow || position == md3SliderRow
                    || position == md3SwitchRow || position == md3ChatHeaderRow
                    || position == md3NavBarRow || position == md3ListItemsRow
                    || position == md3PlayerRow || position == md3MiniPlayerRow
                    || position == md3SurfacesRow || position == md3DialogsRow || position == md3BarsRow
                    || position == md3InputRow
                    || position == iosNavBarRow || position == iosChatHeaderRow) {
                return TYPE_ROUND_CHECK;
            } else if (position == dividerStyleRow || position == glassOutlineRow
                    || position == headerStyleRow || position == searchStyleRow
                    || position == tabTitleStyleRow
                    || position == startupFolderRow
                    || position == tabCounterRow || position == titleTextRow) {
                return TYPE_SETTINGS;
            }
            return TYPE_CHECK;
        }
    }

    /** Сколько стилей MD3 включено. Счётчик «N/8» рядом с шевроном. */
    private static final int MD3_STYLE_COUNT = 12;
    /** Значение селектора MZGram, соответствующее Material Design 3. */
    private static final int STYLE_MD3 = 2;

    private static final int SWITCH_STYLE_M3 = org.telegram.ui.Components.Switch.SWITCH_STYLE_M3;

    private static boolean isMd3(int styleValue) {
        return styleValue == STYLE_MD3;
    }

    private static boolean isMd3Switch(int styleValue) {
        return styleValue == STYLE_MD3 || styleValue == SWITCH_STYLE_M3;
    }

    private int md3SelectedCount() {
        int n = 0;
        if (AppearanceConfig.newLoadingStyle.Bool()) n++;
        if (isMd3(ExtConfig.INSTANCE.getSliderStyle().Int())) n++;
        if (isMd3Switch(ExtConfig.INSTANCE.getSwitchStyle().Int())) n++;
        if (AppearanceConfig.newChatHeaderStyle.Bool()) n++;
        if (md3NavBarShown()) n++;
        if (AppearanceConfig.m3ListItems.Bool()) n++;
        if (AppearanceConfig.md3Player.Bool()) n++;
        if (AppearanceConfig.md3MiniPlayer.Bool()) n++;
        if (AppearanceConfig.materialSurfaces.Bool()) n++;
        if (AppearanceConfig.materialDialogs.Bool()) n++;
        if (AppearanceConfig.materialBars.Bool()) n++;
        if (AppearanceConfig.materialInput.Bool()) n++;
        return n;
    }

    private void onM3ListItemsChanged() {
        AppearanceConfig.invalidateDividerStyle();
        Theme.applyCommonTheme();
        if (listAdapter != null && separateHeadersRow >= 0) {
            listAdapter.notifyItemChanged(separateHeadersRow);
        }
        if (listView != null) {
            listView.invalidateItemDecorations();
        }
    }

    /**
     * Клик по мастер-переключателю группы: если включено хоть что-то — гасим всё,
     * иначе включаем всё. Так же ведёт себя handleMD3StylesSwitchClick MZGram.
     */
    private void toggleAllMd3StylesFromCell() {
        toggleAllMd3Styles();
    }

    /**
     * MZGram: the Material 3 bar as the user sees it. Choosing the floating bar keeps the Material 3 setting (so "Show"
     * brings that bar back), but while the floating bar is on screen the switch reads off, and turning it on leaves the
     * floating bar, as before.
     */
    private static boolean md3NavBarShown() {
        return AppearanceConfig.newNavigationBarStyle.Bool() && !MainTabsLayout.isBottomNavigationFloating();
    }

    private static void leaveFloatingBottomNavigation() {
        if (MainTabsLayout.isBottomNavigationFloating()) {
            MainTabsLayout.setBottomNavigationMode(MainTabsLayout.BOTTOM_NAVIGATION_MODE_SHOW);
        }
    }

    private static final int IOS_STYLE_COUNT = 2;

    private int iosSelectedCount() {
        int n = 0;
        if (AppearanceConfig.iosNavigationBarStyle.Bool()) n++;
        if (AppearanceConfig.iosChatHeader.Bool()) n++;
        return n;
    }

    private void toggleAllIosStyles() {
        boolean enable = iosSelectedCount() == 0;
        AppearanceConfig.iosNavigationBarStyle.setConfigBool(enable);
        AppearanceConfig.iosChatHeader.setConfigBool(enable);
        if (enable) {
            AppearanceConfig.newNavigationBarStyle.setConfigBool(false);
        }
        rebuildAll();
        rebuildRowsAndNotify();
    }

    private void toggleAllMd3Styles() {
        boolean enable = md3SelectedCount() == 0;
        AppearanceConfig.newLoadingStyle.setConfigBool(enable);
        AppearanceConfig.newChatHeaderStyle.setConfigBool(enable);
        AppearanceConfig.newNavigationBarStyle.setConfigBool(enable);
        AppearanceConfig.m3ListItems.setConfigBool(enable);
        AppearanceConfig.md3Player.setConfigBool(enable);
        AppearanceConfig.md3MiniPlayer.setConfigBool(enable);
        AppearanceConfig.materialSurfaces.setConfigBool(enable);
        AppearanceConfig.materialDialogs.setConfigBool(enable);
        AppearanceConfig.materialBars.setConfigBool(enable);
        AppearanceConfig.materialInput.setConfigBool(enable);
        onM3ListItemsChanged();
        if (enable) {
            AppearanceConfig.iosNavigationBarStyle.setConfigBool(false);
            leaveFloatingBottomNavigation();
        }
        ExtConfig.INSTANCE.getSliderStyle().setConfigInt(enable ? STYLE_MD3 : 0);
        ExtConfig.INSTANCE.getSwitchStyle().setConfigInt(enable ? SWITCH_STYLE_M3 : 0);
        if (avatarCornersPreviewCell != null) {
            avatarCornersPreviewCell.invalidate();
        }
        // Most styles are read at draw time; glass and blur need a restart.
        rebuildAll();
        rebuildRowsAndNotify();
        showRestartHint();
    }

    private static int clamp(int value, int size) {
        if (value < 0) return 0;
        if (value >= size) return size - 1;
        return value;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.runOnUIThread(this::invalidatePreviews);
        if (foldersPreviewCell != null) {
            foldersPreviewCell.updateAllChatsTabName(false);
        }
        if (listAdapter != null && emojiSetsRow >= 0) {
            listAdapter.notifyItemChanged(emojiSetsRow);
        }
    }

    @Override
    public void onFragmentDestroy() {
        // Отложенная пересборка после слайдера радиуса переживёт закрытие экрана, если её не снять.
        AndroidUtilities.cancelRunOnUIThread(sectionRadiusRebuild);
        super.onFragmentDestroy();
    }
}
