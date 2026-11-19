package it.michetti.studio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, bounded parser for manually corrected local SRT captions. */
final class CaptionSrt {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_CUES = 4000;
    private static final Pattern TIMING = Pattern.compile(
            "([0-9]{2}):([0-9]{2}):([0-9]{2}),([0-9]{3})[ \\t]+-->[ \\t]+"
            + "([0-9]{2}):([0-9]{2}):([0-9]{2}),([0-9]{3})");

    static List<CaptionCue> parse(String content) throws IOException {
        if (content == null || content.length() > MAX_BYTES
                || content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IOException("File SRT assente o oltre 1 MiB.");
        if (content.indexOf('\uFFFD') >= 0)
            throw new IOException("File SRT con caratteri UTF-8 illeggibili.");
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        content = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (content.isEmpty()) throw new IOException("Il file SRT è vuoto.");
        List<CaptionCue> cues = new ArrayList<>();
        long previousEnd = 0;
        for (String block : content.split("\n[ \\t]*\n+", -1)) {
            if (cues.size() == MAX_CUES)
                throw new IOException("Il file SRT supera 4000 cue.");
            String[] lines = block.split("\n", -1);
            if (lines.length < 3 || !lines[0].trim().matches("[0-9]{1,10}"))
                throw new IOException("Blocco SRT incompleto o indice non numerico.");
            try {
                if (Long.parseLong(lines[0].trim()) <= 0)
                    throw new IOException("L'indice del cue deve essere positivo.");
            } catch (NumberFormatException invalid) {
                throw new IOException("Indice del cue fuori intervallo.", invalid);
            }
            Matcher timing = TIMING.matcher(lines[1].trim());
            if (!timing.matches()) throw new IOException("Timecode SRT non valido.");
            long start = milliseconds(timing, 1);
            long end = milliseconds(timing, 5);
            StringBuilder text = new StringBuilder();
            for (int i = 2; i < lines.length; i++) {
                if (text.length() > 0) text.append(' ');
                text.append(lines[i].trim());
                if (text.length() > 4000)
                    throw new IOException("Testo del cue oltre 4000 caratteri.");
            }
            CaptionCue cue = new CaptionCue(text.toString(), start, end, previousEnd);
            cues.add(cue);
            previousEnd = cue.endMs;
        }
        return cues;
    }

    private static long milliseconds(Matcher match, int group) throws IOException {
        int hours = Integer.parseInt(match.group(group));
        int minutes = Integer.parseInt(match.group(group + 1));
        int seconds = Integer.parseInt(match.group(group + 2));
        int millis = Integer.parseInt(match.group(group + 3));
        if (minutes >= 60 || seconds >= 60)
            throw new IOException("Minuti o secondi SRT fuori intervallo.");
        return ((hours * 60L + minutes) * 60 + seconds) * 1000 + millis;
    }
}
