
import importlib as _importlib
import importlib.util as _importlib_util
import sys as _sys
import types as _types

ROOT = "org.mzgram.sdk"
MEDIA_ROOT = "com.google.android.exoplayer2"
ROOTS = (ROOT, MEDIA_ROOT)


def _under_root(name):
    for root in ROOTS:
        if name.startswith(root + "."):
            return True
    return False


_EXACT = {
    "org.mzgram.sdk.utils.chats.ChatUtils":
        "org.mzgram.sdk.utils.chats.ChatUtils",
    "org.mzgram.sdk.utils.ChatUtils":
        "org.mzgram.sdk.utils.chats.ChatUtils",
    "org.mzgram.sdk.utils.text.LocaleUtils":
        "org.mzgram.sdk.utils.text.LocaleUtils",
    "org.mzgram.sdk.utils.LocaleUtils":
        "org.mzgram.sdk.utils.text.LocaleUtils",
    "org.mzgram.sdk.utils.AppUtils":
        "org.mzgram.sdk.utils.AppUtils",
    "org.mzgram.sdk.R":
        "org.telegram.messenger.R",
    "org.mzgram.sdk.utils.system.VibratorUtils":
        "org.mzgram.sdk.utils.system.VibratorUtils",
    "org.mzgram.sdk.ai.AiConfig":
        "org.mzgram.ai.AiConfig",
    "org.mzgram.sdk.ai.AiController":
        "org.mzgram.sdk.ai.AiController",
    "org.mzgram.sdk.ai.ui.ResponseAlert":
        "org.mzgram.sdk.ai.ui.ResponseAlert",
    "org.mzgram.sdk.ai.ui.GenerateFromMessageBottomSheet":
        "org.mzgram.sdk.ai.ui.GenerateFromMessageBottomSheet",
    "org.mzgram.sdk.plugins.ui.components.InstallPluginBottomSheet":
        "org.mzgram.sdk.plugins.ui.components.InstallPluginBottomSheet",
    "org.mzgram.sdk.utils.system.SystemUtils":
        "org.mzgram.sdk.utils.system.SystemUtils",
    "org.mzgram.sdk.utils.SystemUtils":
        "org.mzgram.sdk.utils.system.SystemUtils",
    "org.mzgram.sdk.preferences.MainPreferencesActivity":
        "org.mzgram.settings.MZGramSettingsActivity",
    "org.mzgram.sdk.preferences.GeneralPreferencesActivity":
        "org.mzgram.settings.MZGramGeneralActivity",
    "org.mzgram.sdk.preferences.AppearancePreferencesActivity":
        "org.mzgram.settings.MZGramAppearanceActivity",
    "org.mzgram.sdk.preferences.ChatsPreferencesActivity":
        "org.mzgram.settings.MZGramChatsActivity",
    "org.mzgram.sdk.preferences.OtherPreferencesActivity":
        "org.mzgram.settings.MZGramOtherActivity",
    "org.mzgram.sdk.preferences.AppNavigationPreferencesActivity":
        "org.mzgram.settings.MZGramAppNavigationActivity",
    "org.mzgram.sdk.preferences.BasePreferencesActivity":
        "org.mzgram.sdk.preferences.BasePreferencesActivity",
    "org.mzgram.sdk.preferences.components.AltSeekbar":
        "org.mzgram.appearance.AltSeekbar",
    "org.mzgram.sdk.utils.chats.MainMenuHelper":
        "org.mzgram.drawer.MainMenuHelper",
    "org.mzgram.sdk.icons.ui.IconPacksActivity":
        "org.mzgram.icons.IconPacksActivity",
    "org.mzgram.sdk.pillstack.ui.pills.crypto.utils.ColoredBackground":
        "org.mzgram.pillstack.pills.ColoredBackground",
    "org.mzgram.sdk.pillstack.ui.pills.weather.WeatherPill":
        "org.mzgram.pillstack.pills.WeatherPill",
    "org.mzgram.sdk.pillstack.ui.PillStackPreferencesActivity":
        "org.mzgram.pillstack.PillStackSettingsActivity",
    "org.mzgram.sdk.pillstack.ui.PillStackLayout":
        "org.mzgram.pillstack.PillStackView",
    "org.mzgram.sdk.pillstack.ui.pills.weather.WeatherPreferencesActivity":
        "org.mzgram.pillstack.pills.weather.WeatherSettingsActivity",
    "org.mzgram.sdk.preferences.appearance.AppearancePreferencesActivity":
        "org.mzgram.settings.MZGramAppearanceActivity",
    "org.mzgram.sdk.nowplaying.ui.components.NowPlayingCard":
        "org.mzgram.components.ProfileMusicCard",
    "org.mzgram.sdk.nowplaying.NowPlayingController":
        "org.mzgram.nowplaying.NowPlayingController",
    "org.mzgram.sdk.proxy.ProxyController":
        "org.mzgram.proxy.ProxyController",
    "org.mzgram.sdk.utils.ui.UIUtil":
        "org.mzgram.utils.UIUtil",
    "org.mzgram.sdk.utils.ui.MainTabsUiHelper":
        "org.mzgram.appearance.MainTabsUiHelper",
    "com.google.android.exoplayer2.util.Consumer":
        "androidx.media3.common.util.Consumer",
    "com.google.android.exoplayer2.video.VideoSize":
        "androidx.media3.common.VideoSize",
    "com.google.android.exoplayer2.PlaybackException":
        "androidx.media3.common.PlaybackException",
    "com.google.android.exoplayer2.PlaybackParameters":
        "androidx.media3.common.PlaybackParameters",
    "com.google.android.exoplayer2.C":
        "androidx.media3.common.C",
    "org.telegram.ui.Gifts.ProfileGiftsContainer$SelectGiftsBottomSheet":
        "org.telegram.ui.Gifts.SelectGiftsBottomSheet",
    "org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode":
        "org.telegram.utils.glass.source.GlassSourceRenderNode",
    "org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor":
        "org.telegram.utils.glass.source.GlassSourceColor",
    "org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped":
        "org.telegram.utils.glass.source.GlassSourceWrapped",
    "org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap":
        "org.telegram.utils.glass.source.GlassSourceBitmap",
    "org.telegram.ui.Components.blur3.source.BlurredBackgroundSource":
        "org.telegram.utils.glass.source.GlassSource",
}

