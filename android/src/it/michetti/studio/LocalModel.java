package it.michetti.studio;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class LocalModel {
    static final String SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db";
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*");
    private static native byte[] generate(byte[] path, byte[] prompt, byte[] grammar);
    private static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    private static String choices(Set<String> options) {
        List<String> terms = new ArrayList<>();
        for (String option : options) {
            // GBNF literal containing the exact JSON string, quotes included.
            String payload = JSONObject.quote(option);
            terms.add("\"" + payload.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return String.join(" | ", terms);
    }

    static JSONObject infer(Context context, File model, String text) throws Exception {
        if (text.isEmpty() || text.length() > 2500) throw new IOException("Usa un segmento da 1 a 2500 caratteri.");
        Matcher scan = WORD.matcher(text);
        List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        Set<String> words = new LinkedHashSet<>(), titles = new LinkedHashSet<>();
        while (scan.find()) {
            starts.add(scan.start()); ends.add(scan.end()); words.add(scan.group());
        }
        if (starts.isEmpty() || starts.size() > 240) throw new IOException("Usa da 1 a 240 parole.");
        for (int i = 0; i < starts.size(); i++) {
            for (int n = Math.min(2, starts.size()); n <= 5 && i+n <= starts.size(); n++) {
                String phrase = text.substring(starts.get(i), ends.get(i+n-1));
                if (phrase.length() <= 55 && !Pattern.compile("[\n\r.!?]").matcher(phrase).find()) titles.add(phrase);
            }
        }
        if (titles.isEmpty()) titles.addAll(words);
        String grammar = "root ::= \"{\\\"keyword\\\":\" word \",\\\"title\\\":\" title \"}\"\n"
                       + "word ::= " + choices(words) + "\ntitle ::= " + choices(titles) + "\n";
        String system;
        try (InputStream input = context.getAssets().open("llm-prompt.it.txt");
             Scanner reader = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            system = reader.next().trim();
        }
        String data = new JSONObject().put("trascrizione", text).toString().replace("<", "\\u003c").replace(">", "\\u003e");
        String prompt = "<|im_start|>system\n" + system + "<|im_end|>\n<|im_start|>user\n" + data
                      + "<|im_end|>\n<|im_start|>assistant\n";
        System.loadLibrary("studio_llm");
        String raw = new String(generate(utf8(model.getAbsolutePath()),utf8(prompt),utf8(grammar)),StandardCharsets.UTF_8);
        JSONObject answer = new JSONObject(raw);
        if (answer.length()!=2 || !words.contains(answer.getString("keyword")) || !titles.contains(answer.getString("title")))
            throw new IOException("Output estrattivo non valido.");
        return answer.put("model", "Qwen2.5-0.5B-Instruct-Q4_K_M").put("model_sha256", SHA256)
                .put("engine","llama.cpp b6000").put("status","PROPOSED").put("role","Video Editor");
    }
}
