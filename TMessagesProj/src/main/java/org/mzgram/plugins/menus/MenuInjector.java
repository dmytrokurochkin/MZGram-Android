package org.mzgram.plugins.menus;

import android.content.Context;
import android.widget.LinearLayout;

import android.text.TextUtils;
import android.view.View;

import org.mvel2.MVEL;
import org.mvel2.optimizers.OptimizerFactory;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.ItemOptions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mzgram.drawer.DrawerMenuItemView;
import org.mzgram.drawer.MainMenuHelper;
import org.mzgram.drawer.MainMenuItem;
import org.mzgram.drawer.MainMenuLayout;
import org.mzgram.plugins.MenuItemRecord;
import org.mzgram.plugins.PluginsController;

public final class MenuInjector {

    static {
        OptimizerFactory.setDefaultOptimizer(OptimizerFactory.SAFE_REFLECTIVE);
    }

    private static final int MESSAGE_OPTION_BASE = 9_000_000;
    public static final int MESSAGE_PLUGINS_OPTION = MESSAGE_OPTION_BASE - 1;
    private static Map<String, Object> messageMenuContext;

    private static final class MessageMenuEntry {
        final MenuItemRecord record;
        final Map<String, Object> context;

        MessageMenuEntry(MenuItemRecord record, Map<String, Object> context) {
            this.record = record;
            this.context = context;
        }
    }

    private static final ArrayList<MessageMenuEntry> messageMenuEntries = new ArrayList<>();

    private static final ConcurrentHashMap<String, Serializable> COMPILED_CONDITIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> ICON_IDS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> FAILED_CONDITIONS = ConcurrentHashMap.newKeySet();

    private MenuInjector() {
    }

    public static void releaseMessageMenu(BaseFragment fragment) {
        if (messageMenuContext != null) {
            Object owner = messageMenuContext.get("fragment");
            if (owner == null || owner == fragment) {
                messageMenuContext = null;
            }
        }
        if (messageMenuEntries.isEmpty()) {
            return;
        }
        Object owner = messageMenuEntries.get(0).context.get("fragment");
        if (owner == null || owner == fragment) {
            messageMenuEntries.clear();
        }
    }


    public static boolean isVisible(MenuItemRecord item, Map<String, Object> context) {
        if (item.condition == null || item.condition.isEmpty()) {
            return true;
        }
        String previousRuntime = org.mzgram.plugins.PluginRuntime.enter(item.pluginId);
        try {
            Serializable compiled = COMPILED_CONDITIONS.get(item.condition);
            if (compiled == null) {
                if (!isSafeCondition(item.condition)) {
                    throw new IllegalArgumentException("condition may only read the menu context");
                }
                compiled = MVEL.compileExpression(item.condition);
                if (COMPILED_CONDITIONS.size() < 512) {
                    COMPILED_CONDITIONS.put(item.condition, compiled);
                }
            }
            Boolean result = MVEL.executeExpression(compiled, context, Boolean.class);
            return result != null && result;
        } catch (Throwable t) {
            if (FAILED_CONDITIONS.size() < 512 && FAILED_CONDITIONS.add(item.pluginId + "/" + item.itemId + "/" + item.condition)) {
                FileLog.e("MenuInjector: condition failed for " + item.pluginId + "/" + item.itemId, t);
            }
            return false;
        } finally {
            org.mzgram.plugins.PluginRuntime.exit(previousRuntime);
        }
    }

    private static final java.util.regex.Pattern UNSAFE_CONDITION = java.util.regex.Pattern.compile(
            "\\b(?:new|import|def|function|foreach|for|while|do|with|until|class|getClass|forName|"
                    + "getClassLoader|loadClass|invoke|getMethod|getMethods|getDeclared\\w*|getField|getFields|"
                    + "newInstance|getRuntime|exec|setAccessible|getConstructor\\w*)\\b"
                    + "|[;@$#`{}]"
                    + "|(?<![=!<>])=(?!=)"
                    + "|\\+\\+|--|[+\\-*/%]=");

    private static final java.util.regex.Pattern ROOT_NAME =
            java.util.regex.Pattern.compile("(?<![.\\w])([A-Za-z_]\\w*)");

