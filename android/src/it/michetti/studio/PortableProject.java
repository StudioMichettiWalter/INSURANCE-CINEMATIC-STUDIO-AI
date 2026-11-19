package it.michetti.studio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** ZIP of the media and timed captions for one render. Paths in a ZIP are never used as output paths. */
final class PortableProject {
    private static final long MIB = 1024L * 1024;
    static final long MAX_ARCHIVE = 1024 * MIB;
    private static final String MANIFEST = "studio-render.txt";
    private static final String MAGIC = "INSURANCE-STUDIO-RENDER/1";
    private static final String[] NAMES = {"clip.mp4", "voce.audio", "captions.srt"};
    private static final long[] LIMITS = {768 * MIB, 128 * MIB, MIB};

    final File clip;
    final File voice;
    final String srt;
    final String archiveSha256;

    private PortableProject(File clip, File voice, String srt, String archiveSha256) {
        this.clip = clip;
        this.voice = voice;
        this.srt = srt;
        this.archiveSha256 = archiveSha256;
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(64);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    static String sha256(File file) throws IOException {
        MessageDigest hash = digest();
        try (InputStream in = new FileInputStream(file)) { transfer(in, null, Long.MAX_VALUE, hash); }
        return hex(hash.digest());
    }

    static long transfer(InputStream source, OutputStream target, long limit, MessageDigest hash)
            throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while ((count = source.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Operazione annullata.");
            total += count;
            if (total > limit) throw new IOException("File oltre il limite dell'archivio portabile.");
            if (hash != null) hash.update(buffer, 0, count);
            if (target != null) target.write(buffer, 0, count);
        }
        if (total == 0) throw new IOException("File vuoto nell'archivio portabile.");
        return total;
    }

    private static void checkEntry(ZipEntry entry, long limit) throws IOException {
        if (entry == null || entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > limit)
            throw new IOException("File mancante, vuoto o troppo grande nell'archivio portabile.");
    }

    static PortableProject read(File file, File emptyDirectory) throws IOException {
        if (file.length() <= 0 || file.length() > MAX_ARCHIVE)
            throw new IOException("L'archivio ZIP deve essere compreso tra 1 byte e 1 GiB.");
        File[] contents = emptyDirectory.listFiles();
        if (contents == null || contents.length != 0)
            throw new IOException("Serve una cartella di lavoro vuota.");
        try (ZipFile zip = new ZipFile(file)) {
            Set<String> allowed = new HashSet<>();
            allowed.add(MANIFEST);
            for (String name : NAMES) allowed.add(name);
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                if (!allowed.remove(entries.nextElement().getName()))
                    throw new IOException("Voce ZIP sconosciuta o duplicata.");
            }
            if (!allowed.isEmpty()) throw new IOException("Archivio portabile incompleto.");

            ZipEntry manifest = zip.getEntry(MANIFEST);
            checkEntry(manifest, 512);
            String[] lines;
            try (InputStream in = zip.getInputStream(manifest);
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                transfer(in, bytes, 512, null);
                lines = new String(bytes.toByteArray(), StandardCharsets.US_ASCII).split("\\r?\\n", -1);
            }
            if (lines.length != 5 || !MAGIC.equals(lines[0]) || !lines[4].isEmpty())
                throw new IOException("Versione o formato del manifest portabile non supportato.");
            String srt = null;
            for (int i = 0; i < NAMES.length; i++) {
                String name = NAMES[i];
                String prefix = name + "=";
                if (!lines[i + 1].startsWith(prefix) || !lines[i + 1].substring(prefix.length()).matches("[0-9a-f]{64}"))
                    throw new IOException("Hash non valido nel manifest portabile.");
                ZipEntry entry = zip.getEntry(name);
                checkEntry(entry, LIMITS[i]);
                MessageDigest hash = digest();
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = i == 2 ? new ByteArrayOutputStream() : new FileOutputStream(new File(emptyDirectory, name))) {
                    long size = transfer(in, out, LIMITS[i], hash);
                    if (size != entry.getSize() || !lines[i + 1].equals(prefix + hex(hash.digest())))
                        throw new IOException("File alterato nell'archivio: " + name);
                    if (i == 2) {
                        srt = new String(((ByteArrayOutputStream)out).toByteArray(), StandardCharsets.UTF_8);
                        CaptionSrt.parse(srt);
                    }
                }
            }
            return new PortableProject(new File(emptyDirectory, NAMES[0]), new File(emptyDirectory, NAMES[1]),
                    srt, sha256(file));
        }
    }

    static void write(File clip, File voice, String srt, File destination) throws IOException {
        CaptionSrt.parse(srt);
        byte[] captions = srt.getBytes(StandardCharsets.UTF_8);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destination)))) {
            StringBuilder manifest = new StringBuilder(MAGIC).append('\n');
            File[] media = {clip, voice};
            for (int i = 0; i < NAMES.length; i++) {
                MessageDigest hash = digest();
                zip.putNextEntry(new ZipEntry(NAMES[i]));
                if (i < media.length) {
                    if (media[i].length() > LIMITS[i]) throw new IOException("Media oltre il limite portabile.");
                    try (InputStream in = new FileInputStream(media[i])) {
                        transfer(in, zip, LIMITS[i], hash);
                    }
                } else {
                    hash.update(captions);
                    zip.write(captions);
                }
                zip.closeEntry();
                manifest.append(NAMES[i]).append('=').append(hex(hash.digest())).append('\n');
            }
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(manifest.toString().getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
    }
}
