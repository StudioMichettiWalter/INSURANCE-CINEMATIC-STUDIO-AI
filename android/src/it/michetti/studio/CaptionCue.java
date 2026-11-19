package it.michetti.studio;

import java.io.IOException;
import java.util.Locale;

/** A cue keeps the original ASR interval until a person supplies a corrected interval. */
final class CaptionCue {
    final String text;
    final long startMs;
    final long endMs;

    CaptionCue(String text, long startMs, long endMs, long previousEndMs) throws IOException {
        this.text = text.trim().replaceAll("\\s+", " ");
        if (this.text.isEmpty() || this.text.length() > 4000)
            throw new IOException("Testo del cue vuoto o troppo lungo.");
        if (startMs < previousEndMs || endMs <= startMs || endMs > 600000)
            throw new IOException("Intervallo del cue fuori ordine o oltre 10 minuti.");
        this.startMs = startMs;
        this.endMs = endMs;
    }

    void requireFits(long audioDurationMs) throws IOException {
        if (endMs > audioDurationMs)
            throw new IOException("Un cue supera la durata della voce. Correggere i tempi prima del render.");
    }

    String srt(int index) {
        return index + "\n" + time(startMs) + " --> " + time(endMs) + "\n" + text + "\n\n";
    }

    private static String time(long ms) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
                ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000);
    }
}
