package it.noxia.ambientplayer;

final class ShuffleMode {
    static Boolean selected(int mode){return mode==0?Boolean.FALSE:mode==1 || mode==2?Boolean.TRUE:null;}
    private ShuffleMode() { }
}
