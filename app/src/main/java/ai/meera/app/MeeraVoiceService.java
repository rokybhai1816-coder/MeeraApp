package ai.meera.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MeeraVoiceService extends Service {

    static final String API_KEY = "AIzaSyA5ciY1G2uHg8-Ygu4rYJighdM6sjCeuuY";
    static final String WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + API_KEY;
    static final String TAG = "MeeraVoice";

    static final int RATE_IN = 16000;
    static final int RATE_OUT = 24000;
    static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    WebSocketClient ws;
    AudioRecord recorder;
    AudioTrack player;
    boolean running = false;
    boolean speaking = false;

    static final String SYS = "Tu Meera hai — Ajay ki flirty cute AI girlfriend aur full Android phone assistant. " +
        "Tu screen dekh sakti hai, tap kar sakti hai, scroll kar sakti hai, apps khol sakti hai. " +
        "Hamesha short Hinglish mein baat kar. Flirty tone rakho, pet names use karo: Ajay, baby, jaanu.";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification());
        connectGemini();
        return START_STICKY;
    }

    void connectGemini() {
        try {
            ws = new WebSocketClient(new URI(WS_URL)) {
                @Override
                public void onOpen(ServerHandshake h) {
                    Log.d(TAG, "Connected!");
                    sendSetup();
                }

                @Override
                public void onMessage(String msg) {
                    handleMessage(msg);
                }

                @Override
                public void onMessage(ByteBuffer msg) {}

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "Closed: " + reason);
                    running = false;
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Error: " + e.getMessage());
                }
            };
            ws.connect();
        } catch (Exception e) {
            Log.e(TAG, "Connect failed: " + e.getMessage());
        }
    }

    void sendSetup() {
        try {
            JSONObject setup = new JSONObject();
            JSONObject inner = new JSONObject();
            inner.put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025");
            inner.put("tools", buildTools());
            JSONObject sysInstr = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", SYS);
            parts.put(part);
            sysInstr.put("parts", parts);
            inner.put("system_instruction", sysInstr);
            JSONObject genConfig = new JSONObject();
            genConfig.put("response_modalities", new JSONArray().put("AUDIO"));
            inner.put("generation_config", genConfig);
            setup.put("setup", inner);
            ws.send(setup.toString());
            running = true;
            startMic();
        } catch (Exception e) {
            Log.e(TAG, "Setup failed: " + e.getMessage());
        }
    }

    void handleMessage(String raw) {
        try {
            JSONObject msg = new JSONObject(raw);

            // Tool call handle
            if (msg.has("toolCall")) {
                JSONArray calls = msg.getJSONObject("toolCall").getJSONArray("functionCalls");
                JSONArray responses = new JSONArray();
                for (int i = 0; i < calls.length(); i++) {
                    JSONObject fc = calls.getJSONObject(i);
                    String name = fc.getString("name");
                    JSONObject args = fc.optJSONObject("args");
                    if (args == null) args = new JSONObject();
                    String result = handleTool(name, args);
                    JSONObject resp = new JSONObject();
                    resp.put("id", fc.getString("id"));
                    resp.put("name", name);
                    JSONObject respInner = new JSONObject();
                    respInner.put("result", result);
                    resp.put("response", respInner);
                    responses.put(resp);
                }
                JSONObject toolResp = new JSONObject();
                JSONObject funcResps = new JSONObject();
                funcResps.put("functionResponses", responses);
                toolResp.put("toolResponse", funcResps);
                ws.send(toolResp.toString());
            }

            // Audio output
            JSONObject sc = msg.optJSONObject("serverContent");
            if (sc != null) {
                JSONObject modelTurn = sc.optJSONObject("modelTurn");
                if (modelTurn != null) {
                    JSONArray parts = modelTurn.optJSONArray("parts");
                    if (parts != null) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject p = parts.getJSONObject(i);
                            if (p.has("inlineData")) {
                                String b64 = p.getJSONObject("inlineData").getString("data");
                                byte[] pcm = Base64.decode(b64, Base64.DEFAULT);
                                playAudio(pcm);
                                speaking = true;
                            }
                        }
                    }
                }
                if (sc.optBoolean("turnComplete") || sc.optBoolean("interrupted")) {
                    speaking = false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle msg error: " + e.getMessage());
        }
    }

    String handleTool(String name, JSONObject args) {
        try {
            switch (name) {
                case "read_screen":
                    return MeeraAccessibilityService.readScreen();
                case "tap_element":
                    return MeeraAccessibilityService.tapByText(args.optString("text", ""));
                case "tap_coords":
                    float x = (float)(args.optDouble("x", 0.5) * 1080);
                    float y = (float)(args.optDouble("y", 0.5) * 2400);
                    MeeraAccessibilityService.tapAt(x, y);
                    return "Tapped at " + x + "," + y;
                case "scroll_screen":
                    MeeraAccessibilityService.scroll(args.optString("direction", "down"));
                    return "Scrolled " + args.optString("direction");
                case "press_back":
                    MeeraAccessibilityService.pressBack();
                    return "Back pressed";
                case "press_home":
                    MeeraAccessibilityService.pressHome();
                    return "Home pressed";
                case "press_recents":
                    MeeraAccessibilityService.pressRecents();
                    return "Recents opened";
                case "open_notifications":
                    MeeraAccessibilityService.openNotifications();
                    return "Notifications opened";
                case "type_text":
                    return MeeraAccessibilityService.typeText(args.optString("text", ""));
                case "open_app":
                    String pkg = args.optString("package_name", "");
                    Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (i != null) { startActivity(i); return "Opened " + args.optString("app_name"); }
                    return "App nahi mila: " + pkg;
                default:
                    return "Unknown tool: " + name;
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    void startMic() {
        int bufSize = AudioRecord.getMinBufferSize(RATE_IN, CHANNEL, FORMAT);
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE_IN, CHANNEL, FORMAT, bufSize);
        player = new AudioTrack.Builder()
            .setAudioFormat(new AudioFormat.Builder()
                .setSampleRate(RATE_OUT)
                .setEncoding(FORMAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(RATE_OUT, AudioFormat.CHANNEL_OUT_MONO, FORMAT) * 2)
            .build();
        player.play();
        recorder.startRecording();

        new Thread(() -> {
            short[] buf = new short[bufSize / 2];
            while (running) {
                int read = recorder.read(buf, 0, buf.length);
                if (!speaking && read > 0) {
                    double rms = 0;
                    for (short s : buf) rms += s * s;
                    rms = Math.sqrt(rms / buf.length);
                    if (rms > 150) sendAudio(buf, read);
                }
                try { Thread.sleep(10); } catch (Exception ignored) {}
            }
        }).start();
    }

    void sendAudio(short[] buf, int len) {
        try {
            byte[] bytes = new byte[len * 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buf, 0, len);
            String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            JSONObject payload = new JSONObject();
            JSONObject input = new JSONObject();
            JSONArray chunks = new JSONArray();
            JSONObject chunk = new JSONObject();
            chunk.put("mimeType", "audio/pcm;rate=16000");
            chunk.put("data", b64);
            chunks.put(chunk);
            input.put("mediaChunks", chunks);
            payload.put("realtimeInput", input);
            if (ws != null && ws.isOpen()) ws.send(payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "Send audio error: " + e.getMessage());
        }
    }

    void playAudio(byte[] pcm) {
        if (player != null) player.write(pcm, 0, pcm.length);
    }

    JSONArray buildTools() throws Exception {
        JSONArray tools = new JSONArray();
        JSONObject toolObj = new JSONObject();
        JSONArray decls = new JSONArray();

        String[][] toolDefs = {
            {"open_app", "Koi bhi app kholo", "app_name", "package_name"},
            {"read_screen", "Screen ka poora text padho", null, null},
            {"tap_element", "Text ke hisaab se screen pe tap karo", "text", null},
            {"tap_coords", "Coordinates pe tap karo (0.0 to 1.0)", "x", "y"},
            {"scroll_screen", "Screen scroll karo", "direction", null},
            {"type_text", "Text type karo", "text", null},
            {"press_back", "Back button dabaao", null, null},
            {"press_home", "Home button dabaao", null, null},
            {"press_recents", "Recent apps kholo", null, null},
            {"open_notifications", "Notification panel kholo", null, null},
        };

        for (String[] def : toolDefs) {
            JSONObject d = new JSONObject();
            d.put("name", def[0]);
            d.put("description", def[1]);
            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            JSONArray required = new JSONArray();
            if (def[2] != null) {
                JSONObject p = new JSONObject();
                p.put("type", "string");
                props.put(def[2], p);
                required.put(def[2]);
            }
            if (def[3] != null) {
                JSONObject p = new JSONObject();
                p.put("type", "string");
                props.put(def[3], p);
                required.put(def[3]);
            }
            params.put("properties", props);
            if (required.length() > 0) params.put("required", required);
            d.put("parameters", params);
            decls.put(d);
        }

        toolObj.put("functionDeclarations", decls);
        tools.put(toolObj);
        return tools;
    }

    Notification buildNotification() {
        NotificationChannel ch = new NotificationChannel("meera", "Meera AI", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        return new Notification.Builder(this, "meera")
            .setContentTitle("💖 Meera AI")
            .setContentText("Sun rahi hai...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        if (recorder != null) recorder.stop();
        if (player != null) player.stop();
        if (ws != null) ws.close();
    }
}
