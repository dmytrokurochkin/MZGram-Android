# STATE_SNAPSHOT: MZGram-Android, w-kb-all (2026-11-09, після №58)

Читати першим після /clear разом із `progress-kb-all.md` (той самий каталог). Повні рядки функцій, карта функцій і повний розділ «Для власника» лежать у progress-kb-all.md; цей файл їх підсумовує.

## Порядок роботи (власник, 2026-11-09)
- Одна функція за раз із черги progress-kb-all.md («Черга за складністю»), зверху вниз.
- Функцію доробляти повністю: код, рядки в 12 мовах, коміт, пуш, рядок у progress (статус done/partial і SHA), «Стан на», «Для власника», коміт мета-репо (без пушу).
- Після кожної функції переписати цей STATE_SNAPSHOT.md, закомітити мета-репо і зупинитися з повідомленням: «функція готова, STATE_SNAPSHOT.md записано, коміт <хеш>». Потім власник робить /clear.

## 1) Completed in Last Cycle

### Репозиторії й місця
- Код: `C:/TBuild/mza-trial`, гілка `w-kb-all` (origin dmytrokurochkin/MZGram-Android). Пуш лише `git push origin HEAD:refs/heads/w-kb-all`. Push у w-kb-all CI не запускає.
- Мета-репо: `C:/Users/mrmoz/Documents/My_GitHub/My_GitHub/MZGram` (progress-kb-all.md, STATE_SNAPSHOT.md), гілка main, комітити, не пушити.
- База знань: `C:/Users/mrmoz/Downloads/Telegram Functionality/features` (README, INDEX, _architecture, _conflicts, домени). Референси: `C:/Users/mrmoz/Downloads/Telegram Functionality/_source/TMessagesProj/src/main` (exteraless `java/app/exteraless`, `kotlin/app/exteraless`; Nagram X `java/tw/nekomimi`, `xyz/nextalone`), `_mercury_source`, `icon_packs_files` (18 .icons), `plugins_files`.
- Скрипти: scratchpad `C:/Users/mrmoz/AppData/Local/Temp/claude/d942a8accedc487d/85bf5ad6-375d-43d0-b572-cf7ea99fdca3/scratchpad/u7/` (addstr.py <json>: додає рядки в 12 мов, апостроф екранує; strcheck.py: перевірка рядків, має дати 0; stateNN.py, pNN_code.py: правки з assert count==1; progrow.py). Якщо scratchpad після /clear інший, скрипти треба відтворити (addstr.py: json {name: {en, uk, ru, be, de, es, fr, pl, pt, tr, kk, ar}} → values*/mzgram_strings.xml перед `</resources>`, `&`→`&amp;`, `<`→`&lt;`, `'`→`\'`, зберігати CRLF/LF файлу).
- Локальні тести текстів: `cd C:/TBuild/mza-trial && python -X utf8 -m pytest -q .github/scripts/test_strings.py .github/scripts/test_settings_rows.py .github/scripts/test_texts.py` (зараз 22 passed).

### Коміти цього циклу (усі запушені в w-kb-all)
- №55 extended pins `c594ed503`; №56 pill bar `f64bac9f6`; №29 status pills `5385471b9`; №30 telemetry pills `b8806e259`; №57 icon packs `5b3a01bb2`; **№58 Monet themes `92259a6f5`** (HEAD w-kb-all).
- Мета-репо: d699bfa (№57) і коміт «Progress kb-all: Monet themes done» з цим файлом.

