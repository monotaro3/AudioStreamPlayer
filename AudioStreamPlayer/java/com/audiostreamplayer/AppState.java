package com.audiostreamplayer;

/**
 * Created on 2016/12/28.
 */

public enum AppState {
    FIRSTEXECUTE,
    ACCEPT,
    INIT,
    PLAY,
    STOP,
    AUDIOTRACK_WAIT,
    AUDIOTRACK_READY,
    AUDIOTRACK_FAILED_INITBUFSIZE,
    AUDIOTRACK_FAILED_INITPERIODFRAME,
    AUDIOTRACK_FAILED_NEWAUDIOTRACK,
    //INIT_INTERRUPTED
}
