package com.audiostreamplayer;

/**
 * Created on 2016/12/19.
 */


import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.HandlerThread;

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
        while(writtensize < readsize) {
            n = mAudioTrack.write(bufshort, 0+writtensize, readsize-writtensize);
            writtensize += n;
        }
        audioBuffer.writtenframes += writtensize/nChannels;
        //ensure next periodnotification will be called
        while(audioBuffer.writtenframes -audioBuffer.playedperiodflames<periodframe && audioBuffer.getAppState() == AppState.PLAY){
            try {
                this.sleep(nDevicePeriodms / 2);
            }catch(InterruptedException e){
                AudioBuffer.getInstance().setAppState(AppState.AUDIOTRACK_FAILED_THREADSLEEP);
                return;
            }
            readsize = audioBuffer.read(bufbyte,bufSize);
            readsize = readsize /2;
            for(int i = 0;i<readsize;i++){
                bufshort[i] = (short) ((bufbyte[i * 2 + 1] << 8) | (bufbyte[i * 2] & 0xFF));
            }
            writtensize = 0;
            n =0;
            while(writtensize < readsize) {
                n = mAudioTrack.write(bufshort, 0+writtensize, readsize-writtensize);
                writtensize += n;
            }
            audioBuffer.writtenframes += writtensize/nChannels;
        }
    }

    public void onMarkerReached(AudioTrack  mAudioTrack) {
    }

    public AppState initBuffer(byte[] data){
        audioBuffer = AudioBuffer.getInstance();
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
        if(audioBuffer.getifCustomBufSize()){
            int custombufsize = nSampleRate*audioBuffer.getCustomBufferSize()/1000*nBlockAlign;
            if(custombufsize < minBufferSizeInBytes){
                return AppState.AUDIOTRACK_FAILED_INITBUFSIZE;
            }else{
                minBufferSizeInBytes = custombufsize;
            }
        }
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
        int n;

        if(audioBuffer.getifstarted()) {
            mAudioTrack.write(data, 1, datasize);
        } else {
            while (writtensize < datasize && audioBuffer.writtenframes < delayflames) {
                n =  mAudioTrack.write(data, 1 + writtensize, datasize - writtensize);
                audioBuffer.writtenframes += (n / nChannels);
                writtensize += n;
            }
            if (audioBuffer.writtenframes >= delayflames) {
                mAudioTrack.play();
                if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    System.out.println("Audio Playing.");
                    audioBuffer.setifstarted(true);
                }
            }
            while(writtensize < datasize){
                n =  mAudioTrack.write(data, 1 + writtensize, datasize - writtensize);
                audioBuffer.writtenframes += (n / nChannels);
                writtensize += n;
            }
        }
    }

    public String getPlaybackDescription(){
        return "Features:\n"
                + "SamplingRate: " + Integer.toString(nSampleRate) + "  "
                + "Channels: " + Integer.toString(nChannels) + "\n"
                + "BitsPerSample: " + Integer.toString(nBitPerSamples) + "\n"
                + "Audiotrack BufSize(bytes): " + Integer.toString(minBufferSizeInBytes)
                + " (" + Integer.toString(minBufferSizeInBytes/nBlockAlign * 1000 / nSampleRate) + " millisec)";
    }

    public void audioclose(){
        mAudioTrack.stop();
        mAudioTrack.flush();
    }
}
