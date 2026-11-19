package it.michetti.studio;

import android.media.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** WAV PCM16 mono/stereo a 48 kHz o 16 kHz. Interpolazione lineare per la prova M0. */
final class Wav {
    static boolean isWave(File f) throws IOException {
        try(RandomAccessFile in=new RandomAccessFile(f,"r")) {
            return in.length()>=12 && tag(in).equals("RIFF") && skipTag(in).equals("WAVE");
        }
    }
    private static String tag(RandomAccessFile in) throws IOException {
        byte[] b=new byte[4]; in.readFully(b);return new String(b,StandardCharsets.US_ASCII);
    }
    private static String skipTag(RandomAccessFile in) throws IOException { in.skipBytes(4); return tag(in); }
    private static int u16(RandomAccessFile in) throws IOException {return Short.reverseBytes(in.readShort())&65535;}
    private static long u32(RandomAccessFile in) throws IOException {return Integer.reverseBytes(in.readInt())&0xffffffffL;}
    static long encode(File src,File dst) throws Exception {
        try(RandomAccessFile in=new RandomAccessFile(src,"r")) {
            in.seek(12);int channels=0,rate=0;long start=-1,size=0;
            while(in.getFilePointer()+8<=in.length()) {
                String id=tag(in);long len=u32(in);long p=in.getFilePointer();
                if(len>in.length()-p)throw new IOException("Chunk WAV troncato.");
                if(id.equals("fmt ")) {
                    if(len<16 || u16(in)!=1)throw new IOException("Richiesto PCM16 WAV.");
                    channels=u16(in);rate=(int)u32(in);in.skipBytes(6);
                    if(u16(in)!=16 || (rate!=16000 && rate!=48000) || (channels!=1 && channels!=2))
                        throw new IOException("Richiesto PCM16 mono/stereo a 16 o 48 kHz.");
                } else if(id.equals("data")){start=p;size=len;}
                in.seek(p+len+(len&1));
            }
            if(start<0 || size<=0 || channels==0 || size%(channels*2)!=0)throw new IOException("WAV senza PCM valido.");
            long samples=size/(2*channels);
            if(samples>(long)rate*600)throw new IOException("Lo spike supporta fino a 10 minuti.");
            try(FileInputStream raw=new FileInputStream(src)){
                raw.getChannel().position(start);
                try(DataInputStream data=new DataInputStream(new BufferedInputStream(raw))) {
                    encodePcm(data,samples,channels,rate,dst);
                    return (samples*1000000L+rate/2)/rate;
                }
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
