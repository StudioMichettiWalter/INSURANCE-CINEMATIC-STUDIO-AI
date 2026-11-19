package it.michetti.studio;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class ModelPanel {
    private static final String TAG = "local-model-panel";
    private static final ExecutorService jobs = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean busy = new AtomicBoolean();
    private static final long MODEL_BYTES = 491400032L; // checked again by SHA-256
    private final EditText transcript;
    private final TextView status;
    private final Button choose, run;

    private ModelPanel(Activity activity, LinearLayout parent) {
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setTag(TAG);
        parent.addView(group);
        group.setTag(0x7f010012, this);
        choose = new Button(activity);
        choose.setText("Importa modello Qwen Q4_K_M");
        group.addView(choose);
        choose.setOnClickListener(v -> activity.startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 11));
        transcript = new EditText(activity);
        transcript.setHint("Segmento trascritto da analizzare");
        transcript.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2500)});
        group.addView(transcript);
        run = new Button(activity);
        run.setText("Proponi titolo e parola chiave");
        group.addView(run);
        status = new TextView(activity);
        status.setTextColor(0xffffffff);
        group.addView(status);
        restore(activity);
        run.setOnClickListener(v -> {
            String text = transcript.getText().toString().trim();
            task(activity, () -> {
                JSONObject answer = LocalModel.infer(activity, model(activity),text);
                if (!prefs(activity).edit().putString("text",text).putString("answer",answer.toString()).commit())
                    throw new IOException("Salvataggio proposta fallito.");
                return describe(answer);
            });
        });
    }
    public static void add(Activity a, LinearLayout parent) { new ModelPanel(a,parent); }
    public static void setTranscript(Activity a, String text) {
        ModelPanel p = panel(a);
        if (p!=null) p.transcript.setText(text);
    }
    public static String keyword(Activity a,String transcript) {
        SharedPreferences prefs = prefs(a);
        if (!prefs.getString("text","").equals(transcript.trim())) return "";
        try { return new JSONObject(prefs.getString("answer","{}")).getString("keyword"); }
        catch (Exception ignored) { return ""; }
    }
    private static ModelPanel panel(Activity a) {
        View group = a.getWindow().getDecorView().findViewWithTag(TAG);
        return group==null ? null : (ModelPanel) group.getTag(0x7f010012);
    }
    private static SharedPreferences prefs(Activity a) { return a.getSharedPreferences("model-m0",0); }
    private static File model(Activity a) { return new File(a.getFilesDir(),"qwen-q4-k-m.gguf"); }
    private static String describe(JSONObject answer) throws Exception {
        return "Proposta da verificare\nTitolo • "+answer.getString("title")+"\nParola chiave • "+answer.getString("keyword");
    }
    private void restore(Activity a) {
        run.setEnabled(model(a).isFile());
        transcript.setText(prefs(a).getString("text",""));
        status.setText(model(a).isFile()?"Modello locale verificato.":"Seleziona il GGUF ufficiale Qwen2.5-0.5B-Instruct Q4_K_M (469 MiB).");
        if (prefs(a).contains("answer")) {
            try { status.setText(describe(new JSONObject(prefs(a).getString("answer","{}")))); }
            catch(Exception ignored) { status.setText("Rigenerare la proposta salvata."); }
        }
    }
    public static void onResult(Activity a,int request,Intent data) {
        if (request!=11 || data==null || data.getData()==null) return;
        ModelPanel p=panel(a);
        if (p!=null) {
            Uri uri=data.getData();
            p.task(a, () -> {install(a,uri); return "Modello locale pronto.";});
        }
    }
    private void task(Activity a,Callable<String> action) {
        if (!busy.compareAndSet(false,true)) { status.setText("Attendi la fine dell’operazione locale."); return; }
        choose.setEnabled(false); run.setEnabled(false);
        status.setText("Elaborazione locale in corso…");
        jobs.execute(() -> {
            String result;
            boolean acquired=M0Memory.acquire();
            try {
                if (!acquired) throw new IOException("Attendi la conclusione del render o della trascrizione.");
                result=action.call();
            }
            catch (Exception | LinkageError e) { result="Operazione interrotta. "+e.getMessage(); }
            finally { if (acquired) M0Memory.release(); busy.set(false); }
            final String msg=result;
            a.runOnUiThread(() -> { if (!a.isDestroyed()) {choose.setEnabled(true);run.setEnabled(model(a).isFile());status.setText(msg);} });
        });
    }
    private static void install(Activity a,Uri uri) throws Exception {
        File temp=File.createTempFile("model-", ".part",a.getFilesDir());
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try (InputStream input=a.getContentResolver().openInputStream(uri); OutputStream output=new FileOutputStream(temp)) {
                if (input==null) throw new IOException("File non disponibile.");
                byte[] buf=new byte[131072]; long total=0; int n;
                while ((n=input.read(buf))!=-1) {
                    total+=n;
                    if (total>MODEL_BYTES) throw new IOException("GGUF troppo grande per il modello selezionato.");
                    output.write(buf,0,n); digest.update(buf,0,n);
                }
            }
            StringBuilder hex=new StringBuilder();
            for (byte b:digest.digest()) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
            if (!LocalModel.SHA256.equals(hex.toString())) throw new IOException("Hash GGUF errato, verifica nome e quantizzazione.");
            Files.move(temp.toPath(),model(a).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {temp.delete();}
    }
}
