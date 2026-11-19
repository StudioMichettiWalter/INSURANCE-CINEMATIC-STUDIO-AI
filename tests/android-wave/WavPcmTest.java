package it.michetti.studio;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.Arrays;

public final class WavPcmTest {
    private static int checks;
    private static byte[] concatenate(byte[]... parts) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (byte[] part : parts) stream.write(part);
        return stream.toByteArray();
    }
    private static byte[] chunk(String name, byte[] data) throws IOException {
        return concatenate(name.getBytes("US-ASCII"),
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array(),
                data, new byte[data.length & 1]);
    }
    private static byte[] format(int channels, int rate) {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short)1).putShort((short)channels).putInt(rate)
                .putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16).array();
    }
    private static byte[] wave(byte[]... chunks) throws IOException {
        byte[] body = concatenate(chunks);
        return concatenate("RIFF".getBytes("US-ASCII"),
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.length+4).array(),
                "WAVE".getBytes("US-ASCII"),body);
    }
    private static WavPcm read(byte[] bytes) throws IOException {
        Path path = Files.createTempFile("wave-regression-", ".wav");
        try {
            Files.write(path, bytes);
            return WavPcm.read(path.toFile());
        } finally { Files.delete(path); }
    }
    private static void reject(String label, byte[] bytes) throws IOException {
        try { read(bytes); }
        catch (IOException expected) {
            System.out.println("PASS " + label + " — " + expected.getMessage());
            checks++;
            return;
        }
        throw new AssertionError("Accettato WAV non valido: " + label);
    }
    private static void expect(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        System.out.println("PASS "+label); checks++;
    }
    public static void main(String[] arguments) throws Exception {
        byte[] fmt = chunk("fmt ",format(1,16000));
        byte[] data = chunk("data",new byte[160]);
        byte[] valid = wave(fmt,data);
        WavPcm parsed = read(valid);
        expect(parsed.frames==80 && parsed.rate==16000 && parsed.channels==1 &&
                parsed.bytes==160 && parsed.offset==44,"PCM16 mono 16 kHz");
        parsed = read(wave(chunk("fmt ",format(2,48000)),chunk("data",new byte[400])));
        expect(parsed.frames==100 && parsed.channels==2 && parsed.rate==48000,"stereo 48 kHz");
        expect(read(wave(data,fmt)).frames==80,"ordine chunk data prima di fmt");
        expect(read(wave(fmt,chunk("JUNK",new byte[]{1}),data)).offset==54,"chunk estraneo dispari e padding");
        expect(read(wave(chunk("fmt ",concatenate(format(1,16000),new byte[2])),data)).frames==80,"PCM con estensione vuota");
        reject("RIFF troncato",Arrays.copyOf(valid,valid.length-1));
        reject("RIFF con byte estranei finali",concatenate(valid,new byte[1]));
        byte[] bad=valid.clone();bad[4]=3;
        reject("dimensione RIFF troppo piccola",bad);
        reject("fmt duplicato",wave(fmt,fmt,data));
        reject("data duplicato",wave(fmt,data,data));
        reject("fmt assente",wave(data));
        reject("data assente",wave(fmt));
        reject("data vuoto",wave(fmt,chunk("data",new byte[0])));
        reject("frame stereo incompleto",wave(chunk("fmt ",format(2,48000)),chunk("data",new byte[2])));
        bad=format(1,16000);bad[12]=4;
        reject("block align errato",wave(chunk("fmt ",bad),data));
        bad=format(1,16000);bad[8]=1;
        reject("byte rate errato",wave(chunk("fmt ",bad),data));
        reject("frequenza non supportata",wave(chunk("fmt ",format(1,44100)),data));
        bad=format(1,16000);bad[14]=24;
        reject("bit depth non supportata",wave(chunk("fmt ",bad),data));
        reject("fmt 17 byte",wave(chunk("fmt ",concatenate(format(1,16000),new byte[1])),data));
        reject("estensione fmt incoerente",wave(chunk("fmt ",concatenate(format(1,16000),new byte[]{1,0})),data));
        byte[] head=concatenate("data".getBytes("US-ASCII"),new byte[]{-1,-1,-1,-1});
        reject("dimensione unsigned oltre file",wave(fmt,head));
        reject("padding dispari assente",wave(fmt,concatenate("JUNK".getBytes("US-ASCII"),new byte[]{1,0,0,0,1})));
        reject("intestazione chunk incompleta",wave(fmt,data,new byte[3]));
        reject("durata oltre dieci minuti",wave(fmt,chunk("data",new byte[16000*2*600+2])));
        System.out.println("PASS totale "+checks);
    }
}
