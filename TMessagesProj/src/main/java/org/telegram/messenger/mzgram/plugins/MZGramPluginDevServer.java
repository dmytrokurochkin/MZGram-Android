/*
 * This is the source code of MZGram for Android,
 * a fork of Telegram for Android.
 *
 * Developer server for plugins: lets a computer add and update plugins
 * while they are written, through adb (adb forward tcp:42690 tcp:42690),
 * without picking the file on the phone each time.
 *
 * It listens on 127.0.0.1 only, so nothing outside the phone reaches it,
 * and only while the user keeps it turned on in MZGram → Other → Plugins:
 * it is off by default and is not started again when the app starts. Apps
 * on the same phone can reach 127.0.0.1, so every command but "ping" needs
 * the token MZGram shows on the Plugins screen, a new one each time the
 * server starts. After MAX_BAD_TOKENS wrong tokens the server stops.
 *
 * Protocol: one UTF-8 JSON object per line. "@" is the command, "#" an id
 * of the client given back in the reply, "token" the token. Each reply is
 * one line {"#": id, "ok": true|false, ...} with "error" when ok is false.
 * Commands: ping, get_plugins, enable_plugin, disable_plugin, write_plugin
 * (plugin_id, content: the .plugin file in base64), remove_plugin,
 * reload_plugin (refused while "Run plugins" or the plugin is off). A file sent
 * with write_plugin goes through the same metadata check as a picked file
 * and is kept the same way (MZGramPlugins.install): a new plugin is off.
 *
 * Based on exteraless python/dev_server.py (GPL-3.0): the port, the line
 * protocol, the command names and the token; written anew for MZGram in
 * Java.
 */

