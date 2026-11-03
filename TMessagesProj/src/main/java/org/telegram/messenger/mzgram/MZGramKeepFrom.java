package org.telegram.messenger.mzgram;

/**
 * Settings > MZGram > Archive > Keep messages: from users, from bots, in
 * groups, in channels; all on by default. The author decides first: a bot's
 * message goes by "From bots" in any chat. Else the chat: a private chat by
 * "From users", a group by "In groups" (a channel writing in the group and an
 * anonymous admin too, as the group is where they write), a channel by "In
 * channels". Turning one off stops new saves and keeps what was saved.
 */
public final class MZGramKeepFrom {

    public static final int USER = 0;
    public static final int BOT = 1;
    public static final int GROUP = 2;
    public static final int CHANNEL = 3;

    public static final int CHAT_PRIVATE = 0;
    public static final int CHAT_GROUP = 1;
    public static final int CHAT_CHANNEL = 2;

    private MZGramKeepFrom() {
    }

    public static int sourceOf(boolean authorBot, int chat) {
        if (authorBot) {
            return BOT;
        }
        return chat == CHAT_CHANNEL ? CHANNEL : chat == CHAT_GROUP ? GROUP : USER;
    }

    public static boolean saves(int source) {
        switch (source) {
            case BOT: return MZGramConfig.saveForBots;
            case GROUP: return MZGramConfig.saveInGroups;
            case CHANNEL: return MZGramConfig.saveInChannels;
            default: return MZGramConfig.saveFromUsers;
        }
    }

    // Whether anything of a chat may be kept, before its messages are
    // known: a group may have bots' messages to keep while "In groups" is off.
    public static boolean maySaveChat(boolean botChat, int chat) {
        if (chat == CHAT_GROUP) {
            return MZGramConfig.saveInGroups || MZGramConfig.saveForBots;
        } else if (chat == CHAT_CHANNEL) {
            return MZGramConfig.saveInChannels;
        }
        return botChat ? MZGramConfig.saveForBots : MZGramConfig.saveFromUsers;
    }
}
