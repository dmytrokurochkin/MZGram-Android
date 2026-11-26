package org.mzgram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.mzgram.arc.database.ArcData;
import org.mzgram.arc.messages.ArcMessagesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Locale;

import org.mzgram.backup.ArcDatabaseImport;
import org.mzgram.drawer.MainMenuItem;
import org.mzgram.drawer.MainMenuLayout;
import org.mzgram.pillstack.PillStackConfig;
import org.mzgram.pillstack.PillType;
import org.mzgram.core.CoreConfig;
import org.mzgram.core.config.ConfigItem;
import org.mzgram.core.filters.RegexFiltersSettingActivity;
import org.mzgram.core.settings.BaseCoreSettingsActivity;
import org.mzgram.core.settings.GhostModeActivity;
import org.mzgram.core.ui.cells.HeaderCell;
import org.mzgram.ext.ExtConfig;

public class MZGramArcMomentsActivity extends BaseCoreSettingsActivity {

    private static final int TYPE_EXPANDABLE_SWITCH = 104;
    private static final int TYPE_ROUND_CHECK = 105;
    private static final int SAVE_MEDIA_TOTAL = 5;
    private static final int DATABASE_IMPORT_REQUEST_CODE = 41;

    /** Булевы функции секции; режим призрака гасится отдельно — он не один ConfigItem. */
    private static final ConfigItem[] ARC_FEATURE_CONFIGS = {
            ExtConfig.INSTANCE.getRegexFiltersEnabled(),
            ExtConfig.INSTANCE.getSaveLocalLastSeen(),
            ExtConfig.INSTANCE.getSaveReadDate(),
            ExtConfig.INSTANCE.getEnableSaveDeletedMessages(),
            ExtConfig.INSTANCE.getEnableSaveEditsHistory(),
            ExtConfig.INSTANCE.getMessageSavingSaveMedia(),
            ExtConfig.INSTANCE.getSaveDeletedMessageForBotUser(),
            ExtConfig.INSTANCE.getSaveDeletedMessageForBot(),
            ExtConfig.INSTANCE.getTranslucentDeletedMessages(),
            ExtConfig.INSTANCE.getUseDeletedIcon(),
            ExtConfig.INSTANCE.getForwardProtectedAsCopy(),
            ExtConfig.INSTANCE.getAskBeforeOpeningStory(),
    };

    private int headerRow;
    private int ghostRow;
    private int askStoryRow;
    private int ghostDividerRow;
    private int savingHeaderRow;
    private int savingDividerRow;
    private int deletedHeaderRow;
    private int deletedDividerRow;
    private int otherHeaderRow;
    private int otherDividerRow;
    private int dangerHeaderRow;
    private int regexRow;
    private int saveLastSeenRow;
    private int saveReadDateRow;
    private int saveDeletedRow;
    private int saveEditsRow;
    private int deletedExceptionsModeRow;
    private int editedExceptionsModeRow;
    private int saveInArchivedRow;
    private int saveMediaRow;
    private int saveMediaPrivateChatsRow;
    private int saveMediaPublicChannelsRow;
    private int saveMediaPrivateChannelsRow;
    private int saveMediaPublicGroupsRow;
    private int saveMediaPrivateGroupsRow;
    private boolean saveMediaExpanded;
    private int botUserRow;
    private int botChatRow;
    private int saveDeletedPrivateRow;
    private int saveDeletedGroupsRow;
    private int saveDeletedChannelsRow;
    private int replyToDeletedRow;
    private int translucentRow;
    private int deletedIconRow;
    private int deletedMarkRow;
    private int deletedMarkColorRow;
    private int forwardProtectedRow;
    private int exportDbRow;
    private int importDbRow;
    private int disableAllRow;
    private int clearDbRow;
    private int dividerRow;

    public MZGramArcMomentsActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        ArcData.loadSizes(this::refreshArcDataSize);
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        headerRow = addRow("arcHeader");
        ghostRow = addRow("arcGhost");
        askStoryRow = addRow(ExtConfig.INSTANCE.getAskBeforeOpeningStory().getKey());
        ghostDividerRow = addRow();

