package it.michetti.studio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class PortableProjectTest {
    public static void main(String[] args) throws Exception {
        File clip=new File(args[0]), voice=new File(args[1]), srt=new File(args[2]);
        File fromWindows=new File(args[3]), fromJava=new File(args[4]);
        File directory=Files.createTempDirectory(fromJava.getParentFile().toPath(),"media-").toFile();

        PortableProject project=PortableProject.read(fromWindows,directory);
        checkEqual(clip,project.clip);
        checkEqual(voice,project.voice);
        checkEqual(srt,project.srt);
        if(!PortableProject.sha256(fromWindows).equals(project.archiveSha256))
            throw new AssertionError("Hash ZIP");
        PortableProject.write(project.clip,project.voice,project.srt,fromJava);
        reject(new File(args[5]),"alterato");
        reject(new File(args[6]),"sconosciuta");
        if(new File(fromJava.getParentFile(),"escaped.txt").exists())throw new AssertionError("Scrittura fuori cartella");
        System.out.println("PASS Java legge lo ZIP PowerShell, riscrive i media e rifiuta hash alterati e percorsi estranei");
    }

    private static void reject(File archive, String reason) throws IOException {
        File scratch=Files.createTempDirectory(archive.getParentFile().toPath(),"rifiuta-").toFile();
        try {
            PortableProject.read(archive,scratch);
            throw new AssertionError("ZIP non valido accettato");
        } catch(IOException expected) {
            if(!expected.getMessage().contains(reason))throw expected;
        }
    }

    private static void checkEqual(File expected, File actual) throws IOException {
        if(!PortableProject.sha256(expected).equals(PortableProject.sha256(actual)))
            throw new AssertionError(actual.getName());
    }

    private static void checkEqual(File expected, String actual) throws IOException {
        if(!actual.equals(new String(Files.readAllBytes(expected.toPath()),StandardCharsets.UTF_8)))
            throw new AssertionError(expected.getName());
    }
}
