package it.michetti.studio;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.*;

public final class Studio extends Activity {
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private volatile Thread renderWorker;
    private volatile boolean cancelRequested;
    private TextView status;
    private EditText caption;
    private Button clip,audio,make,save,saveBundle,openProject,saveProject,stop;
    private SharedPreferences settings;
    private File assets;

    @Override public void onCreate(Bundle old) {
        super.onCreate(old);
        settings=getSharedPreferences("assets",MODE_PRIVATE);
        assets=new File(getFilesDir(),"assets");
        assets.mkdirs();
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density);
        body.setPadding(pad,pad*2,pad,pad);
        body.setBackgroundColor(Color.rgb(10,22,40));
        scroll.addView(body);
        setContentView(scroll);
        TextView title=new TextView(this);
        title.setText("Laboratorio M0");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(230,199,119));
        body.addView(title);
        clip=button(body,"Importa clip video",v->pick(1,"video/*"));
        audio=button(body,"Importa voce WAV o M4A 48 kHz",v->pick(2,"audio/*"));
        caption=new EditText(this);
        caption.setSingleLine();
        caption.setHint("Didascalia manuale (vuota per usare ASR)");
        caption.setId(42);
        body.addView(caption);
        AsrPanel.add(this,body);
        ModelPanel.add(this,body);
        button(body,"Importa SRT corretto",v->{
            if(!available("audio")) {
                status.setText("Importare prima la narrazione da associare ai sottotitoli.");
                return;
            }
            pick(14,"*/*");
        });
        openProject=button(body,"Apri archivio portabile e renderizza",v->pick(15,"*/*"));
        saveProject=button(body,"Salva archivio portabile",v->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip").putExtra(Intent.EXTRA_TITLE,"Progetto-Studio.zip");
            startActivityForResult(i,16);
        });
        make=button(body,"Renderizza MP4 verticale",v->startExport());
        stop=button(body,"Annulla render",v->{cancelRequested=true;Thread w=renderWorker;if(w!=null)w.interrupt();});
        save=button(body,"Salva ultimo MP4",v->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("video/mp4").putExtra(Intent.EXTRA_TITLE,"Studio-M0.mp4");
            startActivityForResult(i,3);
        });
        saveBundle=button(body,"Salva video, SRT e rapporto ZIP",v->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip").putExtra(Intent.EXTRA_TITLE,"Studio-M0-export.zip");
            startActivityForResult(i,4);
        });
        status=new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(17);
        body.addView(status);
        ready();
    }
    private Button button(LinearLayout body,String text,View.OnClickListener action) {
        Button b=new Button(this);
        b.setAllCaps(false); b.setText(text); b.setOnClickListener(action);body.addView(b);return b;
    }
    private File file(String key) {
        String id=settings.getString(key,"");
        return id.isEmpty()?null:new File(assets,id);
    }
    private boolean available(String key) {
        File f=file(key); return f!=null && f.isFile();
    }
    private void ready() {
        clip.setEnabled(true);audio.setEnabled(true);
        make.setEnabled(available("clip") && available("audio"));stop.setEnabled(false);
        save.setEnabled(available("render"));
        saveBundle.setEnabled(available("bundle"));
        openProject.setEnabled(true);
        saveProject.setEnabled(available("clip") && available("audio"));
        status.setText("Clip "+(available("clip")?"importata":"mancante")+" · Voce "+(available("audio")?"importata":"mancante"));
    }
    private void busy(boolean cancellable) {
        clip.setEnabled(false); audio.setEnabled(false); make.setEnabled(false);
        save.setEnabled(false); saveBundle.setEnabled(false); stop.setEnabled(cancellable);
        openProject.setEnabled(false); saveProject.setEnabled(false);
    }
    private void pick(int request,String type) {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type),request);
    }
    private interface Work { String run() throws Exception; }
    private void task(boolean cancellable,Work work) {
        busy(cancellable);
        status.setText(cancellable?"Preparazione render…":"Copia del file…");
        cancelRequested=false;
        jobs.submit(()->{
            String message;
            try {
                if(cancellable){renderWorker=Thread.currentThread();if(cancelRequested)throw new CancellationException("Render annullato.");}
                message=work.run();
            }
            catch(Exception|LinkageError e){message="Operazione interrotta. "+e.getMessage();}
            catch(OutOfMemoryError e){message="Memoria esaurita. Chiudere altre app e riprovare.";}
            finally {if(cancellable)renderWorker=null;}
            final String result=message;
            runOnUiThread(()->{if(!isDestroyed()){ready();status.append("\n"+result);}});
        });
    }
    private static void copy(InputStream in,OutputStream out,MessageDigest hash) throws IOException {
        byte[] buf=new byte[64*1024]; int n;
        while((n=in.read(buf))!=-1) {
            Export.checkCancelled();
            if(hash!=null)hash.update(buf,0,n);
            out.write(buf,0,n);
        }
    }
    private void record(String key,File file) throws IOException {
        if(!settings.edit().putString(key,file.getName()).commit()) throw new IOException("Salvataggio riferimento fallito.");
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        if(request==11){ModelPanel.onResult(this,request,data);return;}
        if(request==12 || request==13){AsrPanel.onResult(this,request,data);return;}
        Uri uri=data.getData();
        if(request==15) {
            task(true,()->renderProject(uri));
            return;
        }
        if(request==16) {
            task(false,()->saveProject(uri));
            return;
        }
        if(request==14) {
            File voice=file("audio");
            if(voice==null || !voice.isFile())return;
            task(false,()->{
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=getContentResolver().openInputStream(uri)) {
                    if(in==null)throw new IOException("SRT non disponibile.");
                    byte[] buffer=new byte[8192];int count;
                    while((count=in.read(buffer))!=-1) {
                        if(bytes.size()+count>1048576)throw new IOException("SRT oltre 1 MiB.");
                        bytes.write(buffer,0,count);
                    }
                }
                java.util.List<CaptionCue> cues=CaptionSrt.parse(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
                JSONArray segments=new JSONArray();
                StringBuilder transcript=new StringBuilder();
                for(CaptionCue cue:cues) {
                    segments.put(new JSONObject().put("text",cue.text).put("startMs",cue.startMs).put("endMs",cue.endMs));
                    if(transcript.length()>0)transcript.append(' ');
                    transcript.append(cue.text);
                }
                JSONObject corrected=new JSONObject().put("segments",segments)
                        .put("audioSha256",voice.getName()).put("source","SRT importato manualmente");
                AtomicFile target=new AtomicFile(new File(getFilesDir(),"asr-cues.json"));
                synchronized(AsrPanel.class) {
                    FileOutputStream out=target.startWrite();
                    try {
                        out.write(corrected.toString().getBytes(StandardCharsets.UTF_8));
                        target.finishWrite(out);
                    } catch(Exception e) {target.failWrite(out);throw e;}
                }
                runOnUiThread(()->{
                    if(!isDestroyed()) {
                        caption.setText("");
                        if(transcript.length()<=2500)ModelPanel.setTranscript(this,transcript.toString());
                    }
                });
                return cues.size()+" cue importati e associati alla narrazione. La durata e la safe area saranno verificate al render.";
            });
            return;
        }
        if(request==3 || request==4){
            task(false,()->{
                try(InputStream in=new FileInputStream(file(request==3?"render":"bundle"));OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                    if(out==null) throw new IOException("Destinazione non disponibile.");
                    copy(in,out,null);
                }
                return request==3?"MP4 salvato.":"Archivio ZIP salvato.";
            });return;
        }
        if(request!=1 && request!=2)return;
        String key=request==1?"clip":"audio";
        task(false,()->{
            File tmp=File.createTempFile("ingest-",".part",assets);
            try {
                MessageDigest hash=MessageDigest.getInstance("SHA-256");
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){
                    if(in==null)throw new IOException("Sorgente non disponibile.");
                    copy(in,out,hash);
                }
                if(tmp.length()==0)throw new IOException("File vuoto.");
                StringBuilder digest=new StringBuilder();
                for(byte b:hash.digest())digest.append(String.format(java.util.Locale.ROOT,"%02x",b));
                File dest=new File(assets,digest.toString());
                if(!dest.isFile() && !tmp.renameTo(dest))throw new IOException("Importazione fallita.");
                record(key,dest);
                return "SHA-256 "+digest;
            } finally {tmp.delete();}
        });
    }
    private String saveProject(Uri destination) throws Exception {
        File staged=File.createTempFile("progetto-",".zip",getCacheDir());
        try {
            File voice=file("audio");
            Subtitles subtitles=Subtitles.read(this,voice,"");
            PortableProject.write(file("clip"),voice,subtitles.srt(),staged);
            try(InputStream in=new FileInputStream(staged);
                OutputStream out=getContentResolver().openOutputStream(destination,"wt")) {
                if(out==null)throw new IOException("Destinazione non disponibile.");
                copy(in,out,null);
            }
            return "Archivio portabile salvato.";
        } finally {staged.delete();}
    }

    private String renderProject(Uri uri) throws Exception {
        File staged=File.createTempFile("importa-progetto-",".zip",getCacheDir());
        File directory=java.nio.file.Files.createTempDirectory(getCacheDir().toPath(),"media-progetto-").toFile();
        try {
            try(InputStream in=getContentResolver().openInputStream(uri);
                OutputStream out=new FileOutputStream(staged)) {
                if(in==null)throw new IOException("Archivio non disponibile.");
                PortableProject.transfer(in,out,PortableProject.MAX_ARCHIVE,null);
            }
            PortableProject project=PortableProject.read(staged,directory);
            staged.delete();
            return render(project.clip,project.voice,Subtitles.fromProject(this,project.srt),project.archiveSha256);
        } finally {
            staged.delete();
            File[] files=directory.listFiles();
            if(files!=null)for(File file:files)file.delete();
            directory.delete();
        }
    }

    private void startExport() {
        String text=caption.getText().toString().trim();
        task(true,()->{
            File voice=file("audio");
            return render(file("clip"),voice,Subtitles.read(this,voice,text),null);
        });
    }

    private String render(File video,File voice,Subtitles subtitles,String projectHash) throws Exception {
        if(!M0Memory.acquire())throw new IOException("Attendere il completamento dell’altro lavoro locale.");
        File temp=null,bundleTemp=null,finalFile=null,finalBundle=null;
        boolean committed=false;
        try {
            temp=File.createTempFile("render-",".part",assets);
            Export.run(video,voice,subtitles,temp,(i,n)->{
                if(i%5==0 || i==n)runOnUiThread(()->status.setText("Fotogramma "+i+" di "+n));
            });
            String exportId=UUID.randomUUID().toString();
            finalFile=new File(assets,exportId+".mp4");
            finalBundle=new File(assets,exportId+".zip");
            bundleTemp=File.createTempFile("bundle-",".part",assets);
            ExportBundle.write(temp,video,voice,subtitles,projectHash,bundleTemp);
            if(!temp.renameTo(finalFile))throw new IOException("Spazio o permessi insufficienti.");
            if(!bundleTemp.renameTo(finalBundle))throw new IOException("Salvataggio archivio fallito.");
            if(!settings.edit().putString("render",finalFile.getName())
                    .putString("bundle",finalBundle.getName()).commit())
                throw new IOException("Salvataggio dei riferimenti export fallito.");
            committed=true;
            return "Render verificato. Salva il video oppure lo ZIP con SRT e rapporto. Revisione umana da completare.";
        } finally {
            if(temp!=null)temp.delete();
            if(bundleTemp!=null)bundleTemp.delete();
            if(!committed) {
                if(finalFile!=null)finalFile.delete();
                if(finalBundle!=null)finalBundle.delete();
            }
            M0Memory.release();
        }
    }
    @Override public void onDestroy(){jobs.shutdownNow();AsrPanel.detach(this);super.onDestroy();}
}
