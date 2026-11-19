package it.michetti.studio;

import java.io.IOException;

final class LocalWhisper {
    static { System.loadLibrary("studio_asr"); }
    static native byte[] transcribe(String model, short[] pcm, int threads, int ticket) throws IOException;
    static native void cancel(int ticket);
    private LocalWhisper() {}
}