### №58 appearance.monet-themes (done, 92259a6f5)
Змінені/нові файли:
- нові: `TMessagesProj/src/main/java/org/telegram/messenger/mzgram/MZGramMonet.java`; `TMessagesProj/src/main/assets/mzgram_monet_light.attheme`, `mzgram_monet_dark.attheme`, `mzgram_monet_amoled.attheme`, `mzgram_monet_light_classic.attheme`, `mzgram_monet_dark_classic.attheme`, `mzgram_monet_amoled_classic.attheme` (з Nagram X monet_*.attheme і monet_*_gram.attheme, LF); тест `TMessagesProj_AppTests/src/androidTest/kotlin/org/telegram/messenger/mzgram/test/MZGramMonetTest.kt` (lab_roundTrip, tokens_shadesRolesAndTones, tokens_fixedChangedAndUnknown, harmonize_turnsAtMostFifteenDegrees, names_andFiles).
- змінені: `ui/ActionBar/Theme.java`, `ui/ThemeActivity.java`, `ui/LaunchActivity.java`, `messenger/mzgram/MZGramConfig.java`, `ui/mzgram/MZGramSettingsActivity.java`, `res/values*/mzgram_strings.xml` (12 мов).

`public final class MZGramMonet` (messenger/mzgram):
- константи: `LIGHT="MZGram Monet Light"`, `DARK="MZGram Monet Dark"`, `AMOLED="MZGram Monet AMOLED"`, `STYLE_ANDROID=0`, `STYLE_TELEGRAM=1`, `int[] SHADES={0,10,50,100..1000}`, `int[] SHADE_TONES={100,99,95,90,80,70,60,50,40,30,20,10,0}`.
- `static boolean available()` (SDK>=31), `isOn()` (monetThemes && available), `isMonet(String)`, `isMonetDark(String)`, `isMonetAsset(String)` (префікс `mzgram_monet_`), `String title(String name)` (R.string.MZGramMonetLight/Dark/Black або null), `String assetName(int index, int style)`, `String styleTitle(int)`.
- `static void addThemes()`, `removeThemes()`, `setStyle(int)`, `refreshIfChanged()`, `File exportFile(Theme.ThemeInfo)`, `Integer color(String assetName, String value, String key)`.
- `public interface Palette { Integer shade(int palette, int shade); Integer role(String role, boolean dark); }` (палітри 0-2 accent1-3, 3-4 neutral1-2).
- чисті методи для тестів: `Integer resolve(String value, String key, Palette)`, `Integer tone(Palette, int index, int tone)`, `int darken(int, int)`, `int harmonize(int design, int source)`, `double[] lab(int)`, `double[] lch(int)`, `int fromLch(double l, double c, double h, int alpha)`, `int fromLab(double l, double a, double b, int alpha)`.
- Токени: константи (white, black, transparent, error_*, monetRed*, monetGreenCall, mBlack/mWhite), підлаштовані (mRed*, mGreen*, monetAvatar*, monetYellow/Green), `aN_shade`/`nN_shade`, ролі `<role>_light|_dark` (на API 34 системні `system_<role>_<mode>` з перевіркою довіри), фіксовані ролі, `primary|secondary|tertiary|neutral|neutral_variant_<tone>`, сіди `blue|red|green|orange|violet|pink|cyan_<tone>`, `<token>_<percent>` (HSL-затемнення), модифікатори `(s=,l=,a=,t=,c=)`, префікс `monet_`. Посилання при сірому акценті синіють (LINK_KEYS).
- Хуки Telegram (усі з `// MZGram:` і повним іменем): Theme статична ініціалізація після `themesDict.put("Night"...)` → `MZGramMonet.addThemes()`; `ThemeInfo.getName()` → `MZGramMonet.title`; `ThemeInfo.isDark()` → `isMonet/isMonetDark`; `getThemeFileValues` → `MZGramMonet.color(assetName, param, key)` перед `Utilities.parseInt`; нові `public static ThemeInfo mzgramCreateTheme(String name, String assetName, int sortIndex)`, `mzgramAddTheme(ThemeInfo)`, `mzgramRemoveTheme(ThemeInfo)` (активна тема Monet → стандартна денна/нічна, needSetDayNightTheme). ThemeActivity експорт → `MZGramMonet.exportFile`. LaunchActivity.onResume → `MZGramMonet.refreshIfChanged()`.
- MZGramConfig: `monetThemes=false`, `monetStyle=0`, `toggleMonetThemes()`, `setMonetStyle(int)`.
- MZGramSettingsActivity (Interface, після наборів іконок, лише якщо available()): BUTTON 174 `BUTTON_MONET_THEMES` (check), 175 `BUTTON_MONET_STYLE` (діалог `chooseMonetStyle()`), 176 `BUTTON_MONET_PICK` (ThemeActivity THEME_TYPE_BASIC), тінь MZGramMonetThemesInfo.
- Рядки (9): MZGramMonetThemes, MZGramMonetThemesInfo, MZGramMonetStyle, MZGramMonetStyleAndroid, MZGramMonetStyleTelegram, MZGramMonetPick, MZGramMonetLight, MZGramMonetDark, MZGramMonetBlack.

