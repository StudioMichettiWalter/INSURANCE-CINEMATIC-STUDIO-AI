package it.michetti.studio;

import java.io.IOException;

public final class CaptionCueTest {
    interface Check { void run() throws IOException; }
    private static void rejects(Check check) throws IOException {
        try { check.run(); } catch(IOException expected) { return; }
        throw new AssertionError("Input non valido accettato");
    }
    public static void main(String[] args) throws Exception {
        CaptionCue cue=new CaptionCue("  Leggi  il contratto.  ",1000,2280,0);
        String expected="1\n00:00:01,000 --> 00:00:02,280\nLeggi il contratto.\n\n";
        if(!cue.srt(1).equals(expected))throw new AssertionError(cue.srt(1));
        cue.requireFits(3287);
        rejects(()->cue.requireFits(2000));
        rejects(()->new CaptionCue("",0,100,0));
        rejects(()->new CaptionCue("testo",50,100,75));
        rejects(()->new CaptionCue("testo",100,100,0));
        rejects(()->new CaptionCue("testo",0,600001,0));
        rejects(()->new CaptionCue("testo",-1,100,0));
        System.out.println("PASS 8 verifiche cue e conservazione dei timestamp");
    }
}
