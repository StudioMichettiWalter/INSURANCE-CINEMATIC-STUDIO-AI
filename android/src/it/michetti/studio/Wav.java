package it.michetti.studio;

import android.media.*;
import java.io.*;
import java.nio.*;

/** WAV PCM16 mono/stereo a 48 kHz o 16 kHz. Interpolazione lineare per la prova M0. */
final class Wav {
    static boolean isWave(File file) throws IOException {
        return WavPcm.isWave(file);
    }
    static long encode(File src,File dst) throws Exception {
        WavPcm pcm = WavPcm.read(src);
        try(FileInputStream raw=new FileInputStream(src)) {
            raw.getChannel().position(pcm.offset);
            try(DataInputStream data=new DataInputStream(new BufferedInputStream(raw))) {
                encodePcm(data,pcm.frames,pcm.channels,pcm.rate,dst);
                return (pcm.frames*1000000L+pcm.rate/2)/pcm.rate;
            }
        }
    }
    private static void readFrame(DataInputStream input,int[] sample) throws IOException {
        for(int i=0;i<sample.length;i++)sample[i]=Short.reverseBytes(input.readShort());
    }
    private static void encodePcm(DataInputStream input,long samples,int channels,int rate,File dest) throws Exception {
        int factor=48000/rate;long total=samples*factor, consumed=0;
        int[] previous=new int[channels],next=new int[channels];
        readFrame(input,previous);
        if(samples>1)readFrame(input,next);else System.arraycopy(previous,0,next,0,channels);
        MediaFormat f=MediaFormat.createAudioFormat("audio/mp4a-latm",48000,channels);
        f.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        f.setInteger(MediaFormat.KEY_BIT_RATE,128000);
        MediaCodec codec=MediaCodec.createEncoderByType("audio/mp4a-latm");
        MediaMuxer mux=null;
        try {
            codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();
            mux=new MediaMuxer(dest.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            boolean sent=false,complete=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();int track=-1;
            long lastWork=System.nanoTime();
            while(!complete){
                Export.checkCancelled();
                int i=sent?-1:codec.dequeueInputBuffer(10000);
                if(i>=0){
                    long pts=consumed*1000000/48000;
                    if(consumed==total){
                        codec.queueInputBuffer(i,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);sent=true;
                    } else{
                        ByteBuffer buffer=codec.getInputBuffer(i).order(ByteOrder.LITTLE_ENDIAN);buffer.clear();
                        int frames=(int)Math.min(total-consumed,buffer.remaining()/(channels*2));
                        if(frames==0)throw new IOException("Buffer AAC insufficiente.");
                        for(int j=0;j<frames;j++){
                            int phase=(int)(consumed%factor);
                            for(int c=0;c<channels;c++)
                                buffer.putShort((short)((previous[c]*(factor-phase)+next[c]*phase)/factor));
                            consumed++;
                            if(consumed%factor==0){
                                System.arraycopy(next,0,previous,0,channels);
                                if(consumed/factor+1<samples)readFrame(input,next);
                            }
                        }
                        codec.queueInputBuffer(i,0,buffer.position(),pts,0);
                    }
                    lastWork=System.nanoTime();
                }
                int out=codec.dequeueOutputBuffer(info,10000);
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=mux.addTrack(codec.getOutputFormat());mux.start();}
                else if(out>=0){
                    if(info.size>0 && (info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0)
                        mux.writeSampleData(track,codec.getOutputBuffer(out),info);
                    complete=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(out,false);lastWork=System.nanoTime();
                }
                if(System.nanoTime()-lastWork>30_000_000_000L)throw new IOException("Encoder AAC bloccato.");
            }
            mux.stop();
        } finally {codec.release();if(mux!=null)mux.release();}
    }
}