### №57 appearance.icon-packs (done, 5b3a01bb2)
- `messenger/mzgram/MZGramIconPacks` (public final): EXTENSION=".icons", BUILTIN_NONE/SOLAR/REMIX=0/1/2, TINT_AUTO/ON/OFF=0/1/2, ERROR_NONE/READ/NOT_PACK/NO_ICONS/TOO_LARGE=0..4; ліміти архів 64 МБ, файл 4 МБ, разом 128 МБ, metadata 1 МБ, MAX_ICONS 5000; prefs `mzgram_iconpacks` (order, active, builtin, tint_<key>, detected_<key>); набори в `filesDir/mzgram_iconpacks/<key>/`. Класи `Pack` (key, id, name, author, version, Boolean declaredTintable, count(), has(String)), `Picked` (file, pack, error), `interface Source { Drawable original(int resId, int density, Resources.Theme); Resources resources(); }`. Методи: isActive, builtin, setBuiltin, builtinTitle, builtinAuthor, installed, isOn, setOn, setOrder(List<String>), tintMode, isTinted(Pack), setTinted, addListener, removeListener, changed, delete, readPicked(Uri, Utilities.Callback<Picked>), readFile(File, Callback<Picked>), discard(Picked), install(Picked, Callback<Boolean>), drawableFor(Source, int resId, int density, Theme); чисті parseMetadata(String, String key, File dir), keyFor, safeEntry, pictureType, isColorful(int[]), normalizeSvg, parseKeys.
- `messenger/mzgram/MZGramBuiltinIcons` (згенеровано): solar() 499 пар, remix() 498 пар; 778 векторів res/drawable/mzgram_*_solar.xml, mzgram_*_remix.xml.
- `ui/mzgram/MZGramIconsResources extends Resources implements MZGramIconPacks.Source`: `static Resources wrap(Resources base)`; хук LaunchActivity.getResources().
- `ui/mzgram/MZGramIconPacksActivity extends UniversalFragment`: REQUEST_CODE_PICK_PACK=8847, `rebuildScreens(BaseFragment)`, `openFromChat(BaseFragment, MessageObject)` (хук ChatActivity після MZGramPluginInstallSheet.openFromChat).
- MZGramConfig.iconPacks, toggleIconPacks; BUTTON 172 (check), 173 (екран). Тест MZGramIconPacksTest. 26 рядків.

### №56, №29, №30 пілюлі
- `messenger/mzgram/MZGramPills` (public final): ids GHOST=1, LAST_SEEN=2, PROXY=3, CACHE=4, CPU=5, RAM=6, NET_SPEED=7, DC_PING=8, int[] ALL; isKnown, parse, join, complete, order, setOrder, isShown, setShown, active, lastActive, setLastActive, reset, addListener, removeListener, changed, shortNumber(long).
- `ui/mzgram/pills/MZGramPill` (abstract FrameLayout): HEIGHT_DP=28; abstract pillId(), update(boolean), onTap(); refreshInterval(), addOptions(ItemOptions, BaseFragment), accentColor(), setIcon, setText, updateColors, onHold, setSelectedPill, isSelectedPill.
- `MZGramPillStackView` (MIN_SWIPE_ANGLE=25, static isSwipe(float,float), count, addPill, clear, setCurrent, updateColors); `MZGramPillStack` (static attach(FrameLayout, EditText, ResourcesProvider), static create(int, Context, ResourcesProvider), static titleOf(int), rebuild()); MZGramGhostPill, MZGramLastSeenPill, MZGramProxyPill, MZGramCachePill; `MZGramTelemetryPill` (abstract, INTERVAL=2000, read(), static cpuShare(long,long,int), static perSecond(long,long,long), вкладені Cpu, Ram, NetSpeed, DcPing); `ui/mzgram/MZGramPillsActivity`; тест MZGramPillsTest.kt. BUTTON 170-171.