_PREFIXES = (
    ("org.mzgram.sdk.ai.data.", "org.mzgram.ai.data."),
    ("org.mzgram.sdk.ai.network.", "org.mzgram.ai.network."),
    ("org.mzgram.sdk.pillstack.core.", "org.mzgram.pillstack."),
    ("org.mzgram.sdk.pillstack.ui.pills.", "org.mzgram.pillstack.pills."),
    ("org.mzgram.sdk.pillstack.ui.", "org.mzgram.pillstack."),
    ("org.mzgram.sdk.preferences.", "org.mzgram.settings."),
    ("org.mzgram.sdk.plugins.", "org.mzgram.plugins."),
    ("org.mzgram.sdk.icons.", "org.mzgram.icons."),
    ("org.mzgram.sdk.camera.", "org.mzgram.camera."),
    ("org.mzgram.sdk.backup.", "org.mzgram.backup."),
    ("org.mzgram.sdk.feed.", "org.mzgram.feed."),
    ("org.mzgram.sdk.drawer.", "org.mzgram.drawer."),
    ("org.mzgram.sdk.components.", "org.mzgram.components."),
    ("org.mzgram.sdk.utils.", "org.mzgram.utils."),
)


_resolve_cache = {}
_field_shape_cache = {}
_NO_SHAPE = object()


def resolve(name):
    if not isinstance(name, str):
        return name
    cached = _resolve_cache.get(name)
    if cached is not None:
        return cached
    result = _resolve_uncached(name)
    if len(_resolve_cache) < 4096:
        _resolve_cache[name] = result
    return result


def _resolve_uncached(name):
    exact = _EXACT.get(name)
    if exact is not None:
        return exact
    if not _under_root(name):
        return name
    outer, sep, nested = name.partition("$")
    exact = _EXACT.get(outer)
    if exact is not None:
        return exact + sep + nested
    for old, new in _PREFIXES:
        if outer.startswith(old):
            return new + outer[len(old):] + sep + nested
    return name


