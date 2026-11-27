package org.mzgram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import org.mzgram.glyph.GlyphConfig;
import org.mzgram.glyph.GlyphController;
import org.mzgram.core.settings.BaseCoreSettingsActivity;
import org.mzgram.core.ui.cells.HeaderCell;

public class MZGramGlyphActivity extends BaseCoreSettingsActivity {

    private int glyphHeaderRow;
    private int glyphEnableRow;
    private int glyphNewMessageRow;
    private int glyphCallsRow;
    private int glyphRecordingRow;
    private int glyphScreenOffRow;
    private int glyphPreviewRow;
    private int glyphDividerRow;

    public MZGramGlyphActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        GlyphConfig.init();
        return super.onFragmentCreate();
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        glyphHeaderRow = addRow("glyphHeader");
        glyphEnableRow = addRow(GlyphConfig.enabled.getKey());
        glyphNewMessageRow = addRow(GlyphConfig.onNewMessage.getKey());
        glyphCallsRow = addRow(GlyphConfig.onCall.getKey());
        glyphRecordingRow = addRow(GlyphConfig.onRecording.getKey());
        glyphScreenOffRow = addRow(GlyphConfig.screenOffOnly.getKey());
        glyphPreviewRow = addRow("glyphPreview");
        glyphDividerRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MZGlyphTitle);
    }

    @Override
    public int getSearchGuid() {
        return 25000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.deproko_baseline_lamp_filled_24;
    }

    @Override
    public String getSearchPrefix() {
        return "MZGlyph";
    }

    @Override
    protected String getKey() {
        return "mzgram_glyph";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == glyphEnableRow) {
            boolean enabled = GlyphConfig.enabled.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
            if (enabled) {
                GlyphController.getInstance().init();
            } else {
                GlyphController.getInstance().shutdown();
            }
        } else if (position == glyphNewMessageRow) {
            boolean enabled = GlyphConfig.onNewMessage.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == glyphCallsRow) {
            boolean enabled = GlyphConfig.onCall.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == glyphRecordingRow) {
            boolean enabled = GlyphConfig.onRecording.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == glyphScreenOffRow) {
            boolean enabled = GlyphConfig.screenOffOnly.toggleConfigBool();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(enabled);
            }
        } else if (position == glyphPreviewRow) {
            if (!GlyphController.getInstance().isSupported()) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, getString(R.string.MZGlyphUnsupported))
                        .show();
                return;
            }
            GlyphController.getInstance().preview();
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == glyphHeaderRow) {
                        cell.setText(getString(R.string.MZGlyphTitle));
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == glyphEnableRow) {
                        cell.setTextAndCheck(getString(R.string.MZGlyphEnable),
                                GlyphConfig.enabled(), true);
                    } else if (position == glyphNewMessageRow) {
                        cell.setTextAndCheck(getString(R.string.MZGlyphNewMessage),
                                GlyphConfig.onNewMessage(), true);
                    } else if (position == glyphCallsRow) {
                        cell.setTextAndCheck(getString(R.string.MZGlyphCalls),
                                GlyphConfig.onCall(), true);
                    } else if (position == glyphRecordingRow) {
                        cell.setTextAndCheck(getString(R.string.MZGlyphRecording),
                                GlyphConfig.onRecording(), true);
                    } else if (position == glyphScreenOffRow) {
                        cell.setTextAndCheck(getString(R.string.MZGlyphScreenOff),
                                GlyphConfig.screenOffOnly(), false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == glyphPreviewRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon,
                                Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.MZGlyphPreview),
                                R.drawable.deproko_baseline_lamp_filled_24, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == glyphDividerRow) {
                        cell.setText(getString(R.string.MZGlyphInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == glyphHeaderRow) {
                return TYPE_HEADER;
            } else if (position == glyphPreviewRow) {
                return TYPE_TEXT;
            } else if (position == glyphDividerRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_CHECK;
        }
    }
}
