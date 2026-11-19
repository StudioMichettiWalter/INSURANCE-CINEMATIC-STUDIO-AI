package it.michetti.studio;

import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A diagnostic export travels with the actual video and timed captions. */
final class ExportBundle {
    private static String sha256(File file) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536];
            int count;
            while((count=in.read(buffer))!=-1) {
                Export.checkCancelled();
                hash.update(buffer,0,count);
            }
        }
        StringBuilder result=new StringBuilder();
        for(byte value:hash.digest())result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        return result.toString();
    }

    private static void text(ZipOutputStream zip,String name,String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static void write(File video,File sourceClip,File voice,Subtitles subtitles,File destination) throws Exception {
        JSONObject report=new JSONObject();
        report.put("schema_version",1).put("status","QA").put("platform","Android");
        report.put("manufacturer",Build.MANUFACTURER).put("device",Build.MODEL);
        report.put("sdk",Build.VERSION.SDK_INT).put("app_version","0.1.0-m0.3");
        report.put("width",Export.W).put("height",Export.H).put("fps",Export.FPS);
        report.put("master_sha256",sha256(video));
        report.put("clip_sha256",sha256(sourceClip)).put("voice_sha256",sha256(voice));
        report.put("automatic_check","Verify.mp4 completato su questo dispositivo");
        report.put("captions_source",subtitles.origin);
        report.put("human_review","NON ESEGUITO");
        report.put("audio_loudness_true_peak","NON MISURATO");
        report.put("cross_platform_parity","NON ESEGUITO");
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destination)))) {
            text(zip,"qa.json",report.toString(2));
            text(zip,"captions.srt",subtitles.srt());
            zip.putNextEntry(new ZipEntry("master.mp4"));
            try(InputStream in=new FileInputStream(video)) {
                byte[] buffer=new byte[65536];int count;
                while((count=in.read(buffer))!=-1) {
                    Export.checkCancelled();
                    zip.write(buffer,0,count);
                }
            }
            zip.closeEntry();
        }
    }
}