        boolean saveDeleted = ExtConfig.INSTANCE.getEnableSaveDeletedMessages().Bool();
        boolean saveEdits = ExtConfig.INSTANCE.getEnableSaveEditsHistory().Bool();
        saveMediaRow = botUserRow = botChatRow = translucentRow = -1;
        deletedExceptionsModeRow = editedExceptionsModeRow = -1;
        saveDeletedPrivateRow = saveDeletedGroupsRow = saveDeletedChannelsRow = -1;
        replyToDeletedRow = deletedIconRow = deletedMarkRow = deletedMarkColorRow = -1;
        saveMediaPrivateChatsRow = saveMediaPublicChannelsRow = saveMediaPrivateChannelsRow = -1;
        saveMediaPublicGroupsRow = saveMediaPrivateGroupsRow = -1;
        deletedHeaderRow = deletedDividerRow = -1;

        savingHeaderRow = addRow("arcSavingHeader");
        saveDeletedRow = addRow(ExtConfig.INSTANCE.getEnableSaveDeletedMessages().getKey());
        if (saveDeleted) {
            deletedExceptionsModeRow = addRow(ExtConfig.INSTANCE.getSaveDeletedExceptionsMode().getKey());
        }
        saveEditsRow = addRow(ExtConfig.INSTANCE.getEnableSaveEditsHistory().getKey());
        if (saveEdits) {
            editedExceptionsModeRow = addRow(ExtConfig.INSTANCE.getSaveEditedExceptionsMode().getKey());
        }
        saveInArchivedRow = addRow("saveInArchived");
        if (saveDeleted) {
            saveMediaRow = addRow(ExtConfig.INSTANCE.getMessageSavingSaveMedia().getKey());
            if (saveMediaExpanded) {
                saveMediaPrivateChatsRow = addRow();
                saveMediaPublicChannelsRow = addRow();
                saveMediaPrivateChannelsRow = addRow();
                saveMediaPublicGroupsRow = addRow();
                saveMediaPrivateGroupsRow = addRow();
            }
            saveDeletedPrivateRow = addRow(ExtConfig.INSTANCE.getSaveDeletedInPrivateChats().getKey());
            saveDeletedGroupsRow = addRow(ExtConfig.INSTANCE.getSaveDeletedInGroups().getKey());
            saveDeletedChannelsRow = addRow(ExtConfig.INSTANCE.getSaveDeletedInChannels().getKey());
            botUserRow = addRow(ExtConfig.INSTANCE.getSaveDeletedMessageForBotUser().getKey());
            if (ExtConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool()) {
                botChatRow = addRow(ExtConfig.INSTANCE.getSaveDeletedMessageForBot().getKey());
            }
        }
        saveLastSeenRow = addRow(ExtConfig.INSTANCE.getSaveLocalLastSeen().getKey());
        saveReadDateRow = addRow(ExtConfig.INSTANCE.getSaveReadDate().getKey());
        savingDividerRow = addRow();

        if (saveDeleted) {
            deletedHeaderRow = addRow("arcDeletedHeader");
            replyToDeletedRow = addRow(ExtConfig.INSTANCE.getReplyToDeletedAsQuote().getKey());
            translucentRow = addRow(ExtConfig.INSTANCE.getTranslucentDeletedMessages().getKey());
            deletedIconRow = addRow(ExtConfig.INSTANCE.getUseDeletedIcon().getKey());
            if (!ExtConfig.INSTANCE.getUseDeletedIcon().Bool()) {
                deletedMarkRow = addRow(ExtConfig.INSTANCE.getCustomDeletedMark().getKey());
            }
            deletedMarkColorRow = addRow(ExtConfig.INSTANCE.getDeletedMarkColor().getKey());
            deletedDividerRow = addRow();
        }

        otherHeaderRow = addRow("arcOtherHeader");
        regexRow = addRow(ExtConfig.INSTANCE.getRegexFiltersEnabled().getKey());
        forwardProtectedRow = addRow(ExtConfig.INSTANCE.getForwardProtectedAsCopy().getKey());
        exportDbRow = addRow("databaseExport");
        importDbRow = addRow("databaseImport");
        otherDividerRow = addRow();

