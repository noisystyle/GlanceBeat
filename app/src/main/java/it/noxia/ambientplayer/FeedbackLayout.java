package it.noxia.ambientplayer;

/** Left edge in the player's logical units; hide unsupported controls and center the rest. */
final class FeedbackLayout {
    static int left(boolean like,boolean dislike,boolean shuffle,int button){
        boolean[] visible={like,dislike,shuffle};int count=0,index=0;
        for(int i=0;i<3;i++){if(visible[i]){count++;if(i<button)index++;}}
        return (count<2?0:count==2?-48+96*index:-72+72*index)-24;
    }
    private FeedbackLayout() { }
}
