package it.michetti.studio;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Downmix and integrate PCM sample intervals at the 16 kHz Whisper timebase. */
final class AudioPcm {
    private static final int MAX_SAMPLES = 16000 * 600;
    private short[] output = new short[16000 * 4];
    private int length, filled, rate, channels;
    private long weightedSum;

    private void format(MediaFormat format) throws IOException {
        int nextRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int nextChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (nextRate < 16000 || nextRate > 192000 || nextChannels < 1 || nextChannels > 8
            || (format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                && format.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT))
            throw new IOException("Serve PCM 16 bit, 16–192 kHz e un massimo di otto canali.");
        if (rate != 0 && (rate != nextRate || channels != nextChannels))
            throw new IOException("Formato audio variabile durante la traccia.");
        rate = nextRate;
        channels = nextChannels;
    }

    private void append(int sample) throws IOException {
        if (length >= MAX_SAMPLES) throw new IOException("Durata massima ASR di 10 minuti.");
        if (length == output.length)
            output = Arrays.copyOf(output, Math.min(MAX_SAMPLES, 2 * output.length));
        output[length++] = (short)sample;
    }

    private void consume(ByteBuffer data) throws IOException {
        data.order(ByteOrder.LITTLE_ENDIAN);
        if (channels == 0 || data.remaining() % (channels * 2) != 0)
            throw new IOException("Buffer PCM disallineato.");
        while (data.hasRemaining()) {
            int summed = 0;
            for (int c=0; c<channels; ++c) summed += data.getShort();
            int mono = summed / channels;
            int left = 16000;
            while (left > 0) {
                int weight = Math.min(rate - filled, left);
                weightedSum += (long)weight * mono;
                filled += weight;
                left -= weight;
                if (filled == rate) {
                    append((int)(weightedSum / rate));
                    weightedSum = 0;
                    filled = 0;
                }
            }
        }
    }

    static void check() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Operazione annullata.");
    }

    static short[] decode(File source) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        AudioPcm pcm = new AudioPcm();
        try {
            extractor.setDataSource(source.getPath());
            MediaFormat format = null;
            for (int i=0; i<extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                if (f.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    extractor.selectTrack(i);
                    format = f;
                    break;
                }
            }
            if (format == null) throw new IOException("Nessuna traccia audio nel file.");
            if (format.containsKey(MediaFormat.KEY_DURATION)
                && format.getLong(MediaFormat.KEY_DURATION) > 600000000L)
                throw new IOException("Durata massima ASR di 10 minuti.");
            String mime = format.getString(MediaFormat.KEY_MIME);
            if ("audio/raw".equals(mime)) {
                pcm.format(format);
                int capacity = format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                    ? format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 256 * 1024;
                if (capacity <= 0 || capacity > 8 * 1024 * 1024)
                    throw new IOException("Dimensione pacchetto PCM fuori intervallo.");
                ByteBuffer packet = ByteBuffer.allocateDirect(capacity);
                do {
                    check();
                    packet.clear();
                    int size = extractor.readSampleData(packet, 0);
                    if (size < 0) break;
                    packet.position(0).limit(size);
                    pcm.consume(packet);
                } while (extractor.advance());
            } else {
                decoder = MediaCodec.createDecoderByType(mime);
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                decoder.configure(format, null, null, 0);
                decoder.start();
                boolean inputDone = false, outputDone = false;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                long lastProgress = System.nanoTime();
                while (!outputDone) {
                    check();
                    int input = inputDone ? -1 : decoder.dequeueInputBuffer(10000);
                    if (input >= 0) {
                        ByteBuffer buf = decoder.getInputBuffer(input);
                        buf.clear();
                        int n = extractor.readSampleData(buf, 0);
                        if (n < 0) {
                            decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(input, 0, n, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                        lastProgress = System.nanoTime();
                    }
                    int index = decoder.dequeueOutputBuffer(info, 10000);
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) pcm.format(decoder.getOutputFormat());
                    if (index >= 0) {
                        if (info.size > 0) {
                            if (pcm.rate == 0) pcm.format(decoder.getOutputFormat(index));
                            ByteBuffer buf = decoder.getOutputBuffer(index);
                            buf.position(info.offset).limit(info.offset + info.size);
                            pcm.consume(buf);
                        }
                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        decoder.releaseOutputBuffer(index, false);
                        lastProgress = System.nanoTime();
                    }
                    if (System.nanoTime() - lastProgress > 30000000000L)
                        throw new IOException("Decoder audio senza avanzamento per 30 secondi.");
                }
            }
            if (pcm.filled > 0) pcm.append((int)(pcm.weightedSum / pcm.filled));
            if (pcm.length == 0) throw new IOException("Nessun campione PCM decodificato.");
            return Arrays.copyOf(pcm.output, pcm.length);
        } finally {
            if (decoder != null) decoder.release();
            extractor.release();
        }
    }
}