_FIELD_SHAPED = {
    "org.mzgram.sdk.MZConfig": {
        "translationProvider": ("getTranslationProvider", "setTranslationProvider"),
        "translationFormality": ("getTranslationFormality", "setTranslationFormality"),
        "disableNumberRounding": ("getDisableNumberRounding", "setDisableNumberRounding"),
        "formatTimeWithSeconds": ("getFormatTimeWithSeconds", "setFormatTimeWithSeconds"),
        "relativeLastSeen": ("getRelativeLastSeen", "setRelativeLastSeen"),
        "inAppVibration": ("getInAppVibration", "setInAppVibration"),
        "filterZalgo": ("getFilterZalgo", "setFilterZalgo"),
        "useYandexMaps": ("getUseYandexMaps", "setUseYandexMaps"),
        "downloadSpeedBoost": ("getDownloadSpeedBoost", "setDownloadSpeedBoost"),
        "uploadSpeedBoost": ("getUploadSpeedBoost", "setUploadSpeedBoost"),
        "hidePhoneNumber": ("getHidePhoneNumber", "setHidePhoneNumber"),
        "showIdAndDc": ("getShowIdAndDc", "setShowIdAndDc"),
        "hideArchiveFolder": ("getHideArchiveFolder", "setHideArchiveFolder"),
        "archiveOnPull": ("getArchiveOnPull", "setArchiveOnPull"),
        "disableUnarchiveSwipe": ("getDisableUnarchiveSwipe", "setDisableUnarchiveSwipe"),
        "doNotUseProxy": ("getDoNotUseProxy", "setDoNotUseProxy"),
        "customSavePath": ("getCustomSavePath", "setCustomSavePath"),
        "iconPack": ("getIconPack", "setIconPack"),
        "editingIconPackId": ("getEditingIconPackId", "setEditingIconPackId"),
        "avatarCorners": ("getAvatarCorners", "setAvatarCorners"),
        "singleCornerRadius": ("getSingleCornerRadius", "setSingleCornerRadius"),
        "dividerStyle": ("getDividerStyle", "setDividerStyle"),
        "forceSnow": ("getForceSnow", "setForceSnow"),
        "hideActionBarStatus": ("getHideActionBarStatus", "setHideActionBarStatus"),
        "centerTitle": ("getCenterTitle", "setCenterTitle"),
        "hideStories": ("getHideStories", "setHideStories"),
        "hideFloatingButton": ("getHideFloatingButton", "setHideFloatingButton"),
        "hideDialogsSearchBar": ("getHideDialogsSearchBar", "setHideDialogsSearchBar"),
        "senderMiniAvatars": ("getSenderMiniAvatars", "setSenderMiniAvatars"),
        "titleText": ("getTitleText", "setTitleText"),
        "tabIcons": ("getTabIcons", "setTabIcons"),
        "tabCounter": ("getTabCounter", "setTabCounter"),
        "hideAllChats": ("getHideAllChats", "setHideAllChats"),
        "squareFab": ("getSquareFab", "setSquareFab"),
        "sectionRadius": ("getSectionRadius", "setSectionRadius"),
        "sectionsSeparatedHeadersPreference": ("getSectionsSeparatedHeadersPreference", "setSectionsSeparatedHeadersPreference"),
        "newLoadingStyle": ("getNewLoadingStyle", "setNewLoadingStyle"),
        "newSliderStyle": ("getNewSliderStyle", "setNewSliderStyle"),
        "newSwitchStyle": ("getNewSwitchStyle", "setNewSwitchStyle"),
        "newChatHeaderStyle": ("getNewChatHeaderStyle", "setNewChatHeaderStyle"),
        "newNavigationBarStyle": ("getNewNavigationBarStyle", "setNewNavigationBarStyle"),
        "tabletMode": ("getTabletMode", "setTabletMode"),
        "useSystemFonts": ("getUseSystemFonts", "setUseSystemFonts"),
        "gooeyAvatarAnimation": ("getGooeyAvatarAnimation", "setGooeyAvatarAnimation"),
        "customThemes": ("getCustomThemes", "setCustomThemes"),
        "predictiveBackIntensity": ("getPredictiveBackIntensity", "setPredictiveBackIntensity"),
        "springAnimations": ("getSpringAnimations", "setSpringAnimations"),
        "glassOutlineStyle": ("getGlassOutlineStyle", "setGlassOutlineStyle"),
        "glassMessageMenu": ("getGlassMessageMenu", "setGlassMessageMenu"),
        "forceBlur": ("getForceBlur", "setForceBlur"),
        "eventType": ("getEventType", "setEventType"),
        "navigationDrawer": ("getNavigationDrawer", "setNavigationDrawer"),
        "immersiveDrawerAnimation": ("getImmersiveDrawerAnimation", "setImmersiveDrawerAnimation"),
        "showFeedTab": ("getShowFeedTab", "setShowFeedTab"),
        "showFeedUnreadCounter": ("getShowFeedUnreadCounter", "setShowFeedUnreadCounter"),
        "stickerSize": ("getStickerSize", "setStickerSize"),
        "hideStickerTime": ("getHideStickerTime", "setHideStickerTime"),
        "replyColors": ("getReplyColors", "setReplyColors"),
        "replyEmoji": ("getReplyEmoji", "setReplyEmoji"),
        "replyBackground": ("getReplyBackground", "setReplyBackground"),
        "stickerShape": ("getStickerShape", "setStickerShape"),
        "unlimitedRecentStickers": ("getUnlimitedRecentStickers", "setUnlimitedRecentStickers"),
        "hideReactionsInPrivateChats": ("getHideReactionsInPrivateChats", "setHideReactionsInPrivateChats"),
        "hideReactionsInChannels": ("getHideReactionsInChannels", "setHideReactionsInChannels"),
        "hideReactionsInGroups": ("getHideReactionsInGroups", "setHideReactionsInGroups"),
        "doubleTapAction": ("getDoubleTapAction", "setDoubleTapAction"),
        "doubleTapActionOutOwner": ("getDoubleTapActionOutOwner", "setDoubleTapActionOutOwner"),
        "bottomButton": ("getBottomButton", "setBottomButton"),
        "widePostsInFeed": ("getWidePostsInFeed", "setWidePostsInFeed"),
        "widePostsInChannels": ("getWidePostsInChannels", "setWidePostsInChannels"),
        "telegramAiEditor": ("getTelegramAiEditor", "setTelegramAiEditor"),
        "telegramAiSummaries": ("getTelegramAiSummaries", "setTelegramAiSummaries"),
        "quickAdminShortcuts": ("getQuickAdminShortcuts", "setQuickAdminShortcuts"),
        "quickTransitionForChannels": ("getQuickTransitionForChannels", "setQuickTransitionForChannels"),
        "quickTransitionForTopics": ("getQuickTransitionForTopics", "setQuickTransitionForTopics"),
        "disableGreetingSticker": ("getDisableGreetingSticker", "setDisableGreetingSticker"),
        "hideKeyboardOnScroll": ("getHideKeyboardOnScroll", "setHideKeyboardOnScroll"),
        "addCommaAfterMention": ("getAddCommaAfterMention", "setAddCommaAfterMention"),
        "disableMarkdown": ("getDisableMarkdown", "setDisableMarkdown"),
        "hideSendAsPeer": ("getHideSendAsPeer", "setHideSendAsPeer"),
        "removeMessageTail": ("getRemoveMessageTail", "setRemoveMessageTail"),
        "replaceEditedWithIcon": ("getReplaceEditedWithIcon", "setReplaceEditedWithIcon"),
        "showOnlineStatus": ("getShowOnlineStatus", "setShowOnlineStatus"),
        "hideShareButton": ("getHideShareButton", "setHideShareButton"),
        "showResultsBeforeVoting": ("getShowResultsBeforeVoting", "setShowResultsBeforeVoting"),
        "showCopyPhotoButton": ("getShowCopyPhotoButton", "setShowCopyPhotoButton"),
        "showSaveMessageButton": ("getShowSaveMessageButton", "setShowSaveMessageButton"),
        "showRepeatMessageButton": ("getShowRepeatMessageButton", "setShowRepeatMessageButton"),
        "showClearButton": ("getShowClearButton", "setShowClearButton"),
        "showHistoryButton": ("getShowHistoryButton", "setShowHistoryButton"),
        "showReportButton": ("getShowReportButton", "setShowReportButton"),
        "showGenerateButton": ("getShowGenerateButton", "setShowGenerateButton"),
        "showDetailsButton": ("getShowDetailsButton", "setShowDetailsButton"),
        "groupMessageMenu": ("getGroupMessageMenu", "setGroupMessageMenu"),
        "recognitionLanguage": ("getRecognitionLanguage", "setRecognitionLanguage"),
        "postprocessingWithAi": ("getPostprocessingWithAi", "setPostprocessingWithAi"),
        "cameraType": ("getCameraType", "setCameraType"),
        "extendedFramesPerSecond": ("getExtendedFramesPerSecond", "setExtendedFramesPerSecond"),
        "cameraStabilization": ("getCameraStabilization", "setCameraStabilization"),
        "cameraMirrorMode": ("getCameraMirrorMode", "setCameraMirrorMode"),
        "videoMessagesCamera": ("getVideoMessagesCamera", "setVideoMessagesCamera"),
        "rememberLastUsedCamera": ("getRememberLastUsedCamera", "setRememberLastUsedCamera"),
        "startWithWideAngleCamera": ("getStartWithWideAngleCamera", "setStartWithWideAngleCamera"),
        "zoomSlider": ("getZoomSlider", "setZoomSlider"),
        "staticZoom": ("getStaticZoom", "setStaticZoom"),
        "alwaysSendInHD": ("getAlwaysSendInHD", "setAlwaysSendInHD"),
        "hideCameraTile": ("getHideCameraTile", "setHideCameraTile"),
        "doubleTapSeekDuration": ("getDoubleTapSeekDuration", "setDoubleTapSeekDuration"),
        "preferOriginalQuality": ("getPreferOriginalQuality", "setPreferOriginalQuality"),
        "swipeToPip": ("getSwipeToPip", "setSwipeToPip"),
        "unmuteWithVolumeButtons": ("getUnmuteWithVolumeButtons", "setUnmuteWithVolumeButtons"),
        "pauseOnMinimizeVideo": ("getPauseOnMinimizeVideo", "setPauseOnMinimizeVideo"),
        "pauseOnMinimizeVoice": ("getPauseOnMinimizeVoice", "setPauseOnMinimizeVoice"),
        "pauseOnMinimizeRound": ("getPauseOnMinimizeRound", "setPauseOnMinimizeRound"),
        "useGoogleCrashlytics": ("getUseGoogleCrashlytics", "setUseGoogleCrashlytics"),
        "useGoogleAnalytics": ("getUseGoogleAnalytics", "setUseGoogleAnalytics"),
        "enableAdBlock": ("getEnableAdBlock", "setEnableAdBlock"),
        "updateScheduleTimestamp": ("getUpdateScheduleTimestamp", "setUpdateScheduleTimestamp"),
        "sdkUpdateScheduleTimestamp": ("getSdkUpdateScheduleTimestamp", "setSdkUpdateScheduleTimestamp"),
        "targetLang": ("getTargetLang", "setTargetLang"),
        "flashWarmth": ("getFlashWarmth", "setFlashWarmth"),
        "flashIntensity": ("getFlashIntensity", "setFlashIntensity"),
        "pluginsDevMode": ("getPluginsDevMode", "setPluginsDevMode"),
        "pluginsSafeMode": ("getPluginsSafeMode", "setPluginsSafeMode"),
        "pluginsCompactView": ("getPluginsCompactView", "setPluginsCompactView"),
        "pluginsPySdkAutoUpdate": ("getPluginsPySdkAutoUpdate", "setPluginsPySdkAutoUpdate"),
        "pluginsPySdkBetaVersions": ("getPluginsPySdkBetaVersions", "setPluginsPySdkBetaVersions"),
        "pluginsDisableArtOpts": ("getPluginsDisableArtOpts", "setPluginsDisableArtOpts"),
        "pinnedPlugins": ("getPinnedPlugins", "setPinnedPlugins"),
        "useSystemIconShape": ("getUseSystemIconShape", "setUseSystemIconShape"),
        "editor": "getEditor",
        "preferences": "getPreferences",
        "GSON": "getGSON",
        "pluginsEngine": ("getPluginsEngine", "setPluginsEngine"),
        "avatarSquareness": "getAvatarSquareness",
        "sectionRadiusDp": "getSectionRadiusDp",
        "sectionsSeparatedHeaders": ("getSectionsSeparatedHeaders", "setSectionsSeparatedHeaders"),
        "logging": ("getLogging", "setLogging"),
        "mainMenuLayout": "getMainMenuLayout",
        "mainMenuHiddenItems": "getMainMenuHiddenItems",
        "defaultMainMenuLayout": "getDefaultMainMenuLayout",
        "iconPacksLayout": "getIconPacksLayout",
        "iconPacksHidden": "getIconPacksHidden",
        "doNotMarkAsNew": "getDoNotMarkAsNew",
        "newFeaturesShowedAt": "getNewFeaturesShowedAt",
        "yandexSearchEngine": "getYandexSearchEngine",
        "currentLangName": "getCurrentLangName",
        "apiBotInfo": "getApiBotInfo",
        "backupKeys": "getBackupKeys",
        "onlineDotInnerRadius": "getOnlineDotInnerRadius",
        "onlineDotOuterRadius": "getOnlineDotOuterRadius",
    },
    "org.mzgram.sdk.plugins.PluginsController": {
        "engines": "getEngines",
        "plugins": "getPlugins",
        "pluginsDir": "getPluginsDir",
        "preferences": "getPreferences",
        "initialized": "getInitialized",
        "settings": "getSettings",
        "watchdog": "getWatchdog",
    },
    "org.mzgram.sdk.pillstack.core.PillStackConfig": {
        "activePills": "getActivePills",
        "hiddenPills": "getHiddenPills",
        "configLoaded": "isConfigLoaded",
    },
    "org.mzgram.sdk.ai.AiConfig": {
        "saveHistory": ("getSaveHistory", "setSaveHistory"),
        "responseStreaming": ("getResponseStreaming", "setResponseStreaming"),
        "temperature": ("getTemperature", "setTemperature"),
        "showResponseOnly": ("getShowResponseOnly", "setShowResponseOnly"),
        "insertAsQuote": ("getInsertAsQuote", "setInsertAsQuote"),
        "selectedServiceId": ("getSelectedServiceId", "setSelectedServiceId"),
        "selectedRole": ("getSelectedRole", "setSelectedRole"),
        "preferences": "getPreferences",
        "services": "getServices",
        "roles": "getRoles",
        "conversationHistory": "getConversationHistory",
        "selectedService": "getSelectedService",
    },
}


