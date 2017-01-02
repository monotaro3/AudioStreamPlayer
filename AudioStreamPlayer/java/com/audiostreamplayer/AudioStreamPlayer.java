package com.audiostreamplayer;

/**
 * Created on 2016/12/19.
 */


import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.HandlerThread;
import android.os.Looper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class AudioStreamPlayer extends HandlerThread implements AudioTrack.OnPlaybackPositionUpdateListener{

    private int nSampleRate;
    private int nChannels;
    private int nBitPerSamples;
    private int nDevicePeriodms;
    private AudioTrack mAudioTrack;
    private int delayflames;
    private byte[] bufbyte;
    private int bufSize;
    private short[] bufshort;
    private int readsize = 0;
    private int Encoding;
    private int Channels;
    private int minBufferSizeInBytes;
    private int periodframe;
    private AudioBuffer audioBuffer;
    private int nBlockAlign;

    public AudioStreamPlayer(){
        super("AudioStreamPlayer");
    }

    public void onPeriodicNotification(AudioTrack mAudioTrack) {
        audioBuffer.frameposition = mAudioTrack.getPlaybackHeadPosition();
        audioBuffer.playedperiodflames += periodframe;
        readsize = audioBuffer.read(bufbyte,bufSize);
        readsize = readsize /2;
        for(int i = 0;i<readsize;i++){
            bufshort[i] = (short) ((bufbyte[i * 2 + 1] << 8) | (bufbyte[i * 2] & 0xFF));
        }
        int writtensize = 0;
        int n =0;
        //while(writtensize < readsize) {
            n = mAudioTrack.write(bufshort, 0+writtensize, readsize-writtensize);
            writtensize += n;
        //}
        audioBuffer.writtenframes += writtensize/nChannels;
        //ensure next periodnotification will be called
        while(audioBuffer.writtenframes -audioBuffer.playedperiodflames<periodframe){
            try {
                this.sleep(nDevicePeriodms / 2);
            }catch(InterruptedException e){

            }
            readsize = audioBuffer.read(bufbyte,bufSize);
            readsize = readsize /2;
            for(int i = 0;i<readsize;i++){
                bufshort[i] = (short) ((bufbyte[i * 2 + 1] << 8) | (bufbyte[i * 2] & 0xFF));
            }
            writtensize = 0;
            n =0;
            //while(writtensize < readsize) {
                n = mAudioTrack.write(bufshort, 0+writtensize, readsize-writtensize);
                writtensize += n;
            //}
            audioBuffer.writtenframes += writtensize/nChannels;
        }
    }

    public void onMarkerReached(AudioTrack  mAudioTrack) {
    }

    public AppState initBuffer(byte[] data){
        byte[] initstream = new byte[16];
        System.arraycopy(data,0,initstream,0,16);
        ByteBuffer bIS = ByteBuffer.wrap(initstream);
        bIS.order(ByteOrder.LITTLE_ENDIAN);
        int SampleRate = bIS.getInt();
        int channels = bIS.getShort();
        int BitPerSamples = bIS.getShort();
        int devPeriod = bIS.getInt();
        nSampleRate = SampleRate;
        nChannels = channels;
        nBitPerSamples = BitPerSamples;
        nDevicePeriodms = devPeriod;
        nBlockAlign = nBitPerSamples / 8 * nChannels;
        if(nBitPerSamples == 16)Encoding = AudioFormat.ENCODING_PCM_16BIT;
        if(nChannels == 2)Channels = AudioFormat.CHANNEL_OUT_STEREO;
        minBufferSizeInBytes = AudioTrack.getMinBufferSize(
                nSampleRate,
                Channels,
                Encoding);
        delayflames = minBufferSizeInBytes/(nBitPerSamples/8*nChannels);
        periodframe = ((int)((minBufferSizeInBytes / nBlockAlign)/3 / (nSampleRate * nDevicePeriodms / 1000)))*(nSampleRate * nDevicePeriodms / 1000);
        if(periodframe==0){
            if(minBufferSizeInBytes / nBlockAlign<nSampleRate * nDevicePeriodms / 1000){
                return AppState.AUDIOTRACK_FAILED_INITPERIODFRAME;
            }
            periodframe = nSampleRate * nDevicePeriodms / 1000;
        }
        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                nSampleRate,
                Channels,
                Encoding,
                minBufferSizeInBytes,
                AudioTrack.MODE_STREAM);

        if(mAudioTrack == null)return AppState.AUDIOTRACK_FAILED_NEWAUDIOTRACK;

        mAudioTrack.setPlaybackPositionUpdateListener(this);
        mAudioTrack.setPositionNotificationPeriod(periodframe);
        audioBuffer = AudioBuffer.getInstance();
        int exp2 = 0;
        bufSize = 1;
        while(bufSize<minBufferSizeInBytes){
            bufSize = bufSize * 2;
            exp2++;
        }
        bufbyte = new byte[bufSize];
        bufshort = new short[bufSize/2];
        audioBuffer.init(exp2);
        audioBuffer.periodflames = periodframe;
        audioBuffer.nBlockAlign = nBlockAlign;

        return AppState.AUDIOTRACK_READY;
    }

    public synchronized void writeBuffer(short[] data){
        int datasize = data[0];
        int writtensize = 0;

        if(audioBuffer.getifstarted()) {
            mAudioTrack.write(data, 1, datasize);
        } else{
                writtensize = mAudioTrack.write(data, 1, datasize);
             audioBuffer.writtenframes += (writtensize/nChannels);
            if (audioBuffer.writtenframes >= delayflames) {
                mAudioTrack.play();
                if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    System.out.println("Audio Playing.");
                    audioBuffer.setifstarted(true);
                }
            }
            if(writtensize < datasize){
                audioBuffer.writtenframes += mAudioTrack.write(data, 1+writtensize, datasize-writtensize)/nChannels;
            }
        }
    }

    public void audioclose(){
        mAudioTrack.stop();
        mAudioTrack.flush();
    }
}