    private static final java.util.Set<String> CONDITION_ROOTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "account", "chat", "chatId", "context", "dialog_id", "encryptedChat", "fragment", "message",
            "user", "userId", "true", "false", "null", "nil", "empty", "is", "isnot", "contains",
            "instanceof", "soundslike", "strsim"));

    static boolean isSafeCondition(String condition) {
        String code = condition.replaceAll("'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"", "''");
        if (UNSAFE_CONDITION.matcher(code).find()) {
            return false;
        }
        java.util.regex.Matcher roots = ROOT_NAME.matcher(code);
        while (roots.find()) {
            if (!CONDITION_ROOTS.contains(roots.group(1))) {
                return false;
            }
        }
        return true;
    }

    public static int resolveIcon(Context context, String icon) {
        if (icon == null || icon.isEmpty()) {
            return 0;
        }
        Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        if (ctx == null) {
            return 0;
        }
        Integer cached = ICON_IDS.get(icon);
        if (cached != null) {
            return cached;
        }
        try {
            int id = ctx.getResources().getIdentifier(icon, "drawable", ctx.getPackageName());
            ICON_IDS.put(icon, id);
            return id;
        } catch (Throwable t) {
            FileLog.e("MenuInjector: bad icon " + icon, t);
            return 0;
        }
    }

    // ---------- MESSAGE_CONTEXT_MENU (ChatActivity) ----------

    public static void fillMessageMenu(BaseFragment fragment, MessageObject message, TLRPC.Chat chat,
                                       TLRPC.User user, TLRPC.EncryptedChat encryptedChat,
                                       long dialogId, int account,
                                       ArrayList<Integer> icons, ArrayList<CharSequence> items,
                                       ArrayList<Integer> options) {
        messageMenuEntries.clear();
        messageMenuContext = null;
        if (!PluginsController.getInstance().isEngineEnabled()) {
            return;
        }
        List<MenuItemRecord> records =
                PluginsController.getInstance().getMenuItemsFor(MenuItemRecord.MenuType.MESSAGE_CONTEXT_MENU);
        if (records.isEmpty()) {
            return;
        }
        Context context = fragment != null ? fragment.getParentActivity() : null;
        Map<String, Object> menuContext = new HashMap<>();
        menuContext.put("message", message);
        menuContext.put("chat", null);
        menuContext.put("user", null);
        menuContext.put("encryptedChat", null);
        if (chat != null) {
            menuContext.put("chat", chat);
            menuContext.put("chatId", chat.id);
        }
        if (user != null) {
            menuContext.put("user", user);
            menuContext.put("userId", user.id);
        }
        if (encryptedChat != null) {
            menuContext.put("encryptedChat", encryptedChat);
        }
        if (fragment != null) {
            menuContext.put("fragment", fragment);
        }
        if (context != null) {
            menuContext.put("context", context);
        }
        menuContext.put("dialog_id", dialogId);
        menuContext.put("account", account);
        for (MenuItemRecord record : records) {
            if (!TextUtils.isEmpty(record.text) && isVisible(record, menuContext)) {
                messageMenuContext = menuContext;
                items.add(LocaleController.getString(R.string.MZGramPlugins));
                icons.add(R.drawable.msg_plugins);
                options.add(MESSAGE_PLUGINS_OPTION);
                return;
            }
        }
    }

    public static void expandMessagePluginsOption(ArrayList<Integer> icons, ArrayList<CharSequence> items,
                                                  ArrayList<Integer> options) {
        int index = options.indexOf(MESSAGE_PLUGINS_OPTION);
        if (index < 0) {
            return;
        }
        icons.remove(index);
        items.remove(index);
        options.remove(index);
        Map<String, Object> menuContext = messageMenuContext;
        if (menuContext == null) {
            return;
        }
        Context context = (Context) menuContext.get("context");
        List<MenuItemRecord> records =
                PluginsController.getInstance().getMenuItemsFor(MenuItemRecord.MenuType.MESSAGE_CONTEXT_MENU);
        for (MenuItemRecord record : records) {
            if (TextUtils.isEmpty(record.text) || !isVisible(record, menuContext)) {
                continue;
            }
            icons.add(index, resolveIcon(context, record.icon));
            items.add(index, record.text);
            options.add(index, MESSAGE_OPTION_BASE + messageMenuEntries.size());
            messageMenuEntries.add(new MessageMenuEntry(record, menuContext));
            index++;
        }
    }

    public static View createMessagePluginsMenu(PopupSwipeBackLayout swipeBackLayout, Runnable closeMenu,
                                                Theme.ResourcesProvider resourcesProvider) {
        Map<String, Object> contextData = messageMenuContext != null ? messageMenuContext : new HashMap<>();
        return new PluginsMenuWrapper(swipeBackLayout, MenuItemRecord.MenuType.MESSAGE_CONTEXT_MENU,
                contextData, resourcesProvider) {
            @Override
            public void closeMenu() {
                closeMenu.run();
            }
        }.getSwipeBack();
    }

    public static boolean handleMessageMenuOption(int option) {
        int index = option - MESSAGE_OPTION_BASE;
        if (index < 0 || index >= messageMenuEntries.size()) {
            return false;
        }
        MessageMenuEntry entry = messageMenuEntries.get(index);
        PluginsController.getInstance()
                .dispatchMenuClick(entry.record.pluginId, entry.record.itemId, entry.context);
        return true;
    }

    // ---------- DRAWER_MENU (DrawerMenuView) ----------

    public static void appendDrawerItems(LinearLayout container, int currentAccount, Runnable onItemClick) {
        if (container == null || !PluginsController.getInstance().isEngineEnabled()
                || MainMenuLayout.getLayout().contains(MainMenuItem.PLUGINS.getId())) {
            return;
        }
        List<MenuItemRecord> records =
                PluginsController.getInstance().getMenuItemsFor(MenuItemRecord.MenuType.DRAWER_MENU);
        if (records.isEmpty()) {
            return;
        }
        Map<String, Object> menuContext = new HashMap<>();
        menuContext.put("account", currentAccount);
        Context context = container.getContext();
        for (MenuItemRecord record : records) {
            if (!isVisible(record, menuContext)) {
                continue;
            }
            DrawerMenuItemView itemView = new DrawerMenuItemView(context);
            itemView.setMenuItem(0, currentAccount, resolveIcon(context, record.icon),
                    record.text != null ? record.text : record.itemId);
            itemView.setOnClickListener(v -> {
                if (onItemClick != null) {
                    onItemClick.run();
                }
                Map<String, Object> clickContext = new HashMap<>();
                clickContext.put("account", currentAccount);
                PluginsController.getInstance()
                        .dispatchMenuClick(record.pluginId, record.itemId, clickContext);
            });
            container.addView(itemView);
        }
    }


    public static final class SwipeBackMenu {
        public final PluginsMenuWrapper wrapper;
        public final ActionBarMenuItem.Item item;
        final ActionBarMenuItem headerItem;

        SwipeBackMenu(PluginsMenuWrapper wrapper, ActionBarMenuItem.Item item, ActionBarMenuItem headerItem) {
            this.wrapper = wrapper;
            this.item = item;
            this.headerItem = headerItem;
        }

        public void refresh() {
            boolean hasItems = wrapper.hasItems();
            if (hasItems) {
                wrapper.rebuildMenu(null);
            }
            item.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        }
    }

    public static SwipeBackMenu attachSwipeBackMenu(ActionBarMenuItem headerItem,
                                                    MenuItemRecord.MenuType menuType,
                                                    Map<String, Object> contextData,
                                                    Theme.ResourcesProvider resourcesProvider,
                                                    SwipeBackMenu previous) {
        if (headerItem == null || !PluginsController.getInstance().isEngineEnabled()) {
            return previous;
        }
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = headerItem.getPopupLayout();
        if (popupLayout == null || popupLayout.getSwipeBack() == null) {
            return previous;
        }
        try {
            final boolean sameHost = previous != null && previous.headerItem == headerItem;
            PluginsMenuWrapper wrapper = sameHost ? previous.wrapper : new PluginsMenuWrapper(
                    popupLayout.getSwipeBack(), menuType, contextData, resourcesProvider) {
                @Override
                public void closeMenu() {
                    headerItem.toggleSubMenu();
                }
            };
            ActionBarMenuItem.Item item;
            if (sameHost) {
                wrapper.rebuildMenu(null);
                item = headerItem.lazilyReuseItem(previous.item);
            } else {
                item = headerItem.lazilyAddSwipeBackItem(
                        R.drawable.msg_settings_old, null,
                        LocaleController.getString(R.string.MZGramPlugins), wrapper.getSwipeBack());
                item.setOnClickListener(v -> item.openSwipeBack());
            }
            item.setVisibility(wrapper.hasItems() ? View.VISIBLE : View.GONE);
            return new SwipeBackMenu(wrapper, item, headerItem);
        } catch (Throwable t) {
            FileLog.e("MenuInjector: cannot attach " + menuType + " submenu", t);
            return previous;
        }
    }

    // ---------- MAIN_MENU (DialogsActivity «⋮») ----------

    public static List<MainMenuHelper.MenuItemInfo> mainMenuItems(int currentAccount, BaseFragment fragment) {
        if (!PluginsController.getInstance().isEngineEnabled()) {
            return Collections.emptyList();
        }
        LinkedHashSet<MenuItemRecord> records = new LinkedHashSet<>(
                PluginsController.getInstance().getMenuItemsFor(MenuItemRecord.MenuType.MAIN_MENU));
        records.addAll(PluginsController.getInstance().getMenuItemsFor(MenuItemRecord.MenuType.DRAWER_MENU));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> menuContext = new HashMap<>();
        menuContext.put("account", currentAccount);
        if (fragment != null) {
            menuContext.put("fragment", fragment);
            if (fragment.getParentActivity() != null) {
                menuContext.put("context", fragment.getParentActivity());
            }
        }
        ArrayList<MainMenuHelper.MenuItemInfo> result = new ArrayList<>(records.size());
        for (MenuItemRecord record : records) {
            if (!isVisible(record, menuContext)) {
                continue;
            }
            int icon = resolveIcon(null, record.icon);
            result.add(new MainMenuHelper.MenuItemInfo(icon, record.text != null ? record.text : record.itemId, () ->
                    PluginsController.getInstance().dispatchMenuClick(record.pluginId, record.itemId, new HashMap<>(menuContext)), null));
        }
        return result;
    }

    public static void appendMainMenuItems(ItemOptions io, int currentAccount, BaseFragment fragment) {
        if (io == null || !PluginsController.getInstance().isEngineEnabled()) {
            return;
        }
        if (MainMenuLayout.isCustomized() && MainMenuLayout.getLayout().contains(MainMenuItem.PLUGINS.getId())) {
            return;
        }
        MainMenuHelper.MenuContext ctx = MainMenuHelper.createMenuContext(currentAccount, fragment);
        if (mainMenuItems(currentAccount, fragment).isEmpty()) {
            return;
        }
        io.addGap();
        MainMenuHelper.addPluginsMenuItem(io, ctx);
    }
}