_INSTANCE_SHAPED = {
    "org.telegram.messenger.SharedConfig$ProxyInfo": {
        "address": ("getAddress", "setAddress"),
        "port": ("getPort", "setPort"),
        "username": ("getUsername", "setUsername"),
        "password": ("getPassword", "setPassword"),
        "secret": ("getSecret", "setSecret"),
        "link": "getLink",
    },
}

_WRAP_RESULT = {
    "org.telegram.messenger.SharedConfig": {
        "currentProxy": "org.telegram.messenger.SharedConfig$ProxyInfo",
    },
}


class _FieldShapedObject:

    def __init__(self, java_obj, fields):
        getters, setters = {}, {}
        for field, target in fields.items():
            if isinstance(target, (tuple, list)):
                getters[field] = target[0]
                if len(target) > 1 and target[1]:
                    setters[field] = target[1]
            else:
                getters[field] = target
        object.__setattr__(self, "_mzgram_java", java_obj)
        object.__setattr__(self, "_mzgram_fields", getters)
        object.__setattr__(self, "_mzgram_setters", setters)

    def __getattr__(self, attr):
        target = object.__getattribute__(self, "_mzgram_java")
        method = object.__getattribute__(self, "_mzgram_fields").get(attr)
        if method is not None:
            return getattr(target, method)()
        return getattr(target, attr)

    def __setattr__(self, attr, value):
        target = object.__getattribute__(self, "_mzgram_java")
        method = object.__getattribute__(self, "_mzgram_setters").get(attr)
        if method is not None:
            getattr(target, method)(value)
            return
        setattr(target, attr, value)

    def __eq__(self, other):
        return object.__getattribute__(self, "_mzgram_java") == unwrap(other)

    def __hash__(self):
        return hash(object.__getattribute__(self, "_mzgram_java"))

    def __repr__(self):
        return repr(object.__getattribute__(self, "_mzgram_java"))


