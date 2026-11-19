package it.michetti.studio;

import java.util.concurrent.atomic.AtomicBoolean;

final class M0Memory {
    private static final AtomicBoolean busy=new AtomicBoolean();
    static boolean acquire(){return busy.compareAndSet(false,true);}
    static void release(){busy.set(false);}
}
