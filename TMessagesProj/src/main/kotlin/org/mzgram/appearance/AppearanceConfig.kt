package org.mzgram.appearance

import android.content.SharedPreferences
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LiteMode
import org.telegram.ui.Components.blur3.GlassOutlineStyle
import org.mzgram.core.CoreConfig
import org.mzgram.core.CoreXConfig
import org.mzgram.core.config.ConfigItem
import org.mzgram.ext.ExtConfig

/**
 * Настройки экрана «Appearance», перенесённые из MZGram.
 *
 * Здесь живут ТОЛЬКО те настройки, аналогов которых нет в MZGram
 * ([org.mzgram.ext.ExtConfig] / [org.mzgram.core.CoreConfig]).
 * Всё остальное экран берёт из существующих конфигов, чтобы не плодить дубли.
 *
 * Схема повторяет [org.mzgram.MZGramConfig]: те же SharedPreferences, тот же [ConfigItem],
 * собственный список. Ключи с префиксом MZAppearance.
 *
 * Загрузка ленивая: [ensureLoaded] вызывается из геттеров, потому что ApplicationLoader
 * трогать нельзя. После первой загрузки проверка стоит один volatile-read.
 */
object AppearanceConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @JvmStatic
    fun getConfigTypes(): Map<String, Int> = configs.associate { it.key to it.type }

    @Volatile
    private var configLoaded = false

    /** Максимум слайдера закругления аватарок: радиус = половина стороны, то есть круг. */
    const val AVATAR_CORNERS_MAX = 28

    /** Темы Monet на ролях Telemone — то, чем набор стал 19.08.2026. */
    const val MONET_STYLE_TELEMONE = 0

    /** Прежний набор Monet, пришедший вместе с базой форка (токены вида `a1_100`). */
    const val MONET_STYLE_CLASSIC = 1

    @JvmStatic
    fun getPreferences(): SharedPreferences = CoreConfig.getPreferences()

    // ---- Аватары ----

    /** Закругление аватарок: 0 — квадрат, [AVATAR_CORNERS_MAX] — круг. */
    @JvmField
    val avatarCorners =
        addConfig("MZAppearanceAvatarCorners", ConfigItem.configTypeInt, AVATAR_CORNERS_MAX)

    /** Единое закругление: форумы получают ту же форму аватарки, что и обычные чаты. */
    @JvmField
    val singleCornerRadius =
        addConfig("MZAppearanceSingleCornerRadius", ConfigItem.configTypeBool, false)

    // ---- Список чатов ----

    /** Мини-аватарки отправителей в списке чатов. */
    @JvmField
    val senderMiniAvatars =
        addConfig("MZAppearanceSenderMiniAvatars", ConfigItem.configTypeBool, true)

    /** Прятать эмодзи-статус рядом с заголовком шапки. Дефолт false, как в MZGram. */
    @JvmField
    val hideActionBarStatus =
        addConfig("MZAppearanceHideActionBarStatus", ConfigItem.configTypeBool, false)

    /**
     * Текст заголовка списка чатов: 0 — имя приложения, 1 — username, 2 — имя,
     * 3 — «Чаты». Только UI.
     */
    @JvmField
    val titleText =
        addConfig("MZAppearanceTitleText", ConfigItem.configTypeInt, 0)

    /**
     * Какой набор темы Monet берут «Monet Light/Dark/AMOLED».
     *
     * Наборы отличаются словарём токенов, а не механикой: [MONET_STYLE_TELEMONE] собран
     * из ролей Material 3, [MONET_STYLE_CLASSIC] — из тональных палитр базы форка. На части
     * прошивок роли M3 система отдаёт со своими поправками, и второй набор выглядит ровнее.
     */
    @JvmField
    val monetStyle =
        addConfig("MZAppearanceMonetStyle", ConfigItem.configTypeInt, MONET_STYLE_TELEMONE)

    /**
     * MZGram: the colour the Material You themes are made from. 0 takes the colours of the
     * wallpaper (as Android does); any other value is an ARGB seed colour the person picked, and
     * the five tonal palettes are made from it the way Android makes them (tonal spot).
     */
    @JvmField
    val monetSeed = addConfig("MZMonetSeed", ConfigItem.configTypeInt, 0)

    /** Суффикс файла темы для выбранного набора: пустой для Telemone. */
    @JvmStatic
    fun monetAssetSuffix(): String = when (monetStyle.Int()) {
        MONET_STYLE_CLASSIC -> "_gram"
        else -> ""
    }

    // ---- Только UI: аналогов в MZGram нет, визуальный эффект пока не подключён ----

    /**
     * Квадратная («squircle») плавающая кнопка вместо круглой. Дефолт true, как в MZGram —
     * одно из самых заметных отличий экстеры из коробки.
     * Радиус считается как ceil(size * 16 / 56) dp, то есть 16 dp при кнопке 56 dp.
     */
    @JvmField
    val squareFab = addConfig("MZAppearanceSquareFab", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun squareFab(): Boolean {
        ensureLoaded()
        return squareFab.Bool()
    }

    /** Радиус скругления кнопки со стороной [size] dp. */
    @JvmStatic
    fun fabCornerRadius(size: Int): Int =
        if (squareFab()) Math.ceil((size * 16) / 56.0).toInt() else size / 2

    /** Заголовок ActionBar по центру. Дефолт false, как в MZGram (BooleanPref(0)). */
    private val centerTitle = addConfig("MZAppearanceCenterTitle", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun centerTitle(): Boolean {
        ensureLoaded()
        return ExtConfig.centerActionBarTitle.Bool()
    }

    @JvmStatic
    fun setCenterTitle(value: Boolean) {
        ExtConfig.centerActionBarTitle.setConfigBool(value)
        ExtConfig.centerActionBarTitleType.setConfigInt(if (value) 1 else 0)
    }

    /** «Gooey»-анимация аватарки при оттягивании шапки профиля. Дефолт true, как в MZGram. */
    @JvmField
    val gooeyAvatarAnimation =
        addConfig("MZAppearanceGooeyAvatarAnimation", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun gooeyAvatarAnimation(): Boolean {
        ensureLoaded()
        return gooeyAvatarAnimation.Bool()
    }

    /**
     * Индивидуальные темы и обои в чатах.
     *
     * Выключено — приложение не применяет тему и обои, выставленные для
     * конкретного диалога, и везде остаётся общая тема. MZGram гейтит этим
     * ключом две точки: ChatActivity.setupChatTheme (12.9.0:12501) и
     * ChatThemeController.getDialogWallpaper (12.9.0:950).
     *
     * По умолчанию включено — как в MZGram: иначе обновление приложения
     * молча погасило бы у всех уже настроенные темы чатов.
     */
    @JvmField
    val customThemes =
        addConfig("MZAppearanceCustomThemes", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun customThemes(): Boolean {
        ensureLoaded()
        return customThemes.Bool()
    }

    /** Радиус карточек-секций, dp. Применяется ко всем спискам через RecyclerListView.setSections(). */
    @JvmField
    val sectionRadius =
        addConfig("MZAppearanceSectionRadius", ConfigItem.configTypeInt, 20)

    /** Отдельные заголовки секций. Только UI. */
    @JvmField
    val separateHeaders =
        addConfig("MZAppearanceSeparateHeaders", ConfigItem.configTypeBool, true)

    /** Стиль разделителя: 0 — скрыт, 1 — линия, 2 — сегменты. 0/1 привязаны к ExtConfig.hideDividers, 2 — только UI. */
    @JvmField
    val dividerStyle =
        addConfig("MZAppearanceDividerStyle", ConfigItem.configTypeInt, 1)

    /** Стиль стеклянного контура: 0 — блик, 1 — сплошной, 2 — скрыт. Только UI. */
    @JvmField
    val glassOutlineStyle =
        addConfig(object : ConfigItem("MZAppearanceGlassOutlineStyle", ConfigItem.configTypeInt, 0) {
            override fun setConfigInt(v: Int) {
                val changed = Int() != v
                super.setConfigInt(v)
                if (changed) GlassOutlineStyle.dispatchChange()
            }
        })

    /** Стеклянное меню сообщения. Дефолт true, как в MZGram (BooleanPref(1)). */
    @JvmField
    val glassMessageMenu =
        addConfig("MZAppearanceGlassMessageMenu", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun glassMessageMenu(): Boolean {
        ensureLoaded()
        return glassMessageMenu.Bool()
    }

    @JvmStatic
    fun glassOutlineStyle(): Int {
        ensureLoaded()
        return glassOutlineStyle.Int()
    }

    // ---- Material Design 3 ----
    // switchStyle и sliderStyle уже есть у MZGram (ExtConfig, дефолт 2 = MD3) — не дублируем.

    /** M3-индикаторы загрузки. Дефолт true, как в MZGram. */
    @JvmField
    val newLoadingStyle =
        addConfig("MZAppearanceNewLoadingStyle", ConfigItem.configTypeBool, true)

    /** M3 chat header. MZGram: on by default, MZGram is Material throughout. */
    @JvmField
    val newChatHeaderStyle =
        addConfig("MZAppearanceNewChatHeaderStyle", ConfigItem.configTypeBool, true)

    /** M3 bottom navigation bar. MZGram: on by default. */
    @JvmField
    val newNavigationBarStyle =
        addConfig("MZAppearanceNewNavigationBarStyle", ConfigItem.configTypeBool, true)

    /** M3 list items. MZGram: on by default. */
    @JvmField
    val m3ListItems =
        addConfig("MZAppearanceM3ListItems", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun m3ListItems(): Boolean {
        ensureLoaded()
        return m3ListItems.Bool()
    }

    @JvmStatic
    fun newLoadingStyle(): Boolean {
        ensureLoaded()
        return newLoadingStyle.Bool()
    }

    @JvmStatic
    fun newChatHeaderStyle(): Boolean {
        ensureLoaded()
        return newChatHeaderStyle.Bool()
    }

    @JvmStatic
    fun newNavigationBarStyle(): Boolean {
        ensureLoaded()
        return newNavigationBarStyle.Bool()
    }

    @JvmField
    val md3Player =
        addConfig("MZAppearanceMd3Player", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun md3Player(): Boolean {
        ensureLoaded()
        return md3Player.Bool()
    }

    @JvmField
    val md3MiniPlayer =
        addConfig("MZAppearanceMd3MiniPlayer", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun md3MiniPlayer(): Boolean {
        ensureLoaded()
        return md3MiniPlayer.Bool()
    }

    /**
     * MZGram: Material surfaces. Panels drawn as liquid glass or blur (search bar, folder
     * strip, bottom bar, chat header and input, menus) become solid Material surfaces.
     * LiteMode reports blur and liquid glass as off while this is on.
     */
    @JvmField
    val materialSurfaces =
        addConfig("MZMaterialSurfaces", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun materialSurfaces(): Boolean {
        if (!configLoaded) {
            if (ApplicationLoader.applicationContext == null) return true
            loadConfig(false)
        }
        return materialSurfaces.Bool()
    }

    /** Opacity for a glass panel colour: fully solid with Material surfaces. */
    @JvmStatic
    fun glassAlpha(liquidGlass: Float, frosted: Float): Float {
        if (materialSurfaces()) return 1f
        return if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) liquidGlass else frosted
    }

    /** MZGram: Material dialogs with 28 dp corners. */
    @JvmField
    val materialDialogs =
        addConfig("MZMaterialDialogs", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun materialDialogs(): Boolean {
        ensureLoaded()
        return materialDialogs.Bool()
    }

    /**
     * MZGram: Material bars. Headers are full-width Material top bars with plain icons.
     * Off: floating pills and round buttons. The chat input has its own switch, [materialInput].
     */
    @JvmField
    val materialBars =
        addConfig("MZMaterialBars", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun materialBars(): Boolean {
        if (!configLoaded) {
            if (ApplicationLoader.applicationContext == null) return true
            loadConfig(false)
        }
        return materialBars.Bool()
    }

    /**
     * MZGram: the chat input sits on a docked full-width bottom bar,
     * with the channel and selection buttons on the same bar. Off: the floating input pill.
     */
    @JvmField
    val materialInput =
        addConfig("MZMaterialInput", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun materialInput(): Boolean {
        if (!configLoaded) {
            if (ApplicationLoader.applicationContext == null) return true
            loadConfig(false)
        }
        return materialInput.Bool()
    }

    // ---- MZGram: chat list header ----

    const val HEADER_STANDARD = 0
    const val HEADER_COMPACT = 1

    /**
     * Chat list header. Compact: the menu button, the folder tabs and the search button share
     * the top bar, and the app title is not shown.
     */
    @JvmField
    val dialogsHeaderStyle =
        addConfig("MZDialogsHeaderStyle", ConfigItem.configTypeInt, HEADER_STANDARD)

    /** Search is removed from the chat list completely: no search bar and no search button. */
    @JvmField
    val hideDialogsSearch =
        addConfig("MZHideDialogsSearch", ConfigItem.configTypeBool, false)

    /** The folder tabs are not shown. Folders still open from a long press on the Chats tab. */
    @JvmField
    val hideFolderTabs =
        addConfig("MZHideFolderTabs", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun compactDialogsHeader(): Boolean {
        ensureLoaded()
        return dialogsHeaderStyle.Int() == HEADER_COMPACT
    }

    @JvmStatic
    fun hideDialogsSearch(): Boolean {
        ensureLoaded()
        return hideDialogsSearch.Bool()
    }

    @JvmStatic
    fun hideFolderTabs(): Boolean {
        ensureLoaded()
        return hideFolderTabs.Bool()
    }

    @JvmField
    val lrclibAllowed =
        addConfig("MZPlayerLrclibAllowed", ConfigItem.configTypeBool, false)

    @JvmField
    val playerCoverStyle = addConfig("MZPlayerCoverStyle", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerCoverSize = addConfig("MZPlayerCoverSize", ConfigItem.configTypeInt, 100)

    @JvmField
    val playerCoverRadius = addConfig("MZPlayerCoverRadius", ConfigItem.configTypeInt, 28)

    @JvmField
    val playerDiscSpin = addConfig("MZPlayerDiscSpin", ConfigItem.configTypeBool, true)

    @JvmField
    val playerCoverSwipe = addConfig("MZPlayerCoverSwipe", ConfigItem.configTypeBool, true)

    @JvmField
    val playerTitleAlign = addConfig("MZPlayerTitleAlign", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerTitleSize = addConfig("MZPlayerTitleSize", ConfigItem.configTypeInt, 1)

    @JvmField
    val playerTitleAbove = addConfig("MZPlayerTitleAbove", ConfigItem.configTypeBool, false)

    @JvmField
    val playerOrder = addConfig("MZPlayerOrder", ConfigItem.configTypeString, "")

    @JvmField
    val playerShowActions = addConfig("MZPlayerShowActions", ConfigItem.configTypeBool, true)

    @JvmField
    val playerShowModes = addConfig("MZPlayerShowModes", ConfigItem.configTypeBool, true)

    @JvmField
    val playerShowLike = addConfig("MZPlayerShowLike", ConfigItem.configTypeBool, true)

    @JvmField
    val playerShowSource = addConfig("MZPlayerShowSource", ConfigItem.configTypeBool, true)

    @JvmField
    val playerWavy = addConfig("MZPlayerWavy", ConfigItem.configTypeBool, true)

    @JvmField
    val playerControlsSize = addConfig("MZPlayerControlsSize", ConfigItem.configTypeInt, 1)

    @JvmField
    val playerActionsStyle = addConfig("MZPlayerActionsStyle", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerColors = addConfig("MZPlayerColors", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerBackground = addConfig("MZPlayerBackground", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerCoverExtend = addConfig("MZPlayerCoverExtend", ConfigItem.configTypeBool, true)

    @JvmField
    val playerMiniCover = addConfig("MZPlayerMiniCover", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerMiniShape = addConfig("MZPlayerMiniShape", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerMiniProgress = addConfig("MZPlayerMiniProgress", ConfigItem.configTypeInt, 0)

    @JvmField
    val playerMiniColors = addConfig("MZPlayerMiniColors", ConfigItem.configTypeInt, 1)

    @JvmField
    val playerMiniCompact = addConfig("MZPlayerMiniCompact", ConfigItem.configTypeBool, false)

    @JvmField
    val playerMiniArtist = addConfig("MZPlayerMiniArtist", ConfigItem.configTypeBool, true)

    @JvmField
    val playerMiniPrevious = addConfig("MZPlayerMiniPrevious", ConfigItem.configTypeBool, false)

    @JvmField
    val playerMiniNext = addConfig("MZPlayerMiniNext", ConfigItem.configTypeBool, true)

    @JvmField
    val playerMiniClose = addConfig("MZPlayerMiniClose", ConfigItem.configTypeBool, true)

    @JvmField
    val playerMiniTop = addConfig("MZPlayerMiniTop", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun playerStyleItems(): List<ConfigItem> {
        ensureLoaded()
        return listOf(
            playerCoverStyle, playerCoverSize, playerCoverRadius, playerDiscSpin, playerCoverSwipe,
            playerTitleAlign, playerTitleSize, playerTitleAbove, playerOrder, playerShowActions,
            playerShowModes, playerShowLike, playerShowSource, playerWavy, playerControlsSize, playerActionsStyle,
            playerColors, playerBackground, playerCoverExtend, playerMiniCover, playerMiniShape, playerMiniProgress,
            playerMiniColors, playerMiniCompact, playerMiniArtist, playerMiniPrevious, playerMiniNext,
            playerMiniClose
        )
    }

    /** Широкая нижняя панель как в Telegram iOS. Уступает M3-панели, если включены обе. */
    @JvmField
    val iosNavigationBarStyle =
        addConfig("MZAppearanceIosNavigationBarStyle", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun iosNavigationBarStyle(): Boolean {
        ensureLoaded()
        return iosNavigationBarStyle.Bool()
    }

    @JvmField
    val profileMusicCard =
        addConfig("MZAppearanceProfileMusicCard", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun profileMusicCard(): Boolean {
        ensureLoaded()
        return profileMusicCard.Bool()
    }

    @JvmField
    val iosChatHeader =
        addConfig("MZAppearanceIosChatHeader", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun iosChatHeader(): Boolean {
        ensureLoaded()
        return iosChatHeader.Bool()
    }

    // ---- AI-функции Telegram ----

    /** Прячет кнопку AI-редактора в поле ввода, вложениях и подписи к медиа. */
    @JvmField
    val hideAiEditor =
        addConfig("MZAppearanceHideAiEditor", ConfigItem.configTypeBool, false)

    /** Прячет кнопку «саммари» на сообщении. */
    @JvmField
    val hideMessageSummary =
        addConfig("MZAppearanceHideMessageSummary", ConfigItem.configTypeBool, false)

    /** Прячет блок «Cocoon AI Summary» в Instant View. */
    @JvmField
    val hideIvSummary =
        addConfig("MZAppearanceHideIvSummary", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun hideAiEditor(): Boolean {
        ensureLoaded()
        return hideAiEditor.Bool()
    }

    @JvmStatic
    fun hideMessageSummary(): Boolean {
        ensureLoaded()
        return hideMessageSummary.Bool()
    }

    @JvmStatic
    fun hideIvSummary(): Boolean {
        ensureLoaded()
        return hideIvSummary.Bool()
    }

    // ---- Боковое меню ----

    /** Своя шторка бокового меню вместо стоковой. Дефолт false, как в MZGram. */
    @JvmField
    val navigationDrawer =
        addConfig("MZAppearanceNavigationDrawer", ConfigItem.configTypeBool, false)

    /** Иммерсивная анимация открытия шторки. Дефолт false, как в MZGram. */
    @JvmField
    val immersiveDrawerAnimation =
        addConfig("MZAppearanceImmersiveDrawer", ConfigItem.configTypeBool, false)

    /** Порядок и видимость пунктов бокового меню, сериализованный список id. */
    @JvmField
    val mainMenuLayout =
        addConfig("MZAppearanceMainMenuLayout", ConfigItem.configTypeString, "")

    // ---- Лента ----

    /**
     * Показывать ленту нижней вкладкой — на месте вкладки «Контакты».
     * Дефолт false, как в MZGram (BooleanPref(0)).
     */
    @JvmField
    val showFeedTab = addConfig("MZAppearanceShowFeedTab", ConfigItem.configTypeBool, false)

    @JvmStatic
    fun showFeedTab(): Boolean {
        ensureLoaded()
        return showFeedTab.Bool()
    }

    /** Счётчик непрочитанных постов на вкладке ленты. */
    @JvmField
    val showFeedUnreadCounter =
        addConfig("MZAppearanceFeedUnreadCounter", ConfigItem.configTypeBool, true)

    @JvmStatic
    fun showFeedUnreadCounter(): Boolean {
        ensureLoaded()
        return showFeedUnreadCounter.Bool()
    }

    @JvmStatic
    fun navigationDrawer(): Boolean {
        ensureLoaded()
        return navigationDrawer.Bool()
    }

    @JvmStatic
    fun immersiveDrawerAnimation(): Boolean {
        ensureLoaded()
        return immersiveDrawerAnimation.Bool()
    }

    // ---- Геттеры для Java (в том числе для горячих мест отрисовки) ----

    @JvmStatic
    fun avatarCorners(): Int {
        ensureLoaded()
        return avatarCorners.Int()
    }

    @JvmStatic
    fun singleCornerRadius(): Boolean {
        ensureLoaded()
        return singleCornerRadius.Bool()
    }

    @JvmStatic
    fun senderMiniAvatars(): Boolean {
        ensureLoaded()
        return senderMiniAvatars.Bool()
    }

    /** Текст заголовка списка чатов: 0 — имя приложения, 1 — username, 2 — имя, 3 — «Чаты». */
    @JvmStatic
    fun titleText(): Int {
        ensureLoaded()
        return titleText.Int()
    }

    const val TITLE_TEXT_CUSTOM = 4

    private const val TITLE_TEXT_MIGRATED = "MZAppearanceTitleTextMigrated"

    @JvmStatic
    fun titleTextScrolls(): Boolean = when (titleText()) {
        1, 2, TITLE_TEXT_CUSTOM -> true
        else -> false
    }

    @JvmStatic
    fun folderNameAsTitle(): Boolean = CoreConfig.tabsTitleType.Int() == CoreXConfig.TITLE_TYPE_ICON

    /** Прятать ли эмодзи-статус в шапке списка чатов. */
    @JvmStatic
    fun hideActionBarStatus(): Boolean {
        ensureLoaded()
        return hideActionBarStatus.Bool()
    }

    /** true, если аватарки должны остаться обычными кругами — быстрый выход из хот-пути. */
    @JvmStatic
    fun avatarCornersDefault(): Boolean = avatarCorners() >= AVATAR_CORNERS_MAX

    /**
     * Квадратность аватарки: 0 — круг, 1 — квадрат.
     *
     * Обратная величина к [avatarCorners] и ровно то, что MZGram зовёт
     * `getAvatarSquareness()`. Нужна там, где геометрия зависит от формы —
     * например, онлайн-точку на квадратной аватарке надо уводить в угол,
     * иначе она наползает на картинку.
     */
    @JvmStatic
    fun avatarSquareness(): Float =
        1f - (avatarCorners().coerceIn(0, AVATAR_CORNERS_MAX).toFloat() / AVATAR_CORNERS_MAX)

    /**
     * Смещение онлайн-точки от края аватарки.
     * У круга — заданное значение, у квадрата — по диагонали от угла.
     */
    @JvmStatic
    fun onlineDotOffset(base: Float, radius: Float): Float =
        base + ((radius / Math.sqrt(2.0)).toFloat() - base) * avatarSquareness()

    // ---- Секции настроек ----

    /** Радиус скругления карточек-секций, dp. 0 — острые углы (как сток). */
    @JvmStatic
    fun sectionRadius(): Int {
        ensureLoaded()
        return sectionRadius.Int()
    }

    /** Отдельные заголовки секций (заголовок — своя карточка). */
    @JvmStatic
    fun separateHeaders(): Boolean {
        ensureLoaded()
        return separateHeaders.Bool()
    }

    /**
     * Выносить ли заголовки секций из карточки — с учётом стиля разделителя.
     *
     * В режиме «Сегменты» каждая строка сама по себе карточка, и заголовок
     * внутри неё выглядел бы чужеродно, поэтому MZGram включает вынос
     * принудительно (MZConfig.getSectionsSeparatedHeaders: SEGMENTS || pref).
     */
    @JvmStatic
    fun sectionsSeparatedHeaders(): Boolean {
        return separateHeaders() || dividerStyle() == DIVIDER_SEGMENTS || m3ListItems()
    }

    @JvmStatic
    fun sectionsSeparatedHeadersForced(): Boolean {
        return dividerStyle() == DIVIDER_SEGMENTS || m3ListItems()
    }

    /** Стиль разделителя внутри карточки: 0 — скрыт, 1 — линия, 2 — сегменты. */
    @JvmStatic
    fun dividerStyle(): Int {
        ensureLoaded()
        return dividerStyle.Int()
    }

    const val DIVIDER_HIDDEN = 0
    const val DIVIDER_LINE = 1
    const val DIVIDER_SEGMENTS = 2

    /**
     * Кэш для [dividerHidden]. `Theme.getColor` — самый горячий путь отрисовки,
     * ходить туда через [ensureLoaded] на каждый вызов нельзя.
     * Обновляется из [invalidateDividerStyle] при смене настройки.
     */
    @Volatile
    private var dividerHiddenCache: Boolean? = null

    /**
     * Гасить ли `key_divider` целиком. У MZGram прозрачный цвет отдаётся во всех режимах,
     * кроме [DIVIDER_LINE] — поэтому стоковые `drawLine(..., Theme.dividerPaint)`
     * перестают рисовать без правки самих ячеек
     */
    @JvmStatic
    fun dividerHidden(): Boolean {
        val cached = dividerHiddenCache
        if (cached != null) return cached
        val value = try {
            dividerStyle() != DIVIDER_LINE || m3ListItems()
        } catch (e: Exception) {
            false
        }
        dividerHiddenCache = value
        return value
    }

    /** Звать при смене стиля разделителя. */
    @JvmStatic
    fun invalidateDividerStyle() {
        dividerHiddenCache = null
    }

    /**
     * Радиус закругления для аватарки со стороной [size] пикселей.
     * При максимуме слайдера возвращает size / 2, то есть круг.
     */
    @JvmStatic
    @JvmOverloads
    fun getAvatarCorners(size: Float, cornerType: Int = CORNER_TYPE_DEFAULT, hasStories: Boolean = false): Int {
        val corners = avatarCorners()
        if (corners <= 0) return 0
        var value = size * corners / (AVATAR_CORNERS_MAX * 2.0)
        if (hasStories) {
            value -= AndroidUtilities.dpf2(2.5f)
        }
        if (!singleCornerRadius()) {
            when (cornerType) {
                CORNER_TYPE_FORUM -> value = value.toInt() * 42.0 / 64.0
                CORNER_TYPE_COMMUNITY -> value = value * 40.0 / 72.0
            }
        }
        if (value <= 0) return 0
        return Math.ceil(value).toInt()
    }

    const val CORNER_TYPE_DEFAULT = 0
    const val CORNER_TYPE_FORUM = 1
    const val CORNER_TYPE_COMMUNITY = 2

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    private fun addConfig(item: ConfigItem): ConfigItem {
        configs.add(item)
        return item
    }

    @JvmStatic
    fun ensureLoaded() {
        if (!configLoaded) loadConfig(false)
    }

    @JvmStatic
    fun init() {
        loadConfig(false)
    }

    @JvmStatic
    fun loadConfig(force: Boolean) {
        synchronized(sync) {
            if (configLoaded && !force) return
            if (ApplicationLoader.applicationContext == null) return
            val preferences = getPreferences()
            for (item in configs) {
                try {
                    when (item.type) {
                        ConfigItem.configTypeBool ->
                            item.value = preferences.getBoolean(item.key, item.defaultValue as Boolean)

                        ConfigItem.configTypeInt ->
                            item.value = preferences.getInt(item.key, item.defaultValue as Int)

                        ConfigItem.configTypeLong ->
                            item.value = preferences.getLong(item.key, item.defaultValue as Long)

                        ConfigItem.configTypeFloat ->
                            item.value = preferences.getFloat(item.key, item.defaultValue as Float)

                        ConfigItem.configTypeString ->
                            item.value = preferences.getString(item.key, item.defaultValue as String?)
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
            configLoaded = true
        }
        migrateLegacyKeys()
    }

    private fun migrateLegacyKeys() {
        val legacyHidden = getPreferences().getBoolean("HideDividers", false)
        if (legacyHidden && dividerStyle.Int() != DIVIDER_HIDDEN) {
            dividerStyle.setConfigInt(DIVIDER_HIDDEN)
        } else if (!legacyHidden && dividerStyle.Int() == DIVIDER_HIDDEN) {
            ExtConfig.hideDividers.setConfigBool(true)
        }
        migrateLegacyTitleName()
        migrateCustomTitle()
        migrateCenterTitle()
        migrateIosChatHeader()
        migrateModernStyles()
        migrateDecorations()
        migrateMaterialInput()
    }

    // MZGram: the chat input used to follow the bars switch; keep what the user had there
    private fun migrateMaterialInput() {
        if (getPreferences().contains(materialInput.key)) return
        materialInput.setConfigBool(materialBars.Bool())
    }

    private fun migrateCenterTitle() {
        if (centerTitle.Bool()) {
            setCenterTitle(true)
            centerTitle.setConfigBool(false)
        }
        val type = ExtConfig.centerActionBarTitleType.Int()
        if (type == 2 || type == 3) {
            ExtConfig.centerActionBarTitleType.setConfigInt(1)
        }
    }

    private fun migrateIosChatHeader() {
        if (getPreferences().contains(iosChatHeader.key)) return
        iosChatHeader.setConfigBool(ExtConfig.centerActionBarTitle.Bool())
    }

    private fun migrateModernStyles() {
        if (ExtConfig.switchStyle.Int() == 1) {
            ExtConfig.switchStyle.setConfigInt(2)
        }
        // MZGram: Material 3 switches, once, over the old default (the ring style); a later pick stays
        val preferences = getPreferences()
        if (!preferences.getBoolean("mz_m3_switch_v1", false)) {
            if (ExtConfig.switchStyle.Int() == 2) {
                ExtConfig.switchStyle.setConfigInt(3)
            }
            preferences.edit().putBoolean("mz_m3_switch_v1", true).apply()
        }
        if (ExtConfig.sliderStyle.Int() == 1) {
            ExtConfig.sliderStyle.setConfigInt(2)
        }
    }

    private fun migrateDecorations() {
        val actionBar = CoreConfig.actionBarDecoration.Int()
        val chat = ExtConfig.chatDecoration.Int()
        val snow = if (actionBar == 1 || chat == 1) 1 else 0
        if (actionBar != snow) {
            CoreConfig.actionBarDecoration.setConfigInt(snow)
        }
        if (chat != snow) {
            ExtConfig.chatDecoration.setConfigInt(snow)
        }
    }

    private const val LEGACY_DEFAULT_TITLE = "MZGram X"

    private fun migrateLegacyTitleName() {
        if (ExtConfig.customTitle.String() != LEGACY_DEFAULT_TITLE) return
        ExtConfig.customTitle.setConfigString(ExtConfig.customTitle.defaultValue as String)
        if (titleText.Int() == TITLE_TEXT_CUSTOM) {
            titleText.setConfigInt(0)
        }
    }

    private fun migrateCustomTitle() {
        val preferences = getPreferences()
        if (preferences.getBoolean(TITLE_TEXT_MIGRATED, false)) return
        if (titleText.Int() == 0) {
            if (ExtConfig.customTitleUserName.Bool()) {
                titleText.setConfigInt(2)
            } else if (ExtConfig.customTitle.String() != ExtConfig.customTitle.defaultValue) {
                titleText.setConfigInt(TITLE_TEXT_CUSTOM)
            }
        }
        ExtConfig.customTitleUserName.setConfigBool(false)
        preferences.edit().putBoolean(TITLE_TEXT_MIGRATED, true).apply()
    }

    /** Сбрасывает настройки экрана Appearance к значениям по умолчанию. */
    @JvmStatic
    fun reset() {
        synchronized(sync) {
            val editor = getPreferences().edit()
            for (item in configs) {
                editor.remove(item.key)
                item.value = item.defaultValue
            }
            editor.apply()
        }
        GlassOutlineStyle.dispatchChange()
    }
}
