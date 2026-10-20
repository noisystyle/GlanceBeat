package it.noxia.ambientplayer;

/** One track per release, with a tactile detent and hysteresis around the commit threshold. */
final class CoverDrag {
    record State(float displacement,int armed,boolean detent) { }
    private int armed;
    State update(float distance,float spacing,boolean previous,boolean next) {
        if(spacing<=0)return new State(0,0,false);
        int direction=distance>0?1:distance<0?-1:0;
        boolean allowed=direction>0?previous:direction<0 && next;
        int before=armed;
        if(!allowed || direction!=armed && armed!=0)armed=0;
        if(allowed) {
            if(Math.abs(distance)>=spacing*.40f)armed=direction;
            else if(Math.abs(distance)<spacing*.30f)armed=0;
        }
        float magnitude=Math.abs(distance),travel;
        if(!allowed)travel=spacing*.09f*(float)Math.tanh(magnitude/(spacing*.12f));
        else travel=magnitude<=spacing?magnitude:spacing+spacing*.16f*(float)Math.tanh((magnitude-spacing)/(spacing*.30f));
        return new State(direction*travel,armed,armed!=0 && armed!=before);
    }
    int armed(){return armed;}
    void reset(){armed=0;}
}
