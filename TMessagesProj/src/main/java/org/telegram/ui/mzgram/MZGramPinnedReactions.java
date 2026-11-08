package org.telegram.ui.mzgram;

import static org.telegram.messenger.LocaleController.getString;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.R;
import org.telegram.messenger.mzgram.MZGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;
import java.util.List;

/**
 * MZGram: own list of quick reactions shown first in the reaction panel above
 * a message, separately for chats and channels. Reads the lists that
 * MZGramPinnedReactionsActivity saves. Ported from Nagram's
 * PinnedElementsHelper (GPL-3.0, tw/nekomimi/nekogram/helpers/PinnedElementsHelper.java);
 * the storage is MZGram's own.
 */
public final class MZGramPinnedReactions {

    /** How many reactions can be pinned in each list. */
    public static final int LIMIT = 7;

    private MZGramPinnedReactions() {
    }

    /** The pinned reactions for chats or for channels; empty while that list is off. */
    public static ArrayList<ReactionsLayoutInBubble.VisibleReaction> getFavoriteReactions(boolean isChannel) {
        ArrayList<ReactionsLayoutInBubble.VisibleReaction> reactions = new ArrayList<>();
        if (isChannel ? !MZGramConfig.usePinnedReactionsChannels : !MZGramConfig.usePinnedReactionsChats) {
            return reactions;
        }
        try {
            JSONArray list = new JSONArray(new JSONTokener(isChannel ? MZGramConfig.pinnedReactionsChannels : MZGramConfig.pinnedReactionsChats));
            int handled = 0;
            for (int i = 0; i < list.length() && handled < LIMIT; i++) {
                try {
                    JSONObject object = list.getJSONObject(i);
                    ReactionsLayoutInBubble.VisibleReaction reaction = new ReactionsLayoutInBubble.VisibleReaction();
                    if (object.has("emoticon")) {
                        reaction.emojicon = object.getString("emoticon");
                        reaction.hash = reaction.emojicon.hashCode();
                    } else if (object.has("document_id")) {
                        reaction.documentId = object.getLong("document_id");
                        reaction.hash = reaction.documentId;
                    } else {
                        continue;
                    }
                    handled++;
                    reactions.add(reaction);
                } catch (JSONException ignored) {
                }
            }
        } catch (JSONException ignored) {
        }
        return reactions;
    }

    /** How many reactions are pinned in the lists that are on. */
    public static int getFavoriteReactionsCount() {
        return getFavoriteReactions(true).size() + getFavoriteReactions(false).size();
    }

    /** The subtitle of the settings row: the number of pinned reactions or "Off". */
    public static String getRowDescription() {
        int count = getFavoriteReactionsCount();
        return count > 0 ? String.valueOf(count) : getString(R.string.ReactionsOff);
    }

    /** Two emoji are the same reaction when they differ at most by the variation selector. */
    public static boolean sameEmoticon(String a, String b) {
        return a != null && b != null && a.replace("️", "").equals(b.replace("️", ""));
    }

    /**
     * For a chat that allows only some reactions: puts the pinned ones that the
     * chat allows first in the panel and reports which ones they were, so the
     * regular loop skips them.
     */
    public static void prefillAllowed(boolean isChannel, List<TLRPC.Reaction> allowed,
                                      List<ReactionsLayoutInBubble.VisibleReaction> visible,
                                      List<String> usedEmoticons, List<Long> usedDocumentIds) {
        for (ReactionsLayoutInBubble.VisibleReaction reaction : getFavoriteReactions(isChannel)) {
            for (TLRPC.Reaction s : allowed) {
                if (reaction.emojicon != null && s instanceof TLRPC.TL_reactionEmoji && sameEmoticon(reaction.emojicon, ((TLRPC.TL_reactionEmoji) s).emoticon)) {
                    // Use the chat's own spelling, so the regular loop recognises it as already added.
                    reaction.emojicon = ((TLRPC.TL_reactionEmoji) s).emoticon;
                    usedEmoticons.add(reaction.emojicon);
                    visible.add(reaction);
                    break;
                } else if (reaction.documentId != 0 && s instanceof TLRPC.TL_reactionCustomEmoji && reaction.documentId == ((TLRPC.TL_reactionCustomEmoji) s).document_id) {
                    usedDocumentIds.add(reaction.documentId);
                    visible.add(reaction);
                    break;
                }
            }
        }
    }
}
