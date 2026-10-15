/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The app's own name wherever it names itself: dialog titles, permission
 * requests, call and service notifications, the version line at the bottom
 * of Settings. The texts come from the cloud language packs too, so the name
 * is changed when a text is read (LocaleController), in every language, for
 * the keys listed here only: the Telegram service (account, server, Premium,
 * other people's apps) keeps its name. Nothing sent to the server changes:
 * the package, api_id, app version and device model stay as they are.
 */

package org.telegram.messenger.mzgram;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MZGramBranding {

    public static final String APP_NAME = "MZGram";
    public static final String REPOSITORY_URL = "https://github.com/dmytrokurochkin/MZGram-Android";

    // Keys whose text names the app itself
    private static final Set<String> RENAMED_KEYS = new HashSet<>(Arrays.asList(
            "AppName",
            "AppNameBeta",
            "AppUpdate",
            "AppUpdateBeta",
            "UpdateTelegram",
            "UpdateAppAlert",
            "UnsupportedMedia2",
            "StoryUnsupported",
            "Gift2ExportTONUpdateRequiredText",
            "NotificationsPermissionAlertSubtitle",
            "ClearingCacheDescription",
            "LocalDatabaseInfo",
            "ChangePasscodeInfo",
            "ScreenCaptureAlert",
            "StorageUsageTelegram",
            "StorageUsageTelegramLess",
            "UpdateContactsMessage",
            "SdCardErrorDescription",
            "BrowserExternalRestricted",
            "AllowReadCall",
            "AllowReadCallAndLog",
            "AllowReadCallLog",
            "AllowFillNumber",
            "AllowBackgroundActivityInfo",
            "AllowBackgroundActivityInfoOneUIBelowS",
            "AllowBackgroundActivityInfoOneUIAboveS",
            "PermissionStorageWithHint",
            "PermissionNoAudioWithHint",
            "PermissionNoBluetoothWithHint",
            "PermissionNoAudioVideoWithHint",
            "PermissionNoCameraWithHint",
            "PermissionNoCameraMicVideo",
            "PermissionNoLocation",
            "PermissionNoLocationStory",
            "PermissionNoLocationFriends",
            "PermissionNoLocationNavigation",
            "PermissionNoContactsSharing",
            "PermissionNoContactsSaving",
            "PermissionNoStorageAvatar",
            "PermissionNoAudioStorageStory",
            "PermissionDrawAboveOtherApps",
            "PermissionDrawAboveOtherAppsGroupCall",
            "PermissionXiaomiLockscreen",
            "PermissionFSILockscreen",
            "PermissionBackgroundLocation",
            "VoipNeedMicPermissionWithHint",
            "VoipNeedMicCameraPermissionWithHint",
            "VoipNeedCameraPermission",
            "VoipInCallBranding",
            "VoipInVideoCallBranding",
            "VoipInConferenceCallBranding",
            "VoipInCallBrandingWithName",
            "VoipInVideoCallBrandingWithName",
            "VoipOutgoingCall"
    ));

    public static boolean isRenamed(String key) {
        return key != null && RENAMED_KEYS.contains(key);
    }

    // "Telegram for Android" -> "MZGram", "Telegram" -> "MZGram"
    public static String rename(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("Telegram for Android", APP_NAME).replace("Telegram", APP_NAME);
    }

    // LocaleController: a text as it is shown
    public static String brand(String key, String value) {
        return isRenamed(key) ? rename(value) : value;
    }

    private static final Pattern VERSION_NAME = Pattern.compile("^(.+?) \\((.+)\\)$");

    // The MZGram version and the Telegram version it is based on, from the
    // versionName "1.0.1 (12.10.1)" of a release; {versionName, null} when
    // the name has no MZGram version (a build without a tag).
    public static String[] versions(String versionName) {
        if (versionName == null) {
            return new String[]{"", null};
        }
        Matcher matcher = VERSION_NAME.matcher(versionName);
        if (matcher.matches()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        return new String[]{versionName, null};
    }
}
