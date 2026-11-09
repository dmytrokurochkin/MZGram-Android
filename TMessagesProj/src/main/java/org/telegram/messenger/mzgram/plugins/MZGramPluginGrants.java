/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * The permissions the user granted to each plugin, signed so that an edited
 * record is not believed. A plugin declares permission keys in its metadata
 * (__permissions__); what it gets is only what is recorded here, plus "ui",
 * which every plugin has. A plugin without a record gets only "ui".
 *
 * Each record is signed with HMAC-SHA256 under a key that lives in the
 * Android Keystore of this app, so the key can't be read from the app's
 * files: a record changed in the preferences file (by a plugin with file
 * access, a rooted app or a restored backup, whose key is not restored) no
 * longer matches its signature. Such a record is dropped and the plugin is
 * turned off, so it has to be granted again. If the signing key itself is
 * gone, every record is dropped. On a device whose Keystore can't make the
 * key, records are kept unsigned (as in exteraless).
 *
 * Gates that check permissions (the permission model, hooks, network) and
 * the prompt before installing read and write through this class; it has no
 * screen of its own.
 *
 * Based on exteraless plugins/PluginGrantStore.java,
 * SignedGrantPreferences.java and PluginPermissions.java (GPL-3.0): the
 * Keystore key, the signed payload and the record of granted keys; written
 * anew for MZGram.
 */

