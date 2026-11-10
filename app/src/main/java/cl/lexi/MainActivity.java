package cl.lexi;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Build;
import android.content.res.Configuration;
import android.view.inputmethod.EditorInfo;
import android.speech.*;
import android.speech.tts.*;
import android.view.Gravity;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.json.*;

public final class MainActivity extends Activity {
    private static final int HISTORY_LIMIT = 100;
    private static final int CONTEXT_LIMIT = 9;
    private static final int CONTEXT_BYTES = 10000;
    private static final Locale SPANISH = Locale.forLanguageTag("es-CL");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout page;
    private LexiAvatar avatar;
    private TextView status, transcript;
    private LinearLayout lesson;
    private String lastAnswer = "", activeResource = "";
    private HttpURLConnection activeConnection;
    private volatile int requestId;
    private ScrollView history;
    private EditText input;
    private SpeechRecognizer recognizer;
    private TextToSpeech voice;
    private JSONArray turns = new JSONArray();
    private boolean voiceReady, busy, visible, listening;
    private int speechId, recognitionId;

    private static final class Session {
        final JSONArray turns;
        final String answer, resource, draft;
        Session(JSONArray turns, String answer, String resource, String draft) {
            this.turns = turns;
            this.answer = answer;
            this.resource = resource;
            this.draft = draft;
        }
    }