def wrap_instance(obj, java_name):
    fields = _INSTANCE_SHAPED.get(java_name)
    if obj is None or fields is None or isinstance(obj, _FieldShapedObject):
        return obj
    try:
        return _FieldShapedObject(obj, fields)
    except Exception:
        return obj


class _FieldShapedClass:

    def __init__(self, java_class, fields, wraps=None):
        getters = {}
        setters = {}
        for field, target in fields.items():
            if isinstance(target, (tuple, list)):
                getters[field] = target[0]
                if len(target) > 1 and target[1]:
                    setters[field] = target[1]
            else:
                getters[field] = target
        object.__setattr__(self, "_mzgram_java", java_class)
        object.__setattr__(self, "_mzgram_fields", getters)
        object.__setattr__(self, "_mzgram_setters", setters)
        object.__setattr__(self, "_mzgram_wraps", wraps or {})

    def __getattr__(self, attr):
        target = object.__getattribute__(self, "_mzgram_java")
        method = object.__getattribute__(self, "_mzgram_fields").get(attr)
        if method is not None:
            value = getattr(target, method)()
        else:
            value = getattr(target, attr)
        wrapped = object.__getattribute__(self, "_mzgram_wraps").get(attr)
        if wrapped is not None:
            return wrap_instance(value, wrapped)
        return value

    def __setattr__(self, attr, value):
        target = object.__getattribute__(self, "_mzgram_java")
        method = object.__getattribute__(self, "_mzgram_setters").get(attr)
        if method is not None:
            getattr(target, method)(value)
            return
        setattr(target, attr, value)

    def __repr__(self):
        return repr(object.__getattribute__(self, "_mzgram_java"))


