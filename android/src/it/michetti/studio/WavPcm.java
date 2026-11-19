package it.michetti.studio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Parser RIFF/WAVE PCM16 condivisibile con il collaudo JVM, senza API Android. */
final class WavPcm {
    final int channels;
    final int rate;
    final long offset;
    final long bytes;
    final long frames;

    private WavPcm(int channels, int rate, long offset, long bytes) {
        this.channels = channels;
        this.rate = rate;
        this.offset = offset;
        this.bytes = bytes;
        this.frames = bytes / (channels * 2);
    }

    static boolean isWave(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (input.length() < 12) return false;
            String riff = tag(input);
            input.skipBytes(4);
            return riff.equals("RIFF") && tag(input).equals("WAVE");
        }
    }

    private static String tag(RandomAccessFile input) throws IOException {
        byte[] bytes = new byte[4];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static int u16(RandomAccessFile input) throws IOException {
        return Short.reverseBytes(input.readShort()) & 65535;
    }

    private static long u32(RandomAccessFile input) throws IOException {
        return Integer.reverseBytes(input.readInt()) & 0xffffffffL;
    }

    static WavPcm read(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long fileBytes = input.length();
            if (fileBytes < 12 || !tag(input).equals("RIFF"))
                throw new IOException("Intestazione RIFF WAV mancante.");
            long riffBytes = u32(input);
            if (!tag(input).equals("WAVE") || riffBytes < 4 || riffBytes != fileBytes - 8)
                throw new IOException("Lunghezza RIFF incoerente o WAV troncato.");
            int channels = 0, rate = 0;
            boolean formatSeen = false, dataSeen = false;
            long offset = -1, pcmBytes = 0;
            while (input.getFilePointer() < fileBytes) {
                if (fileBytes - input.getFilePointer() < 8)
                    throw new IOException("Intestazione chunk WAV troncata.");
                String id = tag(input);
                long length = u32(input);
                long payload = input.getFilePointer();
                // Sottrazione prima della somma per evitare overflow dei limiti.
                long remaining = fileBytes - payload;
                if (length > remaining || (length & 1) > remaining - length)
                    throw new IOException("Chunk WAV o padding troncato.");
                if (id.equals("fmt ")) {
                    if (formatSeen) throw new IOException("Chunk formato WAV duplicato.");
                    formatSeen = true;
                    if (length < 16 || length == 17 || u16(input) != 1)
                        throw new IOException("Richiesto PCM16 WAV.");
                    channels = u16(input);
                    long sourceRate = u32(input);
                    long byteRate = u32(input);
                    int alignment = u16(input);
                    int bits = u16(input);
                    if ((channels != 1 && channels != 2) ||
                            (sourceRate != 16000 && sourceRate != 48000) || bits != 16)
                        throw new IOException("Richiesto PCM16 mono/stereo a 16 o 48 kHz.");
                    rate = (int) sourceRate;
                    if (alignment != channels * 2 || byteRate != sourceRate * alignment)
                        throw new IOException("Allineamento o byte rate WAV incoerente.");
                    if (length >= 18 && u16(input) != length - 18)
                        throw new IOException("Estensione formato WAV incoerente.");
                } else if (id.equals("data")) {
                    if (dataSeen) throw new IOException("Chunk audio WAV duplicato.");
                    dataSeen = true;
                    offset = payload;
                    pcmBytes = length;
                }
                input.seek(payload + length + (length & 1));
            }
            if (!formatSeen || !dataSeen || pcmBytes == 0 || pcmBytes % (channels * 2) != 0)
                throw new IOException("WAV senza frame PCM completi.");
            WavPcm parsed = new WavPcm(channels, rate, offset, pcmBytes);
            if (parsed.frames > (long) rate * 600)
                throw new IOException("Lo spike supporta fino a 10 minuti.");
            return parsed;
        }
    }
}
