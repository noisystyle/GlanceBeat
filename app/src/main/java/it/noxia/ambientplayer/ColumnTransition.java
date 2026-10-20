package it.noxia.ambientplayer;

/** Finite choreography; each channel receives the current Material motion easing. */
final class ColumnTransition {
    static final long OPEN_MS=720,CLOSE_MS=580;
    record Stages(float player,float columns,float queue) { }
    static Stages at(float time,boolean opening) {
        return opening?new Stages(stage(time,0,.55f),stage(time,.15f,.75f),stage(time,.75f,1))
            :new Stages(stage(time,.35f,1),stage(time,.20f,.85f),stage(time,0,.20f));
    }
    private static float stage(float t,float start,float end){return Math.max(0,Math.min(1,(t-start)/(end-start)));}
    private ColumnTransition() { }
}