def unwrap(obj):
    if isinstance(obj, (_FieldShapedClass, _FieldShapedObject)):
        return object.__getattribute__(obj, "_mzgram_java")
    return obj


def _field_shape(name):
    cached = _field_shape_cache.get(name, None)
    if cached is not None:
        return None if cached is _NO_SHAPE else cached
    shape = _field_shape_uncached(name)
    if isinstance(name, str) and len(_field_shape_cache) < 4096:
        _field_shape_cache[name] = _NO_SHAPE if shape is None else shape
    return shape


def _field_shape_uncached(name):
    fields = _FIELD_SHAPED.get(name)
    if fields is not None:
        return fields
    for source, shaped in _FIELD_SHAPED.items():
        if resolve(source) == name:
            return shaped
    return None


def adapt(name, obj):
    if obj is None or isinstance(obj, _FieldShapedClass):
        return obj
    replacement = substitute(name)
    if replacement is not None:
        return replacement
    fields = _field_shape(name)
    wraps = _WRAP_RESULT.get(name)
    if fields is None and wraps is None:
        return obj
    try:
        return _FieldShapedClass(obj, fields or {}, wraps)
    except Exception:
        return obj


_PYTHON_SUBSTITUTES = {
    "org.mzgram.sdk.plugins.models.PluginItemFactory":
        ("ui.settings", "SimpleSettingFactory"),
}


