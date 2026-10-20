package it.noxia.ambientplayer;

/** Optional local accessory values. Unknown values use -1. */
record AccessoryBattery(int left, int right, int caseLevel) {
    boolean known() { return left >= 0 || right >= 0 || caseLevel >= 0; }
}
