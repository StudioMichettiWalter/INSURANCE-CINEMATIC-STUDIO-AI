package it.michetti.studio;

import java.io.IOException;
import java.util.List;

public final class CaptionSrtTest {
    private static int checks;
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Risultato inatteso");
        checks++;
    }
    private static void rejects(String content) throws IOException {
        try { CaptionSrt.parse(content); }
        catch (IOException expected) { checks++; return; }
        throw new AssertionError("SRT non valido accettato");
    }
    private static String cue(int index, String interval, String text) {
        return index + "\n" + interval + "\n" + text + "\n\n";
    }
    public static void main(String[] args) throws Exception {
        String interval = "00:00:01,000 --> 00:00:02,280";
        String valid = cue(1, interval, "Leggi il contratto.");
        List<CaptionCue> list = CaptionSrt.parse("\uFEFF" + valid.replace("\n", "\r\n"));
        require(list.size() == 1);
        require(list.get(0).startMs == 1000 && list.get(0).endMs == 2280);
        require(list.get(0).text.equals("Leggi il contratto."));
        require(list.get(0).srt(1).equals(valid));
        require(CaptionSrt.parse(cue(2, interval, "Due\nrighe.")).get(0).text.equals("Due righe."));
        require(CaptionSrt.parse(valid + cue(2, "00:00:02,280 --> 00:00:03,000", "Segue.")).size() == 2);
        rejects(cue(1, interval, "Testo \uFFFD"));
        rejects("");
        rejects(null);
        rejects("1\n" + interval + "\n\n");
        rejects("indice\n" + interval + "\nTesto");
        rejects("0\n" + interval + "\nTesto");
        rejects(cue(1, "00:60:01,000 --> 00:61:02,280", "Testo"));
        rejects(cue(1, "00:00:60,000 --> 00:01:02,280", "Testo"));
        rejects(cue(1, "00:00:01.000 --> 00:00:02.280", "Testo"));
        rejects(cue(1, "00:00:01,000 --> 00:00:01,000", "Testo"));
        rejects(cue(1, "00:00:02,000 --> 00:00:01,000", "Testo"));
        rejects(cue(1, "00:09:59,000 --> 00:10:00,001", "Testo"));
        rejects(valid + cue(2, "00:00:02,000 --> 00:00:03,000", "Sovrapposto"));
        rejects(cue(1, interval, "x".repeat(4001)));
        rejects("é".repeat(600000));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 4001; i++) {
            int start = i * 2;
            String timestamp = String.format(java.util.Locale.ROOT,
                    "00:00:%02d,%03d --> 00:00:%02d,%03d", start / 1000, start % 1000,
                    (start + 1) / 1000, (start + 1) % 1000);
            many.append(cue(i + 1, timestamp, "Testo"));
        }
        rejects(many.toString());
        System.out.println("PASS " + checks + " verifiche SRT. Host Java, nessuna esecuzione Android.");
    }
}
