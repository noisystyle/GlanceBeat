package it.noxia.ambientplayer;

/** Window geometry follows available space, including rotation and a folded cover display. */
record PlayerColumns(boolean split,int playerLeft,int playerWidth,int queueLeft,int queueWidth) {
    static PlayerColumns resolve(int width,float density,int smallestWidthDp,boolean reversed) {
        boolean split=smallestWidthDp>=600 && width/Math.max(1,density)>=600;
        if(!split)return new PlayerColumns(false,0,width,0,width);
        int leftWidth=width/2,rightWidth=width-leftWidth;
        return reversed?new PlayerColumns(true,0,leftWidth,leftWidth,rightWidth)
            :new PlayerColumns(true,leftWidth,rightWidth,0,leftWidth);
    }
}