### Зв'язки між функціями
- №58 Monet поряд із K-темами (MZGramDefaultThemes, рядок кольорових тем чату) і не змінює їх; №57 тонування наборів іконок іде через фільтри теми Telegram, тож тема Monet фарбує тоновані набори, кольорові (FixedBitmapDrawable) ні.
- №56 смуга пілюль несе пілюлі №29/№30; №54 бічне меню → №34 його вигляд; №53 фільтри → №4 виключення; плагіни: №42 рантайм (Chaquopy лише з mzgramPlugins) → №45 налаштування, №46 UI, №47 меню, №48 dev-server, №49 grant-store, №50 hook-gates, №51 install-flow, №52 журнал → чекають №63 lifecycle, №64 xposed-hooks, №65 permissions, №66 sink-gates, №67 сканер, №68 watchdog, №71 SDK.
- №40 player-styling-queue залежить від №60 m3-music-player.

### Зайняті id
- BUTTON MZGramSettingsActivity до 176 зайнято (161-162 фільтри повідомлень, 163-168 бічне меню і вигляд, 169 до 100 закріплених, 170-171 пілюлі, 172-173 набори іконок, 174-176 теми Monet); **вільні з 177**.
- Пілюлі 1-8. REQUEST_CODE 8846 (плагіни), 8847 (набори іконок). OPTION_MZGRAM у ChatActivity до 1014 (1014 = «Додати у фільтри»). Меню плагінів 1_900_000-1_900_202. ProfileActivity 1014 mzgram_shadow_ban, 1015 mzgram_filters_excluded. MZGramPluginsActivity рядки 1-5; MZGramPluginInfoActivity кнопки 1-10. Порт dev-сервера 42690. sortIndex тем: 1-5 Telegram, 6-8 Monet.
- DB_VERSION історії 7 (тест test_levels_reliable чекає 5, оновити у фазі тестів).

### Стан
- **done 75 з 101**; у черзі 14 рядків todo: №40, 59-71 (13 implement + 2 implement* серед них №61, №62). Збірку не запускали, інструментальні тести не запускали.

