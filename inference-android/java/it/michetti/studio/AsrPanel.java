package it.michetti.studio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.AtomicFile;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

final class AsrPanel {
    private static final String MODEL = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb";
    private static final long MODEL_BYTES = 190085487L;
    private static final AtomicInteger nextTicket = new AtomicInteger(1);
    private static final WeakHashMap<Activity, AsrPanel> panels = new WeakHashMap<>();
    private final Activity activity;
    private final TextView state;
    private final Button chooseModel, chooseAudio, cancel;
    private volatile boolean disposed, inNative;
    private volatile int ticket;
    private Thread worker;

    static void add(Activity a, LinearLayout parent) {
        detach(a);
        panels.put(a, new AsrPanel(a, parent));
    }
    static void detach(Activity a) {
        AsrPanel p = panels.remove(a);
        if (p != null) {
            p.disposed = true;
            p.stop();
        }
    }
    static void onResult(Activity a, int code, Intent data) {
        AsrPanel p = panels.get(a);
        if (p != null && (code == 12 || code == 13) && data != null && data.getData() != null)
            p.start(code, data.getData());
    }
    private AsrPanel(Activity a, LinearLayout parent) {
        activity = a;
        state = new TextView(a);
        state.setTextColor(Color.rgb(246,241,227));
        state.setTextIsSelectable(true);
        chooseModel = button(parent,"Importa modello Whisper small Q5_1", () -> pick(12,"*/*"));
        chooseAudio = button(parent,"Trascrivi narrazione in italiano", () -> pick(13,"audio/*"));
        cancel = button(parent,"Annulla trascrizione o importazione", this::stop);
        parent.addView(state);
        state.setText(model().isFile() ? "Modello ASR presente." : "Seleziona il modello ggml-small-q5_1.bin.");
        controls(false);
    }
    private File model() { return new File(activity.getFilesDir(), MODEL + ".bin"); }
    private Button button(LinearLayout parent,String text,Runnable action) {
        Button b=new Button(activity);
        b.setText(text);
        b.setOnClickListener(v -> action.run());
        parent.addView(b);
        return b;
    }
    private void pick(int code,String type) {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        activity.startActivityForResult(i,code);
    }
    private void controls(boolean busy) {
        chooseModel.setEnabled(!busy);
        chooseAudio.setEnabled(!busy && model().isFile());
        cancel.setEnabled(busy);
    }
    private void stop() {
        if (worker != null) worker.interrupt();
        if (inNative) LocalWhisper.cancel(ticket);
    }
    private void status(String text) {
        activity.runOnUiThread(() -> { if (!disposed) state.setText(text); });
    }
    private String copy(Uri uri, File dest,long limit) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try (InputStream in=activity.getContentResolver().openInputStream(uri);
             FileOutputStream out=new FileOutputStream(dest)) {
            if(in==null) throw new IOException("File non apribile.");
            byte[] buf=new byte[64*1024];
            long total=0;
            int n;
            while((n=in.read(buf))!=-1) {
                AudioPcm.check();
                total+=n;
                if(total>limit) throw new IOException("Il file supera il limite di importazione.");
                digest.update(buf,0,n);
                out.write(buf,0,n);
            }
            out.getFD().sync();
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:digest.digest()) hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private void start(int code,Uri uri) {
        if(worker!=null && worker.isAlive()) return;
        controls(true);
        ticket=nextTicket.getAndIncrement();
        worker=new Thread(() -> {
            File staging=null;
            boolean acquired=M0Memory.acquire();
            try {
                if (!acquired) throw new IOException("Memoria occupata da un’altra attività. Attendi e riprova.");
                if(code==12) {
                    status("Verifica del modello in corso…");
                    staging=File.createTempFile("asr-model-", ".part",activity.getFilesDir());
                    String sha=copy(uri,staging,MODEL_BYTES);
                    if(!MODEL.equals(sha) || staging.length()!=MODEL_BYTES)
                        throw new IOException("Modello inatteso. Serve ggml-small-q5_1.bin con SHA-256 " + MODEL);
                    AudioPcm.check();
                    if(!staging.renameTo(model())) throw new IOException("Salvataggio modello fallito.");
                    status("Modello Whisper small Q5_1 verificato.");
                } else {
                    status("Importazione e decodifica della narrazione…");
                    staging=File.createTempFile("asr-audio-", ".part",activity.getCacheDir());
                    String sha=copy(uri,staging,512L*1024*1024);
                    short[] pcm=AudioPcm.decode(staging);
                    status("Trascrizione locale in corso…");
                    inNative=true;
                    AudioPcm.check();
                    byte[] bytes=LocalWhisper.transcribe(model().getPath(),pcm,
                        Math.min(4,Runtime.getRuntime().availableProcessors()),ticket);
                    inNative=false;
                    AudioPcm.check();
                    JSONObject result=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                    result.put("audioSha256",sha);
                    result.put("modelSha256",MODEL);
                    JSONArray segments=result.getJSONArray("segments");
                    StringBuilder transcript=new StringBuilder();
                    for(int j=0;j<segments.length();j++) {
                        if(j>0) transcript.append(' ');
                        transcript.append(segments.getJSONObject(j).getString("text").trim());
                    }
                    AtomicFile target=new AtomicFile(new File(activity.getFilesDir(),"asr-cues.json"));
                    synchronized(AsrPanel.class) {
                        AudioPcm.check();
                        FileOutputStream out=target.startWrite();
                        try {
                            out.write(result.toString().getBytes(StandardCharsets.UTF_8));
                            target.finishWrite(out);
                        } catch(Exception e) {
                            target.failWrite(out); throw e;
                        }
                    }
                    activity.runOnUiThread(() -> {
                        if(!disposed) {
                            ModelPanel.setTranscript(activity,transcript.toString());
                            state.setText(segments.length()+" segmenti salvati.\n"+transcript);
                        }
                    });
                }
            } catch(Exception | UnsatisfiedLinkError e) {
                status("ASR: "+e.getMessage());
            } finally {
                inNative=false;
                if(acquired) M0Memory.release();
                if(staging!=null && staging.exists()) staging.delete();
                activity.runOnUiThread(() -> { if(!disposed) controls(false); });
            }
        },"studio-asr");
        worker.start();
    }
}