    @Override public Object onRetainNonConfigurationInstance() {
        return new Session(turns, lastAnswer, activeResource, input == null ? "" : input.getText().toString());
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("lexi", MODE_PRIVATE);
        try { turns = new JSONArray(prefs.getString("history", "[]")); }
        catch (JSONException ignored) {}
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof Session) {
            Session session = (Session) retained;
            turns = session.turns;
            lastAnswer = session.answer;
            activeResource = session.resource;
        }
        showChat();
        if (retained instanceof Session) input.setText(((Session) retained).draft);
        voice = new TextToSpeech(this, result -> new android.os.Handler(getMainLooper()).post(() -> {
            if (isDestroyed() || result != TextToSpeech.SUCCESS) return;
            voiceReady = voice.setLanguage(SPANISH) >= 0 ||
                voice.setLanguage(Locale.forLanguageTag("es-MX")) >= 0 ||
                voice.setLanguage(Locale.forLanguageTag("es")) >= 0;
            voice.setSpeechRate(0.92f);
            voice.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {
                    runOnUiThread(() -> {
                        if (visible && Integer.toString(speechId).equals(id))
                            state("Hablando", LexiAvatar.Mood.SPEAKING);
                    });
                }
                @Override public void onDone(String id) { finishSpeech(id, false); }
                @Override public void onError(String id) { finishSpeech(id, true); }
            });
        }));
    }

    @Override protected void onResume() { super.onResume(); visible = true; Reminder.ensure(this); }
    @Override protected void onPause() {
        visible = false;
        stopListening();
        cancelRequest();
        stopVoice();
        super.onPause();
    }
    @Override protected void onDestroy() {
        worker.shutdownNow();
        if (voice != null) voice.shutdown();
        super.onDestroy();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void layout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(16), dp(24), dp(24));
        page.setBackgroundColor(Color.rgb(245, 240, 255));
        scroll.addView(page);
        setContentView(scroll);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(42, 24, 76));
        view.setPadding(0, dp(8), 0, dp(8));
        page.addView(view);
        return view;
    }

    private Button button(String title, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setTextSize(17);
        view.setMinHeight(dp(52));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(20));
        background.setColor(Color.rgb(95, 54, 161));
        view.setBackground(background);
        view.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        page.addView(view, params);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void showChat() {
        layout();
        ScrollView content = (ScrollView) page.getParent();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 240, 255));
        setContentView(root);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = page;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        label("LEXI · Hola, Marina", 28);
        label(prefs.getBoolean("online", false) && !prefs.getString("endpoint", "").isEmpty()
            ? "Asistente de IA · Servicio configurado" : "Asistente de IA · Servicio sin configurar", 15);
        LinearLayout scene = new LinearLayout(this);
        scene.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        page.addView(scene);
        LinearLayout visual = new LinearLayout(this);
        visual.setOrientation(LinearLayout.VERTICAL);
        scene.addView(visual, landscape ? new LinearLayout.LayoutParams(0, -2, 1)
            : new LinearLayout.LayoutParams(-1, -2));
        page = visual;
        avatar = new LexiAvatar(this);
        avatar.reduceMotion(prefs.getBoolean("reduced_motion", false));
        LexiAvatar sceneAvatar = avatar;
        content.setOnScrollChangeListener((view, x, y, oldX, oldY) -> sceneAvatar.invalidate());
        page.addView(avatar, new LinearLayout.LayoutParams(-1,
            dp(landscape ? 128 : 184)));
        status = label(busy ? "Pensando" : "Lista para conversar", 16);
        if (busy) avatar.show(LexiAvatar.Mood.THINKING);
        LinearLayout dialogue = new LinearLayout(this);
        dialogue.setOrientation(LinearLayout.VERTICAL);
        scene.addView(dialogue, landscape ? new LinearLayout.LayoutParams(0, -2, 2)
            : new LinearLayout.LayoutParams(-1, -2));
        page = dialogue;
        lesson = new LinearLayout(this);
        lesson.setOrientation(LinearLayout.VERTICAL);
        page.addView(lesson);
        showResource(activeResource);
        history = new ScrollView(this);
        transcript = new TextView(this);
        transcript.setTextSize(17);
        transcript.setTextIsSelectable(true);
        history.addView(transcript);
        page.addView(history, new LinearLayout.LayoutParams(-1, dp(landscape ? 148 : 172)));
        render();
        page = body;
        button("Aprender", () -> activities(true));
        button("Jugar y crear", () -> activities(false));
        button("Tareas · Lavarse los dientes", this::tasks);
        label("Ajustes", 15);
        Button parental = button("Panel parental", this::parentGate);
        parental.setTextSize(14);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), 0, dp(12), dp(4));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(this);
        controls.addView(actions);
        page = actions;
        compact(button("Hablar", this::listen), 2);
        compact(button("Detener", () -> {
            stopListening();
            cancelRequest();
            stopVoice();
        }), 1);
        compact(button("Repetir", () -> {
            if (busy) return;
            if (lastAnswer.isEmpty()) toast("Todavía no hay una explicación para repetir.");
            else { stopListening(); speak(lastAnswer); }
        }), 1);
        LinearLayout composer = new LinearLayout(this);
        controls.addView(composer);
        page = composer;
        input = new EditText(this);
        input.setHint("Escribe una pregunta");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 3));
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEND) return false;
            sendText();
            return true;
        });
        compact(button("Enviar", this::sendText), 1);
        page = body;
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private void compact(Button button, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, weight);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setTextSize(14);
        button.setLayoutParams(params);
    }

    private void sendText() {
        if (busy) return;
        String text = input.getText().toString();
        input.setText("");
        submit(text);
    }

    private void tasks() {
        layout();
        label("Lavarse los dientes · Todos los días a las 21:00", 25);
        label(Reminder.done(this) ? "Completada hoy" : "Pendiente", 18);
        button("Listo", () -> { Reminder.complete(this); tasks(); });
        button("Volver", this::showChat);
    }

    private void activities(boolean school) {
        layout();
        label(school ? "Explora y aprende" : "Juega con LEXI", 27);
        String[] prompts = school
            ? new String[]{"Practiquemos multiplicaciones paso a paso", "Enséñame ciencias con un ejemplo", "Practiquemos inglés con un juego", "Ayúdame a comprender una lectura"}
            : new String[]{"Cuéntame un cuento original", "Juguemos a las adivinanzas", "Comencemos una aventura donde yo elijo", "Juguemos a recordar palabras"};
        if (school) {
            button("Explorar el sistema solar", () -> { showChat(); submit("Enséñame los planetas"); });
            button("Multiplicar 8 por 4", () -> { showChat(); submit("¿Cuánto es 8 por 4?"); });
        }
        for (String prompt : prompts) button(prompt, () -> { showChat(); submit(prompt); });
        button("Volver", this::showChat);
    }

    private void state(String text, LexiAvatar.Mood mood) {
        status.setText(text);
        avatar.show(mood);
    }

    private void showResource(String resource) {
        activeResource = resource;
        lesson.removeAllViews();
        boolean solar = "solar".equals(resource);
        if (!solar && !"multiply_8_4".equals(resource)) return;
        LessonArt art = new LessonArt(this, solar);
        lesson.addView(art, new LinearLayout.LayoutParams(-1, dp(112)));
        TextView detail = new TextView(this);
        detail.setTextSize(18);
        detail.setTextColor(Color.rgb(42, 24, 76));
        lesson.addView(detail);
        if (!solar) {
            detail.setText("4 grupos de 8 · 8 + 8 + 8 + 8 = 32");
            return;
        }
        String[] names = {"Mercurio", "Venus", "Tierra", "Marte", "Júpiter", "Saturno", "Urano", "Neptuno"};
        String[] facts = {"Está más cerca del Sol.", "Es el planeta más caliente.", "Es nuestro hogar.",
            "Se conoce como el planeta rojo.", "Es el planeta más grande.", "Tiene grandes anillos visibles.",
            "Gira muy inclinado.", "Es el planeta más lejano del Sol."};
        detail.setText("Mercurio · " + facts[0]);
        Spinner planets = new Spinner(this);
        planets.setContentDescription("Elige un planeta");
        planets.setMinimumHeight(dp(48));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        planets.setAdapter(adapter);
        lesson.addView(planets);
        Button explain = new Button(this);
        explain.setText("Escuchar sobre este planeta");
        explain.setMinHeight(dp(48));
        lesson.addView(explain);
        explain.setOnClickListener(v -> {
            int index = planets.getSelectedItemPosition();
            if (busy || index < 0) return;
            stopListening();
            String fact = names[index] + ". " + facts[index];
            lastAnswer = fact;
            speak(fact);
        });
        planets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, android.view.View item, int index, long id) {
                art.select(index);
                detail.setText(names[index] + " · " + facts[index]);
            }
        });
    }

    private void render() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < turns.length(); i++) {
            JSONObject turn = turns.optJSONObject(i);
            text.append("user".equals(turn.optString("role")) ? "Tú · " : "LEXI · ");
            text.append(turn.optString("content")).append("\n\n");
        }
        transcript.setText(text);
        ScrollView currentHistory = history;
        currentHistory.post(() -> currentHistory.scrollTo(0, currentHistory.getChildAt(0).getHeight()));
    }

    private void append(String role, String content) {
        try {
            turns.put(new JSONObject().put("role", role).put("content", content));
            while (turns.length() > HISTORY_LIMIT) turns.remove(0);
            if (prefs.getBoolean("memory", false))
                prefs.edit().putString("history", turns.toString()).apply();
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        render();
    }

    private void stopVoice() {
        speechId++;
        if (voice != null) voice.stop();
        if (!busy && !listening) state("Lista para conversar", LexiAvatar.Mood.IDLE);
    }

    private void finishSpeech(String id, boolean failed) {
        runOnUiThread(() -> {
            if (!isDestroyed() && Integer.toString(speechId).equals(id) && !busy && !listening)
                state(failed ? "No pude leer la respuesta · Puedes leerla en pantalla" : "Lista para conversar",
                    failed ? LexiAvatar.Mood.OFFLINE : LexiAvatar.Mood.IDLE);
        });
    }

    private void reply(String text) {
        reply(text, LexiAvatar.Mood.HAPPY);
    }

    private void reply(String text, LexiAvatar.Mood emotion) {
        append("assistant", text);
        lastAnswer = text;
        state("Respuesta lista", emotion);
        if (prefs.getBoolean("auto_voice", true)) speak(text);
    }

    private void speak(String text) {
        if (visible && voiceReady) {
            if (voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, Integer.toString(++speechId))
                    == TextToSpeech.SUCCESS) return;
        }
        state("Voz no disponible · Lee la respuesta", LexiAvatar.Mood.OFFLINE);
    }

    private void cancelRequest() {
        HttpURLConnection connection;
        synchronized (this) {
            requestId++;
            busy = false;
            connection = activeConnection;
            activeConnection = null;
        }
        if (connection != null) new Thread(connection::disconnect, "lexi-cancel").start();
    }

    private void listen() {
        if (busy || listening) return;
        if (!prefs.getBoolean("allow_mic", true)) {
            state("Micrófono desactivado en el panel parental", LexiAvatar.Mood.OFFLINE);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        boolean system = SpeechRecognizer.isRecognitionAvailable(this);
        boolean local = !system && Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        if (!system && !local) {
            state("El reconocimiento de voz no está disponible. Puedes escribir.", LexiAvatar.Mood.CONFUSED);
            return;
        }
        stopVoice();
        try {
            recognizer = local ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                : SpeechRecognizer.createSpeechRecognizer(this);
        } catch (RuntimeException unavailable) {
            state("No pude iniciar el dictado · Puedes escribir", LexiAvatar.Mood.OFFLINE);
            return;
        }
        final int attempt = ++recognitionId;
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) {}
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rms) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() {
                if (listening && attempt == recognitionId) state("Reconociendo tu pregunta", LexiAvatar.Mood.THINKING);
            }
            public void onError(int error) {
                if (!listening || attempt != recognitionId) return;
                stopListening();
                String message;
                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO:
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        message = "Micrófono ocupado o no disponible · Reintenta o escribe";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        message = "El dictado no pudo conectarse · Puedes escribir";
                        break;
                    case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                    case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                        message = "El motor no tiene disponible el español de Chile · Puedes escribir";
                        break;
                    default:
                        message = "No entendí · Reintenta o escribe";
                }
                state(message, LexiAvatar.Mood.CONFUSED);
            }
            public void onResults(Bundle b) {
                if (!listening || attempt != recognitionId) return;
                stopListening();
                ArrayList<String> results = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (results != null && !results.isEmpty()) submit(results.get(0));
                else state("No entendí. Prueba otra vez.", LexiAvatar.Mood.CONFUSED);
            }
            public void onPartialResults(Bundle b) {}
            public void onEvent(int type, Bundle b) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL");
        listening = true;
        state("Micrófono activo · Escuchando", LexiAvatar.Mood.LISTENING);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try { recognizer.startListening(intent); }
        catch (RuntimeException unavailable) {
            stopListening();
            state("No pude usar el micrófono · Reintenta o escribe", LexiAvatar.Mood.OFFLINE);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1 && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED))
            state("Micrófono sin permiso · Puedes escribir", LexiAvatar.Mood.OFFLINE);
    }

    private void stopListening() {
        listening = false;
        recognitionId++;
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }

    private void submit(String raw) {
        if (busy) { state("Ya estoy pensando en tu pregunta", LexiAvatar.Mood.CONFUSED); return; }
        String text = raw.trim().replaceFirst("(?iu)^lexi\\b[\\s,.:¡!¿?]*", "");
        if (text.isEmpty()) { state("Dime qué quieres hacer", LexiAvatar.Mood.IDLE); return; }
        stopListening();
        stopVoice();
        text = text.substring(0, Math.min(text.length(), 900));
        append("user", text);
        showResource("");
        String command = Normalizer.normalize(text.toLowerCase(SPANISH), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replaceAll("[¿?¡!.]+", "").trim();
        if (command.matches("((ensename |muestrame |exploremos )?(los planetas|el sistema solar)|cuales son los planetas)")) {
            showResource("solar");
            reply("Mercurio es el planeta más cercano al Sol. Elige otro planeta para explorarlo.");
            return;
        }
        if (command.matches("(cuanto es )?8 (por|x) 4")) {
            showResource("multiply_8_4");
            reply("Ocho por cuatro son cuatro grupos de ocho. Mira los grupos en la tarjeta: juntos son treinta y dos.");
            return;
        }
        if (command.equals("listo") && prefs.getBoolean("reminder", false)) {
            Reminder.complete(this);
            reply("¡Listo! Marqué la tarea de hoy.");
            return;
        }
        if (command.matches("(dime )?(que hora es|la hora)")) {
            reply("Son las " + DateFormat.getTimeInstance(DateFormat.SHORT, SPANISH).format(new Date()));
            return;
        }
        if (command.matches("(dime )?(que fecha es|que dia es( hoy)?|la fecha)")) {
            reply("Hoy es " + DateFormat.getDateInstance(DateFormat.FULL, SPANISH).format(new Date()));
            return;
        }
        if (command.matches("abre youtube( para escuchar musica)?") ||
            command.matches("busca .+ en youtube") || command.startsWith("busca videos de ")) {
            if (!prefs.getBoolean("youtube", false)) { reply("YouTube está desactivado. Pide permiso a un adulto."); return; }
            if (command.startsWith("abre ")) openYoutube("");
            else openYoutube(text.replaceFirst("(?iu)^busca\\s+", "").replaceFirst("(?iu)\\s+en youtube[.!?]*$", ""));
            return;
        }
        String endpoint = prefs.getString("endpoint", "");
        String token;
        try { token = SecretStore.get(prefs); }
        catch (Exception e) { state("Revisa la conexión en el panel parental.", LexiAvatar.Mood.CONFUSED); return; }
        if (!prefs.getBoolean("online", false) || endpoint.isEmpty() || token.isEmpty()) {
            state("La IA en línea se configura en el panel parental.", LexiAvatar.Mood.CONFUSED);
            return;
        }
        JSONArray context = recentContext();
        busy = true;
        int current = ++requestId;
        state("Pensando", LexiAvatar.Mood.THINKING);
        worker.execute(() -> {
            try {
                JSONObject answer = ask(endpoint, token, context, current);
                runOnUiThread(() -> {
                    if (isDestroyed() || current != requestId) return;
                    busy = false;
                    showResource(answer.optString("resource", ""));
                    String emotion = answer.optString("emotion");
                    reply(answer.optString("answer"), "surprised".equals(emotion)
                        ? LexiAvatar.Mood.SURPRISED : "confused".equals(emotion)
                        ? LexiAvatar.Mood.CONFUSED : LexiAvatar.Mood.HAPPY);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || current != requestId) return;
                    busy = false;
                    state("No pude conectarme. Revisa el Wi-Fi o intenta más tarde.", LexiAvatar.Mood.OFFLINE);
                });
            }
        });
    }

    private JSONArray recentContext() {
        int start = turns.length();
        int bytes = 32;
        while (start > 0 && turns.length() - start < CONTEXT_LIMIT) {
            int size = turns.optJSONObject(start - 1).toString().getBytes(StandardCharsets.UTF_8).length + 1;
            if (bytes + size > CONTEXT_BYTES) break;
            bytes += size;
            start--;
        }
        JSONArray context = new JSONArray();
        for (int i = start; i < turns.length(); i++) context.put(turns.optJSONObject(i));
        return context;
    }

    private JSONObject ask(String endpoint, String token, JSONArray context, int current) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        synchronized (this) {
            if (requestId != current) throw new IOException("Cancelled");
            activeConnection = connection;
        }
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            byte[] data = new JSONObject().put("messages", context).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream stream = connection.getOutputStream()) { stream.write(data); }
            if (connection.getResponseCode() != 200) throw new IOException("Request failed");
            try (InputStream stream = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    if (buffer.size() + count > 8192) throw new IOException("Response too large");
                    buffer.write(chunk, 0, count);
                }
                JSONObject result = new JSONObject(buffer.toString("UTF-8"));
                String answer = result.getString("answer").trim();
                if (answer.isEmpty() || answer.length() > 1500) throw new IOException("Invalid answer");
                return result;
            }
        } finally {
            synchronized (this) {
                if (activeConnection == connection) activeConnection = null;
            }
            connection.disconnect();
        }
    }

    private void openYoutube(String query) {
        Uri uri = query.isEmpty() ? Uri.parse("https://www.youtube.com/") :
            Uri.parse("https://www.youtube.com/results").buildUpon().appendQueryParameter("search_query", query).build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube");
        try { startActivity(intent); }
        catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this).setMessage("YouTube no está disponible. ¿Abrir en el navegador?")
                .setNegativeButton("Cancelar", null).setPositiveButton("Abrir", (d, w) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) { state("No hay un navegador disponible.", LexiAvatar.Mood.CONFUSED); }
                }).show();
        }
    }

    private void parentGate() {
        boolean setup = !prefs.contains("pin_hash");
        if (System.currentTimeMillis() < prefs.getLong("locked_until", 0)) {
            Toast.makeText(this, "Espera un minuto antes de volver a intentar.", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText pin = new EditText(this);
        pin.setInputType(18);
        new AlertDialog.Builder(this).setTitle(setup ? "Adulto: crea un PIN antes de entregar la tablet" : "PIN parental")
            .setView(pin).setNegativeButton("Cancelar", null).setPositiveButton("Continuar", (d, w) -> {
                String value = pin.getText().toString();
                if (!value.matches("[0-9]{6,12}")) { toast("Usa entre 6 y 12 dígitos."); return; }
                try {
                    byte[] salt;
                    if (setup) {
                        salt = new byte[16];
                        new SecureRandom().nextBytes(salt);
                    } else salt = Base64.getDecoder().decode(prefs.getString("pin_salt", ""));
                    byte[] digest = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(new PBEKeySpec(value.toCharArray(), salt, 210000, 256)).getEncoded();
                    if (setup) prefs.edit().putString("pin_salt", Base64.getEncoder().encodeToString(salt))
                        .putString("pin_hash", Base64.getEncoder().encodeToString(digest)).commit();
                    else if (!MessageDigest.isEqual(digest, Base64.getDecoder().decode(prefs.getString("pin_hash", "")))) {
                        int failures = prefs.getInt("pin_failures", 0) + 1;
                        prefs.edit().putInt("pin_failures", failures >= 5 ? 0 : failures)
                            .putLong("locked_until", failures >= 5 ? System.currentTimeMillis() + 60000 : 0).apply();
                        toast("PIN incorrecto.");
                        return;
                    }
                    prefs.edit().remove("pin_failures").remove("locked_until").apply();
                    parental();
                } catch (GeneralSecurityException e) { toast("No se pudo verificar el PIN."); }
            }).show();
    }

    private void parental() {
        stopListening();
        stopVoice();
        layout();
        label("Panel parental", 26);
        label("Android puede utilizar un proveedor externo para reconocer la voz. Al activar la IA, el servidor recibe texto y contexto reciente.", 16);
        toggle("Recordatorio diario a las 21:00", "reminder");
        button("Permisos de los recordatorios", () -> {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2);
            } else if (android.os.Build.VERSION.SDK_INT >= 31 &&
                !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
            } else toast("Los permisos necesarios ya están disponibles.");
        });
        toggle("Permitir micrófono", "allow_mic", true);
        toggle("Leer respuestas automáticamente", "auto_voice", true);
        toggle("Reducir animaciones", "reduced_motion");
        toggle("Permitir YouTube normal", "youtube");
        toggle("Permitir IA en línea", "online");
        toggle("Guardar historial en la tablet", "memory");
        button("Borrar historial", () -> {
            cancelRequest();
            lastAnswer = "";
            activeResource = "";
            turns = new JSONArray();
            prefs.edit().remove("history").apply();
            toast("Historial borrado.");
        });
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setHint("https://servidor.example/chat");
        url.setText(prefs.getString("endpoint", ""));
        page.addView(url);
        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setInputType(129);
        token.setHint("Token de acceso de la tablet");
        page.addView(token);
        button("Guardar conexión", () -> {
            String address = url.getText().toString().trim();
            try {
                URI uri = new URI(address);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                    throw new IllegalArgumentException();
                String secret = token.getText().toString().trim();
                if (!address.equals(prefs.getString("endpoint", "")) && secret.isEmpty())
                    throw new IllegalArgumentException();
                if (!secret.isEmpty()) SecretStore.put(prefs, secret);
                prefs.edit().putString("endpoint", address).apply();
                token.setText("");
                toast("Conexión guardada.");
            } catch (Exception e) { toast("Revisa la URL HTTPS y el token."); }
        });
        button("Cerrar panel", this::showChat);
    }

    private void toggle(String title, String key) { toggle(title, key, false); }

    private void toggle(String title, String key, boolean defaultValue) {
        Switch view = new Switch(this);
        view.setText(title);
        view.setMinHeight(dp(48));
        view.setChecked(prefs.getBoolean(key, defaultValue));
        page.addView(view);
        view.setOnCheckedChangeListener((v, checked) -> {
            SharedPreferences.Editor edit = prefs.edit().putBoolean(key, checked);
            if ("memory".equals(key) && !checked) edit.remove("history");
            if ("online".equals(key) && !checked) cancelRequest();
            edit.apply();
            if ("reminder".equals(key)) Reminder.reset(this);
        });
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}






