package com.audiostreamplayer;

/**
 * Created on 2016/12/19.
 */

public class AudioBuffer {
    private static AudioBuffer audiobuffer = new AudioBuffer();
    private byte[] buffer;
    private int bufsize = 1;
    private int bufStart = 0;
    private int datasize = 0;
    private int mask;
    private boolean ifstarted = false;
    private AppState state = AppState.STOP;


    public long readcount = 0;
    public long readframes = 0;
    public long frameposition = 0;
    public long playedperiodflames = 0;
    public int periodflames = 0;
    public int nBlockAlign;
    public long writtenframes = 0;

    private AudioBuffer(){
    }
    public void init(int bufsize_exp2){
        bufsize = 1;
        for(int i = 0;i<bufsize_exp2;i++){
            bufsize = bufsize * 2;
        }
        buffer = new byte[bufsize];
        mask = bufsize -1;
        flush();
    }
    public static AudioBuffer getInstance(){
        return audiobuffer;
    }
    public synchronized void write(byte[] src, int bytes){
        if(bytes >= bufsize){
            System.arraycopy(src, 0, buffer, bytes-bufsize, bufsize);
            bufStart = 0;
            datasize = bufsize;
        }else {
            int tmpStart = (bufStart + datasize) & mask;
            if (tmpStart+ bytes > bufsize) {
                System.arraycopy(src, 0, buffer, tmpStart, bufsize - tmpStart);
                System.arraycopy(src, bufsize - tmpStart, buffer, 0, bytes - (bufsize - tmpStart));
                if (datasize + bytes > bufsize) {
                    bufStart = (tmpStart + bytes) & mask;
                    datasize = bufsize;
                } else {
                    datasize += bytes;
                }
            } else {
                System.arraycopy(src, 0, buffer, tmpStart, bytes);
                datasize += bytes;
            }
        }
    }
    public synchronized int read(byte[] dst, int dstsize){
        readcount++;
        int readbytes;
        if(dstsize < datasize)readbytes = dstsize;
        else readbytes = datasize;
        if (bufStart + readbytes > bufsize){
            System.arraycopy(buffer,bufStart,dst,0,bufsize - bufStart);
            System.arraycopy(buffer,0,dst,bufsize - bufStart,readbytes -(bufsize - bufStart));
            bufStart = (bufStart + readbytes) & mask;
            datasize -= readbytes;
        }else{
            System.arraycopy(buffer,bufStart,dst,0,readbytes);
            bufStart += readbytes;
            datasize -= readbytes;
        }

        readframes += readbytes /nBlockAlign;

        return readbytes;
    }

    public boolean getifstarted(){
        return ifstarted;
    }
    public void setifstarted(boolean flag){
        ifstarted = flag;
    }
    public void setAppState(AppState s){state = s;}
    public AppState getAppState(){return state;}
    public void flush(){
        bufStart = 0;
        datasize = 0;
        ifstarted = false;
        readcount = 0;
        readframes = 0;
        frameposition = 0;
        playedperiodflames = 0;
        //periodflames = 0;
        //nBlockAlign;
        writtenframes = 0;
    }
}