## 2) Active Architectural Rules (власник, не змінюються)
- Гілка w-kb-all; пуш лише `git push origin HEAD:refs/heads/w-kb-all`; без force push і переписування історії. Без мерджу в mzgram, без релізів і тегів без слова власника.
- Якість №1. Без субагентів. Одна функція за раз; після функції коміт і пуш, рядок у progress, STATE_SNAPSHOT.md, зупинка.
- НЕ ЛАМАТИ НАЯВНЕ: нічого наявного не прибирати, не перейменовувати, не міняти поведінку. Нове лише поряд, вимкнене за замовчуванням (OFF), окрема тема або перемикач. Якщо нове вимагає змінити наявне, не міняти, а записати в «Для власника». Наявні тести не видаляти й не послаблювати (лічильники пунктів/мов лише у фазі тестів із записом). Міграції бази лише додавальні.
- Кожен новий рядок у 12 мовах: values, uk, ru, be, de, es, fr, pl, pt-rBR, tr, kk, ar. CI-перевірка текстів (test_strings, test_texts, test_settings_rows) не послаблюється: переклад не дорівнює англійському, якщо не всі слова в SHARED_WORDS (список не розширювати); без літерального % і довгого тире; апостроф `\'`; рядок Telegram у класі MZGram лише з коментарем `// Telegram's own text: …`; LocaleController лише для рядків.
- GPL: зберігати ліцензійні заголовки й атрибуцію; наприкінці всієї роботи оновити FEATURES.md (однаковий в обох репо перед злиттям; Desktop не чіпати). До FEATURES додати: Mercurygram MgPins; exteraless пілюлі (PillStackController, PillStackView, BasePill, PillRegistry, PillStackSettingsActivity, Ghost/LastSeen/Proxy/Cache/Telemetry/Cpu/Ram/NetSpeed/DcPing); exteraless IconPackManager, IconPackStorage, IconPack, IconPacksActivity, InstallIconPackBottomSheet, BaseIconPacks; Nagram X IconsResources (GPL-3.0); Solar Icon Set (480 Design, CC BY 4.0), Remix Icon (Remix Design, Apache-2.0); Nagram X MonetHelper і теми monet_*.attheme (GPL-3.0, також exteraless); раніше: Nagram, Nullgram, exteraless drawer/menu/plugins, AyuFilter, Regex*, Nekogram, Nagram X, Chaquopy MIT.
- Коміти: `git -c user.name=dmytrokurochkin -c user.email=dmytro.kurochkin@tm1.edu.pl commit`, англійською, префікс `[mzgram] KB <domain>: …`; без Co-authored-by і згадок Claude, AI чи бота.
- CI: максимум 2 запуски на репо, лише у фазі тестів (після всіх функцій: короткий звіт, компіляція, тести). Ручний запуск `gh workflow run mzgram-android.yml --ref w-kb-all`.
- Мережа: правила MZGram без перекладача; без мережевих викликів до третіх сторін, крім шлюзу UnifiedPush. Мережеві функції лише офлайн-частина, решта в «Для власника».
- Chaquopy лише за `-PmzgramPlugins=true` (mzgramPlugins). minSdk 23: без незахищених викликів API 24+ в основному коді (лише з перевіркою SDK_INT).
- Безпека: «Не копіювати google-services.json, ключі й секрети з _source.» «Ключ підпису Android не чіпати.» Desktop і Dependabot PR не чіпати. Без платних рішень. Без чужих брендів в іконках.
- Хуки в класах Telegram мінімальні: коментар `// MZGram: …` і виклик з повним іменем класу.
- Правки: без cp; Edit або Python-скрипти з assert `count==1`, `python -X utf8 -I`, з урахуванням CRLF; скрипти писати Write. Великі файли читати діапазонами рядків. progress-kb-all.md читати першим.
- Плагіни: лише платформа, без вбудованого каталогу 471 плагіна.
- Наприкінці всієї роботи викликати project-log.

