package it.michetti.studio;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.*;

final class Verify {
    static void mp4(File mp4,int expectedFrames,long wantedUs) throws IOException {
        MediaExtractor scanner=new MediaExtractor();
        try {
            scanner.setDataSource(mp4.getPath());
            if(scanner.getTrackCount()!=2)throw new IOException("QA: numero tracce inatteso.");
            boolean sawAudio=false,sawVideo=false;
            for(int i=0;i<scanner.getTrackCount();i++){
                MediaFormat f=scanner.getTrackFormat(i);
                String mime=f.getString(MediaFormat.KEY_MIME);
                boolean video="video/avc".equals(mime);
                if(video) {
                    if(f.getInteger(MediaFormat.KEY_WIDTH)!=Export.W || f.getInteger(MediaFormat.KEY_HEIGHT)!=Export.H)
                        throw new IOException("QA: dimensioni inattese.");
                    sawVideo=true;
                } else {
                    if(!"audio/mp4a-latm".equals(mime) || f.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=48000)
                        throw new IOException("QA: formato audio inatteso.");
                    sawAudio=true;
                }
                scanner.selectTrack(i);
                scanner.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                int count=0;long last=-1;
                do {
                    Export.checkCancelled();
                    long ts=scanner.getSampleTime();
                    if(ts<0)break;
                    if(ts<=last || (video && Math.abs(ts-count*1000000L/Export.FPS)>100))
                        throw new IOException("QA: timestamp inatteso al campione "+count);
                    last=ts;count++;
                }while(scanner.advance());
                if(count==0 || (video && count!=expectedFrames))throw new IOException("QA: campioni mancanti.");
                long tolerance=video?33434:55000; // un frame video, oppure due pacchetti AAC e arrotondamento.
                if(!f.containsKey(MediaFormat.KEY_DURATION) || Math.abs(f.getLong(MediaFormat.KEY_DURATION)-wantedUs)>tolerance)
                    throw new IOException("QA: durata fuori tolleranza.");
                scanner.unselectTrack(i);
            }
            if(!sawAudio || !sawVideo)throw new IOException("QA: ingresso audio o video assente.");
        }finally{scanner.release();}
    }
}