def substitute(name):
    if not isinstance(name, str):
        return None
    target = _PYTHON_SUBSTITUTES.get(name)
    if target is None:
        return None
    module, attr = target
    try:
        return getattr(_importlib.import_module(module), attr, None)
    except Exception:
        return None


_plain_cache = {}


def is_plain(name):
    cached = _plain_cache.get(name)
    if cached is not None:
        return cached
    plain = (isinstance(name, str) and resolve(name) == name
             and name not in _PYTHON_SUBSTITUTES and name not in _WRAP_RESULT
             and _field_shape(name) is None)
    if len(_plain_cache) < 4096:
        _plain_cache[name] = plain
    return plain


def is_alias(name):
    return isinstance(name, str) and _under_root(name)


def _find_class(name):
    try:
        from hook_utils import find_class
        return find_class(name)
    except Exception:
        return None


class _AliasModule(_types.ModuleType):

    def __getattr__(self, attr):
        if attr.startswith("__"):
            raise AttributeError(attr)
        full = self.__name__ + "." + attr
        replacement = substitute(full)
        if replacement is not None:
            return replacement
        found = _find_class(full)
        if found is not None:
            return found
        if attr[:1].isupper():
            raise AttributeError(attr)
        try:
            return _importlib.import_module(full)
        except Exception:
            raise AttributeError(attr)


class _AliasFinder:

    PACKAGE = "com.mzgram"
    PACKAGES = ("com.mzgram", MEDIA_ROOT)

    def find_spec(self, fullname, path=None, target=None):
        if not any(fullname == root or fullname.startswith(root + ".")
                   or root.startswith(fullname + ".")
                   for root in self.PACKAGES):
            return None
        if fullname.rpartition(".")[2][:1].isupper():
            return None
        return _importlib_util.spec_from_loader(fullname, _AliasLoader(), is_package=True)


class _AliasLoader:

    def create_module(self, spec):
        module = _AliasModule(spec.name)
        module.__path__ = []
        return module

    def exec_module(self, module):
        return None


def _ensure_root_package():
    try:
        _importlib.import_module("com")
        return
    except Exception:
        pass
    module = _AliasModule("com")
    module.__path__ = []
    _sys.modules["com"] = module


def install_import_hook():
    try:
        if any(isinstance(finder, _AliasFinder) for finder in _sys.meta_path):
            return
        _sys.meta_path.insert(0, _AliasFinder())
        _ensure_root_package()
    except Exception:
        pass