        dangerHeaderRow = addRow("arcDangerHeader");
        disableAllRow = addRow("arcDisableAll");
        clearDbRow = addRow("arcClearDatabase");
        dividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MZGeneralArcMoments);
    }

    @Override
    public int getSearchGuid() {
        return 27000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.arc_ghost;
    }

    @Override
    public String getSearchPrefix() {
        return "MZArc";
    }

    @Override
    protected String getKey() {
        return "mzgram_arcmoments";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == ghostRow) {
            presentFragment(new GhostModeActivity());
        } else if (position == askStoryRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getAskBeforeOpeningStory(), false);
        } else if (position == regexRow) {
            // Как в эталоне: тап по тексту ведёт в список фильтров, тап по переключателю — включает.
            boolean onSwitch = LocaleController.isRTL
                    ? x <= AndroidUtilities.dp(76)
                    : x >= view.getMeasuredWidth() - AndroidUtilities.dp(76);
            if (onSwitch) {
                toggleArcConfig(view, ExtConfig.INSTANCE.getRegexFiltersEnabled(), false);
            } else {
                presentFragment(new RegexFiltersSettingActivity());
            }
        } else if (position == saveLastSeenRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getSaveLocalLastSeen(), false);
        } else if (position == saveReadDateRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getSaveReadDate(), false);
        } else if (position == saveDeletedRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getEnableSaveDeletedMessages(), true);
        } else if (position == deletedExceptionsModeRow) {
            showSaveExceptionsModeDialog(ExtConfig.INSTANCE.getSaveDeletedExceptionsMode(), deletedExceptionsModeRow);
        } else if (position == saveEditsRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getEnableSaveEditsHistory(), true);
        } else if (position == editedExceptionsModeRow) {
            showSaveExceptionsModeDialog(ExtConfig.INSTANCE.getSaveEditedExceptionsMode(), editedExceptionsModeRow);
        } else if (position == saveInArchivedRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getSaveInArchivedChats(), false);
        } else if (position == saveMediaRow) {
            saveMediaExpanded = !saveMediaExpanded;
            rebuildRowsAndNotify();
        } else if (position == saveMediaPrivateChatsRow) {
            toggleSaveMediaKind(view, ExtConfig.INSTANCE.getSaveMediaInPrivateChats());
        } else if (position == saveMediaPublicChannelsRow) {
            toggleSaveMediaKind(view, ExtConfig.INSTANCE.getSaveMediaInPublicChannels());
        } else if (position == saveMediaPrivateChannelsRow) {
            toggleSaveMediaKind(view, ExtConfig.INSTANCE.getSaveMediaInPrivateChannels());
        } else if (position == saveMediaPublicGroupsRow) {
            toggleSaveMediaKind(view, ExtConfig.INSTANCE.getSaveMediaInPublicGroups());
        } else if (position == saveMediaPrivateGroupsRow) {
            toggleSaveMediaKind(view, ExtConfig.INSTANCE.getSaveMediaInPrivateGroups());
        } else if (position == botUserRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getSaveDeletedMessageForBotUser(), true);
        } else if (position == botChatRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getSaveDeletedMessageForBot(), false);
        } else if (position == saveDeletedPrivateRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getSaveDeletedInPrivateChats(), false);
        } else if (position == saveDeletedGroupsRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getSaveDeletedInGroups(), false);
        } else if (position == saveDeletedChannelsRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getSaveDeletedInChannels(), false);
        } else if (position == replyToDeletedRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getReplyToDeletedAsQuote(), false);
        } else if (position == translucentRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getTranslucentDeletedMessages(), false);
        } else if (position == deletedIconRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getUseDeletedIcon(), true);
        } else if (position == deletedMarkRow) {
            showDeletedMarkDialog();
        } else if (position == deletedMarkColorRow) {
            showDeletedMarkColorPicker();
        } else if (position == forwardProtectedRow) {
            toggleArcConfig(view, ExtConfig.INSTANCE.getForwardProtectedAsCopy(), false);
        } else if (position == exportDbRow) {
            ArcData.exportArcDatabase(this);
        } else if (position == importDbRow) {
            openDatabasePicker();
        } else if (position == disableAllRow) {
            showDisableAllDialog();
        } else if (position == clearDbRow) {
            showClearArcDatabaseDialog();
        }
    }

    /**
     * Убирает быстрые переключатели призрака. Раскладку меню трогаем только если она
     * уже своя: пункта нет в раскладке по умолчанию, а запись без нужды сделала бы
     * её кастомной и заморозила текущий состав.
     */
    private void removeGhostShortcuts() {
        PillStackConfig.setPillActive(PillType.GHOST.id, false);
        PillStackConfig.savePillsLayout();

        final int ghostId = MainMenuItem.GHOST_MODE.getId();
        final ArrayList<Integer> layout = MainMenuLayout.getLayoutMutable();
        if (layout.remove((Integer) ghostId)) {
            final ArrayList<Integer> hidden = MainMenuLayout.getHiddenItemsMutable();
            if (!hidden.contains(ghostId)) {
                hidden.add(ghostId);
            }
            MainMenuLayout.save(layout, hidden);
        }
    }

    private void openDatabasePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, DATABASE_IMPORT_REQUEST_CODE);
        } catch (Exception e) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ErrorOccurred)).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != DATABASE_IMPORT_REQUEST_CODE) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            return;
        }
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            ArcDatabaseImport.importFromUri(this, data.getData());
        }
    }

    private void showDisableAllDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context, getResourceProvider())
                .setTitle(getString(R.string.MZGeneralArcMomentsDisableAll))
                .setMessage(getString(R.string.AreYouSure))
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> disableAllFeatures())
                .setNegativeButton(getString(R.string.Cancel), (d, w) -> d.dismiss())
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
    }

    private void disableAllFeatures() {
        for (ConfigItem config : ARC_FEATURE_CONFIGS) {
            if (config.Bool()) {
                config.setConfigBool(false);
            }
        }
        // Через toggle, а не setGhostMode: он же отправляет пакет онлайна,
        // без которого мы останемся невидимыми уже без своего ведома.
        if (CoreConfig.isGhostModeActive()) {
            CoreConfig.toggleGhostMode();
            NotificationCenter.getInstance(currentAccount)
                    .postNotificationName(NotificationCenter.mainUserInfoChanged);
        }
        removeGhostShortcuts();
        rebuildRowsAndNotify();
    }

    private void rebuildRowsAndNotify() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    /** Заголовок берётся из ключа настройки — так же, как это делают ячейки MZGram. */
    private void bindArcCheck(TextCheckCell cell, ConfigItem config, boolean divider) {
        cell.setTextAndCheck(getString(config.getKey()), config.Bool(), divider);
    }

    /** Переключает пункт ArcMoments; rebuild нужен там, где от него зависит состав строк. */
    private void toggleArcConfig(View view, ConfigItem config, boolean affectsRows) {
        boolean enabled = config.toggleConfigBool();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(enabled);
        }
        if (affectsRows) {
            rebuildRowsAndNotify();
        }
    }

    private void toggleSaveMediaKind(View view, ConfigItem config) {
        boolean enabled = config.toggleConfigBool();
        if (view instanceof CheckBoxCell) {
            ((CheckBoxCell) view).setChecked(enabled, true);
        }
        if (listAdapter != null && saveMediaRow >= 0) {
            listAdapter.notifyItemChanged(saveMediaRow);
        }
    }

    private ConfigItem[] saveMediaKinds() {
        return new ConfigItem[]{
                ExtConfig.INSTANCE.getSaveMediaInPrivateChats(),
                ExtConfig.INSTANCE.getSaveMediaInPublicChannels(),
                ExtConfig.INSTANCE.getSaveMediaInPrivateChannels(),
                ExtConfig.INSTANCE.getSaveMediaInPublicGroups(),
                ExtConfig.INSTANCE.getSaveMediaInPrivateGroups(),
        };
    }

    private int saveMediaSelectedCount() {
        int selected = 0;
        for (ConfigItem config : saveMediaKinds()) {
            if (config.Bool()) {
                selected++;
            }
        }
        return selected;
    }

    private void toggleSaveMedia() {
        ConfigItem master = ExtConfig.INSTANCE.getMessageSavingSaveMedia();
        boolean enable = !master.Bool();
        master.setConfigBool(enable);
        if (enable && saveMediaSelectedCount() == 0) {
            for (ConfigItem config : saveMediaKinds()) {
                config.setConfigBool(true);
            }
        }
        rebuildRowsAndNotify();
    }

    private static String ratio(int selected, int total) {
        return String.format(Locale.getDefault(), "%d/%d", selected, total);
    }

    private void refreshArcDataSize() {
        if (clearDbRow >= 0 && listAdapter != null) {
            listAdapter.notifyItemChanged(clearDbRow);
        }
    }

    private void showSaveExceptionsModeDialog(ConfigItem config, int row) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        String[] modes = {
                getString(R.string.SaveExceptionsAllExceptSelected),
                getString(R.string.SaveExceptionsOnlySelected)
        };
        showDialog(org.mzgram.settings.utils.MZChoice.create(getParentActivity(), getString(config.getKey()), modes,
                config.Int() == 1 ? 1 : 0, (dialog, which) -> {
                    config.setConfigInt(which);
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(row);
                    }
                }));
    }

    private String getSaveExceptionsModeValue(ConfigItem config) {
        return getString(config.Int() == 1 ? R.string.SaveExceptionsOnlySelected : R.string.SaveExceptionsAllExceptSelected);
    }

    private void showDeletedMarkDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        ConfigItem config = ExtConfig.INSTANCE.getCustomDeletedMark();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(config.getKey()));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
        editText.setFocusable(true);
        editText.setBackground(null);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        editText.setPadding(0, 0, 0, AndroidUtilities.dp(6));
        editText.setText(config.String());
        editText.requestFocus();
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(10), 0));

        builder.setPositiveButton(getString(R.string.OK), null);
        builder.setView(linearLayout);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            config.setConfigString(editText.getText().toString());
            dialog.dismiss();
            if (listAdapter != null && deletedMarkRow >= 0) {
                listAdapter.notifyItemChanged(deletedMarkRow);
            }
        }));
        showDialog(dialog);
    }

    private void showDeletedMarkColorPicker() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        ColorPicker colorPicker = new ColorPicker(context, false, (color, num, applyNow) ->
                ExtConfig.INSTANCE.getDeletedMarkColor().setConfigInt(color)) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
            }
        };
        int color = ExtConfig.INSTANCE.getDeletedMarkColor().Int();
        colorPicker.setColor(color == 0 ? getThemedColor(Theme.key_chat_messageTextIn) : color, 0);
        colorPicker.setType(-1, true, 1, 1, false, 0, false);
        BottomSheet bottomSheet = new BottomSheet(context, false);
        LinearLayout sheetLayout = new LinearLayout(context);
        sheetLayout.setOrientation(LinearLayout.VERTICAL);
        sheetLayout.addView(colorPicker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView resetButton = new TextView(context);
        resetButton.setText(getString(R.string.DeletedMarkColorDefault));
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        resetButton.setTypeface(AndroidUtilities.bold());
        resetButton.setGravity(Gravity.CENTER);
        resetButton.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        resetButton.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4), 0.12f), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        resetButton.setOnClickListener(v -> {
            ExtConfig.INSTANCE.getDeletedMarkColor().setConfigInt(0);
            bottomSheet.dismiss();
        });
        sheetLayout.addView(resetButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 8));
        bottomSheet.setCustomView(sheetLayout);
        bottomSheet.setOnDismissListener(dialog -> {
            if (listAdapter != null && deletedMarkColorRow >= 0) {
                listAdapter.notifyItemChanged(deletedMarkColorRow);
            }
        });
        bottomSheet.show();
    }

    private String getDeletedMarkColorValue() {
        int color = ExtConfig.INSTANCE.getDeletedMarkColor().Int();
        return color == 0
                ? getString(R.string.DeletedMarkColorDefault)
                : String.format(Locale.US, "#%08X", color);
    }

    private void showClearArcDatabaseDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context, getResourceProvider())
                .setTitle(getString(R.string.ClearMessageDatabase))
                .setMessage(getString(R.string.AreYouSure))
                .setPositiveButton(getString(R.string.Clear), (dialog, which) -> {
                    AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.setCanCancel(false);
                    progressDialog.show();
                    Utilities.globalQueue.postRunnable(() -> {
                        ArcMessagesController.getInstance().clean();
                        AndroidUtilities.runOnUIThread(() -> {
                            progressDialog.dismiss();
                            BulletinFactory.of(this)
                                    .createSimpleBulletin(R.raw.done, getString(R.string.ClearMessageDatabaseNotification))
                                    .show();
                        });
                        ArcData.loadSizes(this::refreshArcDataSize);
                    });
                })
                .setNegativeButton(getString(R.string.Cancel), (d, w) -> d.dismiss())
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
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
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_ROUND_CHECK: {
                    CheckBoxCell checkBoxCell = new CheckBoxCell(mContext, 4, 21, resourcesProvider);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked,
                            Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    view = checkBoxCell;
                    break;
                }
                default:
                    return super.onCreateViewHolder(parent, viewType);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == TYPE_EXPANDABLE_SWITCH || type == TYPE_ROUND_CHECK) {
                return true;
            }
            return super.isEnabled(holder);
        }

        @Override
        protected boolean isSectionContent(int viewType) {
            if (viewType == TYPE_EXPANDABLE_SWITCH || viewType == TYPE_ROUND_CHECK) {
                return true;
            }
            return super.isSectionContent(viewType);
        }

        private void bindSaveMediaGroup(TextCheckCell2 cell) {
            cell.useStandardSwitchColors();
            int selected = saveMediaSelectedCount();
            cell.setTextAndCheck(getString(ExtConfig.INSTANCE.getMessageSavingSaveMedia().getKey()),
                    ExtConfig.INSTANCE.getMessageSavingSaveMedia().Bool(), saveMediaExpanded);
            cell.setCollapseArrow(ratio(selected, SAVE_MEDIA_TOTAL), !saveMediaExpanded,
                    MZGramArcMomentsActivity.this::toggleSaveMedia);
        }

        private void bindSaveMediaKind(CheckBoxCell cell, int position) {
            if (position == saveMediaPrivateChatsRow) {
                cell.setText(getString(R.string.MessageSavingSaveMediaInPrivateChats), "",
                        ExtConfig.INSTANCE.getSaveMediaInPrivateChats().Bool(), true, true);
            } else if (position == saveMediaPublicChannelsRow) {
                cell.setText(getString(R.string.MessageSavingSaveMediaInPublicChannels), "",
                        ExtConfig.INSTANCE.getSaveMediaInPublicChannels().Bool(), true, true);
            } else if (position == saveMediaPrivateChannelsRow) {
                cell.setText(getString(R.string.MessageSavingSaveMediaInPrivateChannels), "",
                        ExtConfig.INSTANCE.getSaveMediaInPrivateChannels().Bool(), true, true);
            } else if (position == saveMediaPublicGroupsRow) {
                cell.setText(getString(R.string.MessageSavingSaveMediaInPublicGroups), "",
                        ExtConfig.INSTANCE.getSaveMediaInPublicGroups().Bool(), true, true);
            } else if (position == saveMediaPrivateGroupsRow) {
                cell.setText(getString(R.string.MessageSavingSaveMediaInPrivateGroups), "",
                        ExtConfig.INSTANCE.getSaveMediaInPrivateGroups().Bool(), true, true);
            }
            cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_EXPANDABLE_SWITCH:
                    bindSaveMediaGroup((TextCheckCell2) holder.itemView);
                    break;
                case TYPE_ROUND_CHECK:
                    bindSaveMediaKind((CheckBoxCell) holder.itemView, position);
                    break;
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        cell.setText(getString(R.string.MZArcGhostHeader));
                    } else if (position == savingHeaderRow) {
                        cell.setText(getString(R.string.MZArcSavingHeader));
                    } else if (position == deletedHeaderRow) {
                        cell.setText(getString(R.string.MZArcDeletedHeader));
                    } else if (position == otherHeaderRow) {
                        cell.setText(getString(R.string.MZArcOtherHeader));
                    } else if (position == dangerHeaderRow) {
                        cell.setText(getString(R.string.MZArcDangerHeader));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setEnabled(true, null);
                    cell.setIcon(0);
                    if (position == askStoryRow) {
                        cell.setTextAndValueAndCheck(
                                getString(R.string.AskBeforeOpeningStory),
                                getString(R.string.AskBeforeOpeningStoryInfo),
                                ExtConfig.INSTANCE.getAskBeforeOpeningStory().Bool(), true, false);
                    } else if (position == regexRow) {
                        cell.setTextAndValueAndCheck(
                                getString(ExtConfig.INSTANCE.getRegexFiltersEnabled().getKey()),
                                getString(R.string.RegexFiltersNotice),
                                ExtConfig.INSTANCE.getRegexFiltersEnabled().Bool(), true, true);
                    } else if (position == saveLastSeenRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getSaveLocalLastSeen(), true);
                    } else if (position == saveReadDateRow) {
                        cell.setTextAndValueAndCheck(
                                getString(R.string.MZArcSaveReadDate),
                                getString(R.string.MZArcSaveReadDateInfo),
                                ExtConfig.INSTANCE.getSaveReadDate().Bool(), true, false);
                    } else if (position == saveDeletedRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getEnableSaveDeletedMessages(), true);
                    } else if (position == saveEditsRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getEnableSaveEditsHistory(), true);
                    } else if (position == saveInArchivedRow) {
                        cell.setTextAndValueAndCheck(
                                getString(R.string.MZArcSaveInArchived),
                                getString(R.string.MZArcSaveInArchivedInfo),
                                ExtConfig.INSTANCE.getSaveInArchivedChats().Bool(), true, true);
                    } else if (position == botUserRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getSaveDeletedMessageForBotUser(), true);
                    } else if (position == botChatRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getSaveDeletedMessageForBot(), true);
                    } else if (position == saveDeletedPrivateRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getSaveDeletedInPrivateChats(), true);
                    } else if (position == saveDeletedGroupsRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getSaveDeletedInGroups(), true);
                    } else if (position == saveDeletedChannelsRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getSaveDeletedInChannels(), true);
                    } else if (position == replyToDeletedRow) {
                        cell.setTextAndValueAndCheck(
                                getString(R.string.ReplyToDeletedAsQuote),
                                getString(R.string.ReplyToDeletedAsQuoteInfo),
                                ExtConfig.INSTANCE.getReplyToDeletedAsQuote().Bool(), true, true);
                    } else if (position == translucentRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getTranslucentDeletedMessages(), true);
                    } else if (position == deletedIconRow) {
                        bindArcCheck(cell, ExtConfig.INSTANCE.getUseDeletedIcon(), deletedMarkColorRow != -1);
                    } else if (position == forwardProtectedRow) {
                        cell.setTextAndValueAndCheck(
                                getString(R.string.ForwardProtectedAsCopy),
                                getString(R.string.ForwardProtectedAsCopyInfo),
                                ExtConfig.INSTANCE.getForwardProtectedAsCopy().Bool(), true, true);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == ghostRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.GhostMode), R.drawable.arc_ghost, true);
                    } else if (position == exportDbRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.MZArcDatabaseExport), R.drawable.msg_share, true);
                    } else if (position == importDbRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.MZArcDatabaseImport), R.drawable.msg_download, false);
                    } else if (position == disableAllRow) {
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedBold);
                        cell.setTextAndIcon(getString(R.string.MZGeneralArcMomentsDisableAll), R.drawable.msg_block, true);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == deletedExceptionsModeRow || position == editedExceptionsModeRow) {
                        ConfigItem config = position == deletedExceptionsModeRow
                                ? ExtConfig.INSTANCE.getSaveDeletedExceptionsMode()
                                : ExtConfig.INSTANCE.getSaveEditedExceptionsMode();
                        cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(getString(config.getKey()), getSaveExceptionsModeValue(config), true);
                    } else if (position == deletedMarkRow) {
                        cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(getString(ExtConfig.INSTANCE.getCustomDeletedMark().getKey()),
                                ExtConfig.INSTANCE.getCustomDeletedMark().String(), true);
                    } else if (position == deletedMarkColorRow) {
                        cell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                        cell.setTextAndValue(getString(ExtConfig.INSTANCE.getDeletedMarkColor().getKey()),
                                getDeletedMarkColorValue(), false);
                    } else if (position == clearDbRow) {
                        cell.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                        cell.setTextAndValue(getString(R.string.ClearMessageDatabase),
                                ArcData.totalSize > 0 ? AndroidUtilities.formatFileSize(ArcData.totalSize) : "...", false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == dividerRow) {
                        cell.setText(getString(R.string.MZGeneralArcMomentsInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow || position == savingHeaderRow || position == deletedHeaderRow
                    || position == otherHeaderRow || position == dangerHeaderRow) {
                return TYPE_HEADER;
            } else if (position == ghostDividerRow || position == savingDividerRow
                    || position == deletedDividerRow || position == otherDividerRow) {
                return TYPE_SHADOW;
            } else if (position == dividerRow) {
                return TYPE_INFO_PRIVACY;
            } else if (position == ghostRow || position == disableAllRow
                    || position == exportDbRow || position == importDbRow) {
                return TYPE_TEXT;
            } else if (position == deletedExceptionsModeRow || position == editedExceptionsModeRow
                    || position == deletedMarkRow || position == deletedMarkColorRow || position == clearDbRow) {
                return TYPE_SETTINGS;
            } else if (position == saveMediaRow) {
                return TYPE_EXPANDABLE_SWITCH;
            } else if (position == saveMediaPrivateChatsRow || position == saveMediaPublicChannelsRow
                    || position == saveMediaPrivateChannelsRow || position == saveMediaPublicGroupsRow
                    || position == saveMediaPrivateGroupsRow) {
                return TYPE_ROUND_CHECK;
            }
            return TYPE_CHECK;
        }
    }
}
