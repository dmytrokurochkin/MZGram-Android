/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Spy mode: hide own online/last-seen status. This is a DIFFERENT
 * mechanism from Ghost Mode's MZGramGhostMode/SendOnline concept on
 * Desktop -- there, suppressing online status is a live per-request
 * override at the point the client tells the server "I'm online" (see
 * MZGram::SendOnline() in mzgram_options.cpp, api_updates.cpp). On
 * Android that live heartbeat is generated deep inside native tgnet
 * (jni/TgNetWrapper + the tgnet library), with no safe Java-level hook
 * to override it per request without risking unrelated native-layer
 * side effects (e.g. native_pauseNetwork() also throttles the
 * connection itself, not just presence).
 *
 * Instead this reuses Telegram's own native "Last seen & online"
 * PRIVACY RULE (the exact same one Settings > Privacy > Last Seen
 * already edits, TL_inputPrivacyKeyStatusTimestamp), which is reachable
 * safely from Java and has the same end result: other people cannot see
 * your online status. Because that is a STORED server-side rule, not a
 * transient override, only the three simple cases (Everybody/Contacts/
 * Nobody with no custom per-user or premium exceptions) are snapshotted
 * and restored -- a user with custom exceptions already configured is
 * left untouched rather than risk silently discarding them.
 */

package org.telegram.messenger.mzgram;

import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.util.ArrayList;

public class MZGramSpyMode {

    private static final int STATE_EVERYBODY = 0;
    private static final int STATE_CONTACTS = 1;
    private static final int STATE_NOBODY = 2;
    private static final int STATE_UNKNOWN = -1;
    private static final int STATE_NONE_SAVED = Integer.MIN_VALUE;

    public interface ResultCallback {
        void onResult(boolean success, boolean hadCustomExceptions);
    }

    // Applies (hide = true) or reverts (hide = false) the last-seen privacy
    // rule. Returns immediately with false if the current rule set has not
    // loaded from the server yet, or (when hiding) if it is anything other
    // than one of the three simple states -- callers should keep the
    // setting off and let the user retry, and should tell the user their
    // custom privacy exceptions were left alone.
    public static void setHideOwnOnlineStatus(int account, boolean hide, ResultCallback callback) {
        ArrayList<TLRPC.PrivacyRule> current = ContactsController.getInstance(account).getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
        if (current == null) {
            if (callback != null) {
                callback.onResult(false, false);
            }
            return;
        }
        if (hide) {
            int state = classify(current);
            if (state == STATE_UNKNOWN) {
                if (callback != null) {
                    callback.onResult(false, true);
                }
                return;
            }
            MZGramConfig.setSavedLastSeenPrivacyState(state);
            sendSetPrivacy(account, buildInputRules(STATE_NOBODY), callback);
        } else {
            int saved = MZGramConfig.savedLastSeenPrivacyState;
            if (saved == STATE_NONE_SAVED) {
                if (callback != null) {
                    callback.onResult(true, false);
                }
                return;
            }
            sendSetPrivacy(account, buildInputRules(saved), callback);
            MZGramConfig.setSavedLastSeenPrivacyState(STATE_NONE_SAVED);
        }
    }

    private static int classify(ArrayList<TLRPC.PrivacyRule> rules) {
        if (rules.size() != 1) {
            return STATE_UNKNOWN;
        }
        TLRPC.PrivacyRule rule = rules.get(0);
        if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
            return STATE_EVERYBODY;
        } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
            return STATE_CONTACTS;
        } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
            return STATE_NOBODY;
        }
        return STATE_UNKNOWN;
    }

    private static ArrayList<TLRPC.InputPrivacyRule> buildInputRules(int state) {
        ArrayList<TLRPC.InputPrivacyRule> rules = new ArrayList<>();
        switch (state) {
            case STATE_EVERYBODY:
                rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                break;
            case STATE_CONTACTS:
                rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                break;
            case STATE_NOBODY:
            default:
                rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
                break;
        }
        return rules;
    }

    private static void sendSetPrivacy(int account, ArrayList<TLRPC.InputPrivacyRule> inputRules, ResultCallback callback) {
        TL_account.setPrivacy req = new TL_account.setPrivacy();
        req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        req.rules = inputRules;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error == null && response instanceof TL_account.privacyRules) {
                TL_account.privacyRules privacyRules = (TL_account.privacyRules) response;
                ContactsController.getInstance(account).setPrivacyRules(privacyRules.rules, ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
            } else if (error != null) {
                FileLog.e("MZGramSpyMode.sendSetPrivacy: " + error.text);
            }
            if (callback != null) {
                callback.onResult(error == null, false);
            }
        });
    }
}
