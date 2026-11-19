package it.michetti.studio;

import android.graphics.*;
import android.media.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;

final class Export {
    static { System.loadLibrary("studio"); }
    static native int render(Bitmap target, int frame, int count);
    static native int yuv(Bitmap target, ByteBuffer y,int ys,int yp,ByteBuffer u,int us,int up,ByteBuffer v,int vs,int vp);
    interface Progress { void update(int index,int count); }
    static final int W=1080,H=1920,FPS=30;

    static int audioTrack(MediaExtractor extractor) throws IOException {
        for(int i=0;i<extractor.getTrackCount();i++) {
            MediaFormat f=extractor.getTrackFormat(i);
            if ("audio/mp4a-latm".equals(f.getString(MediaFormat.KEY_MIME)) &&
                f.getInteger(MediaFormat.KEY_SAMPLE_RATE)==48000) return i;
        }
        throw new IOException("Importare una narrazione M4A AAC a 48 kHz.");
    }
    static void checkCancelled() {
        if(Thread.currentThread().isInterrupted()) throw new CancellationException("Esportazione annullata.");
    }
    static void run(File video, File audio, Subtitles captions, File result, Progress progress) throws Exception {
        MediaMetadataRetriever reader=new MediaMetadataRetriever();
        MediaExtractor narration=new MediaExtractor();
        MediaCodec codec=null;
        MediaMuxer mux=null;
        Bitmap master=null,scene=null;
        File encodedAudio=null;
        try {
            reader.setDataSource(video.getPath());
            long pcmDuration=-1;
            if(Wav.isWave(audio)){
                encodedAudio=File.createTempFile("voce-",".m4a",result.getParentFile());
                pcmDuration=Wav.encode(audio,encodedAudio);
            }
            narration.setDataSource(encodedAudio==null ? audio.getPath() : encodedAudio.getPath());
            int track=audioTrack(narration);
            narration.selectTrack(track);
            MediaFormat sound=narration.getTrackFormat(track);
            long duration=pcmDuration>0 ? pcmDuration : sound.getLong(MediaFormat.KEY_DURATION);
            if(duration<=0 || duration>600_000_000L) throw new IOException("Durata audio supportata nello spike: fino a 10 minuti.");
            captions.validateDuration((duration+999)/1000);
            int total=(int)((duration*FPS+999999)/1000000);
            String videoLength=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if(videoLength==null || Long.parseLong(videoLength)*1000+33333<duration)
                throw new IOException("La clip deve coprire tutta la narrazione.");
            MediaFormat avc=MediaFormat.createVideoFormat("video/avc",W,H);
            avc.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            avc.setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709);
            avc.setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED);
            avc.setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
            avc.setInteger(MediaFormat.KEY_BIT_RATE,8000000);
            avc.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
            avc.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            String encoder=new MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(avc);
            if(encoder==null) throw new IOException("Manca un encoder AVC 1080×1920 YUV420 compatibile.");
            codec=MediaCodec.createByCodecName(encoder);
            codec.configure(avc,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            mux=new MediaMuxer(result.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int soundOut=mux.addTrack(sound), videoOut=-1, frame=0;
            boolean inputEnded=false,outputEnded=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            master=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
            scene=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(master);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            long lastProgress=System.nanoTime();
            while(!outputEnded) {
                checkCancelled();
                int slot=inputEnded ? -1 : codec.dequeueInputBuffer(10000);
                if(slot>=0) {
                    long pts=frame*1000000L/FPS;
                    if(frame==total) {
                        codec.queueInputBuffer(slot,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputEnded=true;
                    } else {
                        compose(reader,scene,canvas,paint,frame,total,captions);
                        try(Image target=codec.getInputImage(slot)) {
                            if(target==null) throw new IOException("Encoder privo di input Image.");
                            Image.Plane[] p=target.getPlanes();
                            if(yuv(master,p[0].getBuffer().slice(),p[0].getRowStride(),p[0].getPixelStride(),
                                    p[1].getBuffer().slice(),p[1].getRowStride(),p[1].getPixelStride(),
                                    p[2].getBuffer().slice(),p[2].getRowStride(),p[2].getPixelStride())!=0)
                                throw new IOException("Layout YUV incompatibile.");
                        }
                        codec.queueInputBuffer(slot,0,W*H*3/2,pts,0);
                        progress.update(++frame,total);
                    }
                    lastProgress=System.nanoTime();
                }
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoOut=mux.addTrack(codec.getOutputFormat());
                    mux.start();
                } else if(index>=0) {
                    if(info.size>0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) {
                        if(videoOut<0) throw new IOException("Formato video mancante.");
                        mux.writeSampleData(videoOut,codec.getOutputBuffer(index),info);
                    }
                    outputEnded=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(index,false);
                    lastProgress=System.nanoTime();
                }
                if(System.nanoTime()-lastProgress>30_000_000_000L) throw new IOException("Encoder senza avanzamento da 30 secondi.");
            }
            ByteBuffer sample=ByteBuffer.allocateDirect(1024*1024);
            long first=narration.getSampleTime();
            if(first<0) throw new IOException("Narrazione vuota.");
            do {
                checkCancelled();
                sample.clear();
                int size=narration.readSampleData(sample,0);
                if(size<=0) throw new IOException("Campione audio illeggibile.");
                info.set(0,size,narration.getSampleTime()-first,MediaCodec.BUFFER_FLAG_KEY_FRAME);
                mux.writeSampleData(soundOut,sample,info);
            } while(narration.advance());
            mux.stop();
            mux.release();mux=null;
            Verify.mp4(result,total,duration);
        } finally {
            if(codec!=null) codec.release();
            if(mux!=null) mux.release();
            narration.release();
            reader.release();
            if(master!=null) master.recycle();
            if(scene!=null) scene.recycle();
            if(encodedAudio!=null) encodedAudio.delete();
        }
    }

    static void compose(MediaMetadataRetriever reader, Bitmap scene, Canvas canvas, Paint paint,
                        int frame,int total,Subtitles captions) throws IOException {
        Bitmap clip=reader.getScaledFrameAtTime(frame*1000000L/FPS,MediaMetadataRetriever.OPTION_CLOSEST,W,H);
        if(clip==null) throw new IOException("Fotogramma illeggibile: "+frame);
        canvas.drawColor(Color.rgb(10,22,40));
        float scale=Math.min(W/(float)clip.getWidth(),H/(float)clip.getHeight());
        float x=(W-clip.getWidth()*scale)/2, y=(H-clip.getHeight()*scale)/2;
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(clip,null,new RectF(x,y,W-x,H-y),paint);
        clip.recycle();
        if(render(scene,frame,total)!=0) throw new IOException("Errore renderer condiviso al frame "+frame);
        canvas.drawBitmap(scene,null,new Rect(0,0,W,H),paint);
        captions.draw(canvas,frame*1000L/FPS);
    }
}