package org.telegram.messenger.mzgram.plugins;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class MZGramPluginDevServer {

    public static final String HOST = "127.0.0.1";
    /** The same port as the exteraless developer server, so its tools fit. */
    public static final int PORT = 42690;

    // Wrong tokens after which the server stops
    private static final int MAX_BAD_TOKENS = 20;
    private static final int MAX_CLIENTS = 4;
    // A line holds at most a 2 MB file in base64 and the rest of the command
    private static final int MAX_LINE = 3 * 1024 * 1024;
    // A client that sends nothing for this long is dropped
    private static final int IDLE_MS = 5 * 60 * 1000;
    // Reading the metadata starts Python, which can take a few seconds
    private static final long WAIT_SECONDS = 120;

    private static final String KEY_COMMAND = "@";
    private static final String KEY_REQUEST_ID = "#";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_OK = "ok";
    private static final String KEY_ERROR = "error";
    private static final String KEY_PLUGIN_ID = "plugin_id";
    private static final String KEY_CONTENT = "content";

    private static final String COMMAND_PING = "ping";
    private static final String COMMAND_GET_PLUGINS = "get_plugins";
    private static final String COMMAND_ENABLE = "enable_plugin";
    private static final String COMMAND_DISABLE = "disable_plugin";
    private static final String COMMAND_WRITE = "write_plugin";
    private static final String COMMAND_REMOVE = "remove_plugin";
    private static final String COMMAND_RELOAD = "reload_plugin";
    private static final String COMMAND_START_DEBUGGER = "start_debugger";
    private static final String COMMAND_STOP_DEBUGGER = "stop_debugger";

    private static final Object lock = new Object();
    // Commands that change plugins run one at a time
    private static final Object commandLock = new Object();
    private static ServerSocket server;
    private static String token;
    private static int clients;
    private static int badTokens;
    private static final ArrayList<Socket> sockets = new ArrayList<>();
    // UI thread only
    private static final ArrayList<Runnable> listeners = new ArrayList<>();

    private MZGramPluginDevServer() {
    }

    public static boolean isRunning() {
        synchronized (lock) {
            return server != null;
        }
    }

    /** The token of the running server, or null when it is stopped. */
    public static String getToken() {
        synchronized (lock) {
            return server != null ? token : null;
        }
    }

    /**
     * Listeners run on the UI thread when the server starts or stops and
     * when a command changed the added plugins. UI thread only.
     */
    public static void addListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        AndroidUtilities.runOnUIThread(() -> {
            for (Runnable listener : new ArrayList<>(listeners)) {
                listener.run();
            }
        });
    }

    /**
     * Starts listening on 127.0.0.1:PORT with a new token. onDone gets, on
     * the UI thread, true when the server runs, false when it can't (no
     * Python in this build, or the port is taken).
     */
    public static void start(Utilities.Callback<Boolean> onDone) {
        if (!MZGramPythonRuntime.isAvailable()) {
            finish(onDone, false);
            return;
        }
        final Thread thread = new Thread(() -> {
            final ServerSocket socket;
            synchronized (lock) {
                if (server != null) {
                    finish(onDone, true);
                    return;
                }
                try {
                    socket = new ServerSocket();
                    socket.setReuseAddress(true);
                    // The address as bytes: no lookup, and never another interface
                    socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), PORT), MAX_CLIENTS);
                } catch (Exception e) {
                    FileLog.e("MZGramPluginDevServer: cannot listen on " + HOST + ":" + PORT, e);
                    finish(onDone, false);
                    return;
                }
                server = socket;
                token = newToken();
                badTokens = 0;
            }
            FileLog.d("MZGramPluginDevServer: listening on " + HOST + ":" + PORT);
            finish(onDone, true);
            notifyListeners();
            accept(socket);
        }, "mzgram-plugin-dev-server");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the server and drops its clients. */
    public static void stop() {
        final ServerSocket socket;
        final ArrayList<Socket> open;
        synchronized (lock) {
            socket = server;
            server = null;
            token = null;
            open = new ArrayList<>(sockets);
            sockets.clear();
        }
        if (socket == null) {
            return;
        }
        closeQuietly(socket);
        for (Socket client : open) {
            closeQuietly(client);
        }
        FileLog.d("MZGramPluginDevServer: stopped");
        notifyListeners();
    }

    private static void finish(Utilities.Callback<Boolean> onDone, boolean ok) {
        if (onDone != null) {
            AndroidUtilities.runOnUIThread(() -> onDone.run(ok));
        }
    }

    private static String newToken() {
        final byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        final StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return hex.toString();
    }

    // ---------- connections ----------

    private static void accept(ServerSocket socket) {
        while (true) {
            final Socket client;
            try {
                client = socket.accept();
            } catch (IOException e) {
                // Closed by stop()
                break;
            }
            synchronized (lock) {
                if (server != socket || clients >= MAX_CLIENTS) {
                    closeQuietly(client);
                    continue;
                }
                clients++;
                sockets.add(client);
            }
            final Thread thread = new Thread(() -> {
                try {
                    serve(client);
                } finally {
                    synchronized (lock) {
                        clients--;
                        sockets.remove(client);
                    }
                    closeQuietly(client);
                }
            }, "mzgram-plugin-dev-client");
            thread.setDaemon(true);
            thread.start();
        }
        synchronized (lock) {
            if (server == socket) {
                server = null;
                token = null;
            }
        }
    }

    private static void serve(Socket client) {
        try {
            client.setSoTimeout(IDLE_MS);
            final InputStream in = new BufferedInputStream(client.getInputStream());
            final OutputStream out = client.getOutputStream();
            while (true) {
                final byte[] line;
                try {
                    line = readLine(in);
                } catch (LineTooLongException e) {
                    write(out, reply(null, false, "the line is longer than " + MAX_LINE + " bytes"));
                    return;
                }
                if (line == null) {
                    return;
                }
                final String text = new String(line, StandardCharsets.UTF_8).trim();
                if (text.isEmpty()) {
                    continue;
                }
                final JSONObject request;
                try {
                    request = new JSONObject(text);
                } catch (Exception e) {
                    write(out, reply(null, false, "malformed request: " + e.getMessage()));
                    continue;
                }
                final Object requestId = request.opt(KEY_REQUEST_ID);
                final String command = request.optString(KEY_COMMAND, "");
                if (!command.isEmpty() && !COMMAND_PING.equals(command) && !tokenMatches(request.opt(KEY_TOKEN))) {
                    write(out, reply(requestId, false, "bad or missing token"));
                    countBadToken();
                    // A wrong token ends the connection
                    return;
                }
                write(out, dispatch(requestId, command, request));
            }
        } catch (SocketTimeoutException e) {
            // Idle client
        } catch (IOException e) {
            // The client went away or the server stopped
        }
    }

    private static final class LineTooLongException extends IOException {
    }

    // One line without its end, or null at the end of the stream
    private static byte[] readLine(InputStream in) throws IOException {
        final ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return line.toByteArray();
            }
            if (line.size() >= MAX_LINE) {
                throw new LineTooLongException();
            }
            line.write(b);
        }
        return line.size() > 0 ? line.toByteArray() : null;
    }

    private static void write(OutputStream out, JSONObject response) throws IOException {
        out.write((response.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (Exception ignore) {
        }
    }

    // ---------- commands ----------

    private static JSONObject reply(Object requestId, boolean ok, String error) {
        final JSONObject response = new JSONObject();
        try {
            response.put(KEY_REQUEST_ID, requestId != null ? requestId : JSONObject.NULL);
            response.put(KEY_OK, ok);
            if (error != null) {
                response.put(KEY_ERROR, error);
            }
        } catch (Exception ignore) {
        }
        return response;
    }

    private static boolean tokenMatches(Object supplied) {
        final String expected = getToken();
        if (!(supplied instanceof String) || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(((String) supplied).getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static void countBadToken() {
        final boolean tooMany;
        synchronized (lock) {
            tooMany = ++badTokens >= MAX_BAD_TOKENS;
        }
        if (tooMany) {
            FileLog.e("MZGramPluginDevServer: " + MAX_BAD_TOKENS + " wrong tokens, stopping");
            stop();
        }
    }

    // A command with a valid token, or ping
    private static JSONObject dispatch(Object requestId, String command, JSONObject request) {
        if (command.isEmpty()) {
            return reply(requestId, false, "missing \"@\" command");
        }
        try {
            final JSONObject response = reply(requestId, true, null);
            final String error;
            synchronized (commandLock) {
                error = run(command, request, response);
            }
            if (error != null) {
                return reply(requestId, false, error);
            }
            return response;
        } catch (Exception e) {
            return reply(requestId, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // Fills response; returns the error, or null when the command succeeded
    private static String run(String command, JSONObject request, JSONObject response) throws Exception {
        switch (command) {
            case COMMAND_PING:
                response.put("pong", true);
                return null;
            case COMMAND_GET_PLUGINS:
                response.put("plugins", pluginsJson());
                return null;
            case COMMAND_ENABLE:
            case COMMAND_DISABLE: {
                final String pluginId = request.optString(KEY_PLUGIN_ID, "");
                if (MZGramPlugins.get(pluginId) == null) {
                    return unknown(pluginId);
                }
                MZGramPlugins.setEnabled(pluginId, COMMAND_ENABLE.equals(command), MZGramPluginAuditJournal.SOURCE_DEV_SERVER);
                notifyListeners();
                return null;
            }
            case COMMAND_WRITE:
                return write(request, response);
            case COMMAND_REMOVE: {
                final String pluginId = request.optString(KEY_PLUGIN_ID, "");
                if (MZGramPlugins.get(pluginId) == null) {
                    return unknown(pluginId);
                }
                MZGramPlugins.delete(pluginId, MZGramPluginAuditJournal.SOURCE_DEV_SERVER);
                notifyListeners();
                return null;
            }
            case COMMAND_RELOAD: {
                final String pluginId = request.optString(KEY_PLUGIN_ID, "");
                if (MZGramPlugins.get(pluginId) == null) {
                    return unknown(pluginId);
                }
                if (!MZGramPluginEngine.restart(pluginId)) {
                    return MZGramPluginEngine.isOn() ? "the plugin is off; enable_plugin starts it"
                            : "Run plugins is off in MZGram; write_plugin has kept the new file";
                }
                return null;
            }
            case COMMAND_START_DEBUGGER:
            case COMMAND_STOP_DEBUGGER:
                return "debugpy is not available";
            default:
                return "unknown command " + JSONObject.quote(command);
        }
    }

    private static String unknown(String pluginId) {
        return "unknown plugin " + JSONObject.quote(pluginId);
    }

    private static JSONArray pluginsJson() throws Exception {
        final JSONArray array = new JSONArray();
        final List<MZGramPlugins.Plugin> plugins = MZGramPlugins.list();
        for (MZGramPlugins.Plugin plugin : plugins) {
            final JSONObject item = new JSONObject();
            item.put("id", plugin.meta.id);
            item.put("name", plugin.meta.name);
            item.put("version", plugin.meta.version);
            item.put("author", plugin.meta.author.isEmpty() ? JSONObject.NULL : plugin.meta.author);
            item.put("enabled", plugin.enabled);
            final String error = MZGramPluginEngine.getError(plugin.meta.id);
            item.put("loaded", MZGramPluginEngine.getState(plugin.meta.id) == MZGramPluginEngine.STATE_RUNNING);
            item.put("has_settings", MZGramPluginEngine.hasSettings(plugin.meta.id));
            item.put(KEY_ERROR, error != null ? error : JSONObject.NULL);
            array.put(item);
        }
        return array;
    }

    private static String write(JSONObject request, JSONObject response) throws Exception {
        final String pluginId = request.optString(KEY_PLUGIN_ID, "");
        if (pluginId.isEmpty()) {
            return "missing plugin_id";
        }
        final Object content = request.opt(KEY_CONTENT);
        if (!(content instanceof String)) {
            return "missing content (the .plugin file in base64)";
        }
        final byte[] data;
        try {
            data = Base64.decode((String) content, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            return "content is not valid base64";
        }
        if (data.length > MZGramPlugins.MAX_FILE_BYTES) {
            return "the file is larger than " + MZGramPlugins.MAX_FILE_BYTES + " bytes";
        }
        final File file = MZGramPlugins.writeIncoming(data);
        if (file == null) {
            return "cannot write the file";
        }
        final MZGramPluginFormat.Result[] result = {null};
        final CountDownLatch read = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> MZGramPluginFormat.readMetadata(file, value -> {
            result[0] = value;
            read.countDown();
        }));
        if (!read.await(WAIT_SECONDS, TimeUnit.SECONDS) || result[0] == null) {
            MZGramPlugins.discard(file);
            return "reading the metadata timed out";
        }
        if (!result[0].isOk()) {
            MZGramPlugins.discard(file);
            return result[0].error != null ? result[0].error : "invalid metadata";
        }
        final MZGramPluginFormat.Meta meta = result[0].meta;
        if (!pluginId.equals(meta.id)) {
            MZGramPlugins.discard(file);
            return "the file's __id__ is " + JSONObject.quote(meta.id) + ", not " + JSONObject.quote(pluginId);
        }
        final boolean updated = MZGramPlugins.get(meta.id) != null;
        final boolean[] kept = {false};
        final CountDownLatch installed = new CountDownLatch(1);
        MZGramPlugins.install(file, meta, MZGramPluginAuditJournal.SOURCE_DEV_SERVER, ok -> {
            kept[0] = ok != null && ok;
            installed.countDown();
        });
        if (!installed.await(WAIT_SECONDS, TimeUnit.SECONDS) || !kept[0]) {
            return "cannot keep the file";
        }
        notifyListeners();
        response.put("updated", updated);
        return null;
    }
}