package org.telegram.messenger.mzgram.plugins;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public final class MZGramPluginGrants {

    public static final String UI = "ui";
    public static final String MESSAGES_READ = "messages.read";
    public static final String MESSAGES_SEND = "messages.send";
    public static final String NETWORK = "network";
    public static final String FILES = "files";
    public static final String INTENTS = "intents";
    public static final String SETTINGS = "settings";
    public static final String HOOKS = "hooks";
    public static final String NATIVE = "native";

    /** Every permission key, in the order they are shown and stored; the same as in mzgram_plugins/metadata.py. */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            UI, MESSAGES_READ, MESSAGES_SEND, NETWORK, FILES, INTENTS, SETTINGS, HOOKS, NATIVE));

    private static final String PREFS = "mzgram_plugin_grants";
    private static final String KEY_ALIAS = "mzgram_plugin_grants_v1";
    private static final String KEY_PERMS = "perms_";
    private static final String KEY_SIGNATURE = "sig:";
    // Set once the first key was made; a missing key after that means it was lost
    private static final String KEY_SEALED = "sealed_v1";
    private static final String HMAC = "HmacSHA256";

    private static final Object lock = new Object();
    private static SecretKey key;
    private static boolean keyUnavailable;
    // Signed payloads already checked: payload + signature -> valid
    private static final Map<String, Boolean> verified = new HashMap<>();

    private MZGramPluginGrants() {
    }

    public static boolean isKnown(String permission) {
        return permission != null && ALL.contains(permission);
    }

    /** Known keys only, without repeats, in the order of ALL. */
    public static List<String> sanitize(Collection<String> permissions) {
        final ArrayList<String> out = new ArrayList<>();
        if (permissions == null) {
            return out;
        }
        for (String permission : ALL) {
            if (permissions.contains(permission)) {
                out.add(permission);
            }
        }
        return out;
    }

    private static SharedPreferences prefs() {
        final Context context = ApplicationLoader.applicationContext;
        return context != null ? context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) : null;
    }

    // ---------- the signing key ----------

    // The key, made on first use; null when the Keystore can't make it
    private static SecretKey key(SharedPreferences prefs) {
        if (key != null || keyUnavailable) {
            return key;
        }
        try {
            final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
            if (key == null) {
                final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build());
                key = generator.generateKey();
                // Records signed with a key that is gone can't be checked any more
                dropAll(prefs, prefs.getBoolean(KEY_SEALED, false));
            }
        } catch (Throwable e) {
            key = null;
            keyUnavailable = true;
            FileLog.e("MZGramPluginGrants: the Keystore can't make the signing key, records stay unsigned", e);
        }
        return key;
    }

    private static void dropAll(SharedPreferences prefs, boolean wasSealed) {
        final SharedPreferences.Editor editor = prefs.edit();
        int count = 0;
        for (String name : prefs.getAll().keySet()) {
            if (name.startsWith(KEY_PERMS) || name.startsWith(KEY_SIGNATURE)) {
                editor.remove(name);
                if (name.startsWith(KEY_PERMS)) {
                    count++;
                    disable(name.substring(KEY_PERMS.length()));
                }
            }
        }
        editor.putBoolean(KEY_SEALED, true).commit();
        if (wasSealed || count > 0) {
            FileLog.e("MZGramPluginGrants: the signing key is new, dropped " + count + " records");
        }
    }

    private static String payload(String name, String value) {
        return name + '\u0000' + value;
    }

    private static String sign(SecretKey secret, String name, String value) {
        try {
            final Mac mac = Mac.getInstance(HMAC);
            mac.init(secret);
            return Base64.encodeToString(mac.doFinal(payload(name, value).getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (Throwable e) {
            FileLog.e("MZGramPluginGrants: cannot sign " + name, e);
            return null;
        }
    }

    // ---------- signed values ----------

    /**
     * The value of a signed record, or null when there is none or it was
     * changed (then it is dropped and the plugin is turned off).
     */
    static String getSigned(String name, String pluginId) {
        synchronized (lock) {
            final SharedPreferences prefs = prefs();
            if (prefs == null) {
                return null;
            }
            final String value = prefs.getString(name, null);
            final SecretKey secret = key(prefs);
            if (value == null || secret == null) {
                return value;
            }
            final String signature = prefs.getString(KEY_SIGNATURE + name, null);
            boolean ok = false;
            if (signature != null) {
                final String cacheKey = payload(name, value) + '\u0000' + signature;
                final Boolean cached = verified.get(cacheKey);
                if (cached != null) {
                    ok = cached;
                } else {
                    final String expected = sign(secret, name, value);
                    if (expected == null) {
                        // Signing failed this time, which proves nothing: grant nothing now, drop nothing
                        return null;
                    }
                    ok = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
                    verified.put(cacheKey, ok);
                }
            }
            if (!ok) {
                FileLog.e("MZGramPluginGrants: the record " + name + " is unsigned or was changed; dropping it");
                prefs.edit().remove(name).remove(KEY_SIGNATURE + name).commit();
                disable(pluginId);
                return null;
            }
            return value;
        }
    }

    /** Keeps a signed record; value null removes it. False when it couldn't be kept. */
    static boolean putSigned(String name, String value) {
        synchronized (lock) {
            final SharedPreferences prefs = prefs();
            if (prefs == null) {
                return false;
            }
            if (value == null) {
                return prefs.edit().remove(name).remove(KEY_SIGNATURE + name).commit();
            }
            final SecretKey secret = key(prefs);
            if (secret == null) {
                return prefs.edit().putString(name, value).remove(KEY_SIGNATURE + name).commit();
            }
            final String signature = sign(secret, name, value);
            if (signature == null) {
                // Never keep a value without its signature: it would be dropped as changed
                return false;
            }
            verified.put(payload(name, value) + '\u0000' + signature, true);
            return prefs.edit().putString(name, value).putString(KEY_SIGNATURE + name, signature).commit();
        }
    }

    private static void disable(String pluginId) {
        if (pluginId != null) {
            MZGramPlugins.setEnabled(pluginId, false);
        }
    }

    // ---------- permissions of plugins ----------

    /** Whether the user has decided on this plugin's permissions (even granting none). */
    public static boolean hasRecord(String pluginId) {
        return getStored(pluginId) != null;
    }

    /** The granted keys without "ui", or null when there is no valid record. */
    public static List<String> getStored(String pluginId) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId)) {
            return null;
        }
        final String raw = getSigned(KEY_PERMS + pluginId, pluginId);
        if (raw == null) {
            return null;
        }
        final ArrayList<String> parts = new ArrayList<>();
        for (String part : raw.split(",")) {
            parts.add(part.trim());
        }
        final List<String> out = sanitize(parts);
        out.remove(UI);
        return out;
    }

    /** What the plugin has: "ui" first, then the granted keys. Without a record only "ui". */
    public static List<String> getEffective(String pluginId) {
        final List<String> stored = getStored(pluginId);
        final ArrayList<String> out = new ArrayList<>();
        out.add(UI);
        if (stored != null) {
            out.addAll(stored);
        }
        return out;
    }

    public static boolean isGranted(String pluginId, String permission) {
        return UI.equals(permission) ? MZGramPluginSettingsStore.isValidId(pluginId) : getEffective(pluginId).contains(permission);
    }

    /**
     * Records the user's decision; the record is kept even when empty, which
     * tells "granted nothing" from "not asked yet". "ui" is implied and not
     * stored. False when it couldn't be kept.
     */
    public static boolean setGranted(String pluginId, Collection<String> permissions) {
        if (!MZGramPluginSettingsStore.isValidId(pluginId)) {
            return false;
        }
        final List<String> clean = sanitize(permissions);
        clean.remove(UI);
        return putSigned(KEY_PERMS + pluginId, TextUtils.join(",", clean));
    }

    public static boolean grant(String pluginId, String permission) {
        if (!isKnown(permission)) {
            return false;
        }
        final List<String> current = getEffective(pluginId);
        if (!current.contains(permission)) {
            current.add(permission);
        }
        return setGranted(pluginId, current);
    }

    /** "ui" can't be taken back. */
    public static boolean revoke(String pluginId, String permission) {
        if (UI.equals(permission)) {
            return false;
        }
        final List<String> current = getEffective(pluginId);
        current.remove(permission);
        return setGranted(pluginId, current);
    }

    /**
     * After an update: keeps only the granted keys the new version still
     * declares, so a permission dropped and declared again is asked again.
     * declared null (a file without __permissions__) keeps the record as it is.
     */
    public static void retainDeclared(String pluginId, Collection<String> declared) {
        final List<String> stored = declared != null ? getStored(pluginId) : null;
        if (stored == null) {
            return;
        }
        final ArrayList<String> kept = new ArrayList<>(stored);
        kept.retainAll(declared);
        if (kept.size() != stored.size()) {
            setGranted(pluginId, kept);
        }
    }

    /** Removes the record (when the plugin is deleted). */
    public static void clear(String pluginId) {
        if (MZGramPluginSettingsStore.isValidId(pluginId)) {
            putSigned(KEY_PERMS + pluginId, null);
        }
    }
}
