package it.michetti.studio;

import android.app.Activity;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

final class Subtitles {
    private static final int MAX_WIDTH=888, BASELINE=1660;
    private static final class Cue {
        String[] lines;
        long start,end;
        Cue(List<String> words,long a,long b){lines=words.toArray(new String[0]);start=a;end=b;}
    }
    private final List<Cue> cues=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String keyword="";
    private Subtitles(Activity a){
        paint.setTypeface(Typeface.createFromAsset(a.getAssets(),"Inter.ttf"));
        paint.setTextSize(50);
    }
    static Subtitles read(Activity a,File audio,String manual) throws Exception {
        Subtitles sub=new Subtitles(a);
        if(!manual.isEmpty()){
            sub.add(manual,0,Integer.MAX_VALUE);return sub;
        }
        File file=new File(a.getFilesDir(),"asr-cues.json");
        if(!file.exists())return sub;
        if(file.length()>4_000_000)throw new IOException("Cue ASR troppo grandi.");
        byte[] bytes=new byte[(int)file.length()];
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(bytes);}
        JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!audio.getName().equals(json.getString("audioSha256")))
            throw new IOException("La narrazione è cambiata. Ripetere la trascrizione della voce importata.");
        JSONArray segments=json.getJSONArray("segments");
        StringBuilder transcript=new StringBuilder();
        long prev=0;
        for(int i=0;i<segments.length();i++){
            JSONObject cue=segments.getJSONObject(i);
            String t=cue.getString("text").trim();
            long start=cue.getLong("startMs"),end=cue.getLong("endMs");
            if(start<prev || end<=start || end>600000)throw new IOException("Timestamp ASR incoerenti.");
            prev=end;
            sub.add(t,start,end);
            if(transcript.length()>0)transcript.append(' ');transcript.append(t);
        }
        sub.keyword=ModelPanel.keyword(a,transcript.toString());
        if(sub.keyword==null)sub.keyword="";
        return sub;
    }
    private void add(String text,long start,long end) throws IOException {
        if(text.trim().isEmpty())return;
        ArrayList<String> lines=new ArrayList<>();
        String line="";
        for(String word:text.trim().split("\\s+")){
            if(paint.measureText(word)>MAX_WIDTH)throw new IOException("Parola oltre la safe area.");
            String next=line.isEmpty()?word:line+" "+word;
            if(paint.measureText(next)>MAX_WIDTH){lines.add(line);line=word;}
            else line=next;
        }
        if(!line.isEmpty())lines.add(line);
        int pages=(lines.size()+1)/2;
        if(end==Integer.MAX_VALUE && pages>1)throw new IOException("Didascalia manuale oltre due righe.");
        for(int i=0;i<pages;i++){
            long a=start+(end-start)*i/pages,b=start+(end-start)*(i+1)/pages;
            cues.add(new Cue(lines.subList(i*2,Math.min(i*2+2,lines.size())),a,b));
        }
    }
    void draw(Canvas canvas,long ms){
        for(Cue cue:cues){
            if(cue.start>ms)break;
            if(ms>=cue.end)continue;
            int y=BASELINE-(cue.lines.length-1)*64;
            for(String line:cue.lines){
                float width=paint.measureText(line),x=(1080-width)/2;
                paint.setColor(Color.argb(220,10,22,40));
                canvas.drawRoundRect(x-18,y-56,x+width+18,y+15,12,12,paint);
                for(String word:line.split(" ")){
                    String bare=word.replaceAll("^[\\p{Punct}«»“”]+|[\\p{Punct}«»“”]+$","");
                    paint.setColor(!keyword.isEmpty() && bare.equalsIgnoreCase(keyword)?Color.rgb(230,199,119):Color.WHITE);
                    canvas.drawText(word,x,y,paint);x+=paint.measureText(word+" ");
                }
                y+=64;
            }
            return;
        }
    }
}