## 3) Known Issues / Tech Debt
- Збірка не запускалась з початку kb-all: ризик компіляції в №26, №33, №35-58, №29, №30, №4, №34; середній у №57 (нові підкласи Resources і Drawable) і №58 (хуки Theme, константи android.R.color.system_* API 31 під перевіркою SDK).
- `jni/audio.c` перевірити в CI. test_levels_reliable (DB_VERSION 5 проти 7) оновити у фазі тестів разом із MZGramHistoryDatabaseTest.
- Плагіни не запускаються до №63/№64; сканер коду №67; плагіни exteraless з об'єктом повідомлення (context["message"]) №71. №16 plugins-backup відкладено до платформи. №40 після №60.
- Для власника (повний текст: progress-kb-all.md, розділ «Для власника»):
  - owner з карти: map-preview-provider (тайли третьої сторони), tor-idle-plugin, телефон у бічному меню, bookmarks-manager (перетин з 1.0.12), курси в калькуляторі, пілюлі фінансів і погоди (сторонні API), icon-packs-catalog (18 наборів, ліцензії невідомі), synced-lyrics онлайн, whisper (моделі HuggingFace), музична картка профілю (1.0.17), перекладач і AI-перекладачі (правило), зовнішні LLM, Vosk, стрічка каналів (1.0.15), хмарний бекап (1.0.16), бейджі, пропрієтарні emoji-шрифти, Nothing Glyph, DoH, локальний преміум і підміна геопозиції (правила Telegram), plugin-ai-review, pip з PyPI; ризики Chaquopy (64-bit, +десятки МБ, R8 keep).
  - network-tracking: ротація temp key не зроблена (tgnet, ризик виходу з акаунта), CDN-відмова зроблена; passcode-accounts: panic-код і межі прихованості на рішення; deleted-dialogs не вставляються в головний список; імпорт сторонньої бази (медіа лише за шляхом); deleted-reply-quote/Forward Loader без «Переслано від»; секретні чати й TTL-медіа зберігаються за рішенням власника.
  - chat-headers: компактна шапка не зроблена; виключення діалогів зроблено в №4; №23 зроблено окремим перемикачем; №24 LocalPeerColorHelper не робити (рішення); №31 ярлики лаунчера не зроблені (варіанти а/б/в); appearance.minor M3-перемикачі й картки не зроблені; №36 emoticon папки стирається при збереженні на Android (upstream); №37 вбудовані OFL-шрифти не додані, рефлексія Typeface; №41 «Поділитися» лишити (рішення), wake lock не відстежується.
  - Плагіни №42 (розмір +50-70 МБ, ліцензії, F-Droid, minSdk 24, Python 3.11 у CI, без pip, окремий CI-запуск), №45-52 (Java-моделі, view в on_click, умови MVEL, підтвердження enable через dev-сервер, порт 42690, Keystore після бекапу, список заборонених хуків, «Не завантажувати приховані історії» окремим перемикачем, шторка дозволів, журнал лише локально).
  - №53 regex-фільтри: «Не завантажувати медіа прихованих» вимкнене; без «згорнути під спойлер» і без приховування в списку чатів; №4 таємні чати окремо.
  - №54 бічне меню лише трьома крапками, без свайпу; №34 немає фону шапки з галереї, скляна панель знімок, а не живе розмиття.
  - №55 локальне закріплення не переходить на сервер після зняття серверного.
  - №56 немає пілюль погоди, курсів, крипти, золота; пілюля кешу без швидкого очищення.
  - №57 немає редактора наборів; 18 наборів каталогу не вбудовано; +2,8 МБ XML; частина іконок чату після перезапуску; складні SVG неповно.
  - №58 лише Android 12+; CIELAB замість HCT (відтінки можуть трохи відрізнятися від Nagram X); без варіантів акценту, фон чату суцільний; оновлення після повернення в застосунок, без приймача OVERLAY_CHANGED; експорт зберігає знімок кольорів; M3-перемикачі в кольорах Monet не зроблено.
  - Кольори текстів: борг test_texts виправлено (9526e3088).

## 4) Next Immediate Task
1. **№59 media.camerax-integration** (L, план implement, примітка «Фікс 2.1»): прочитати рядок у progress-kb-all.md, `features/media/camerax-integration.md` (CameraXSession, CameraZoomSliderView, InstantCameraZoomSlider, CameraLensStops; ключі use_camerax, camera_default_lens), фікс 2.1 у `features/_conflicts/*` (знайти grep «2.1»), референс у _source; перевірити, чи MZGram уже має CameraX (Telegram має `org.telegram.messenger.camera`, перевірити залежності build.gradle: нова бібліотека camerax потребує рішення щодо розміру/правил, інакше «Для власника»). OFF за замовчуванням, наявний запис кругляшків не міняти.
2. **№60 media.m3-music-player** (L, «Плеєр Telegram, не плитка чужих застосунків»): `features/media/m3-music-player.md`, референс app/exteraless/player; окремий перемикач поряд із плеєром Telegram.
3. **№40 media.player-styling-queue** (M, після №60): PlayerColors, PlayerStyleEditor, PlayerQueueSheet з app/exteraless/player поверх №60.
Далі №61 system.backup-cloud-sync (лише локальний бекап mzgram_*), №62 proxy-management (автовимкнення проксі локально), №63-68, №69, №70, №71.
