package com.audiostreamplayer;

/**
 * Created on 2016/12/19.
 */
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class AudioStreamReceiver {

    private int mPort;
    private final Handler mHandler;
    private static Handler ASPHandler = null;
    private static boolean bDone = false;
    private AppState AudioTrackReady;
    private ServerSocket serverSocket;

    private Runnable mAcceptTask = new Runnable() {
        public void run() {
            AudioTrackReady = AppState.AUDIOTRACK_WAIT;

            try {
                serverSocket = new ServerSocket(mPort);
            } catch (final IOException e) {
                mHandler.post(new Runnable() {
                    public void run() {
                        ToastText("ServerSocket creation failed.");
                        AudioBuffer.getInstance().setAppState(AppState.STOP);
                        setDisplayUpdater(true);
                    }
                });
                throw new RuntimeException(e);
            }

            final Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (final IOException e) {
                if(AudioBuffer.getInstance().getAppState() == AppState.STOP){
                    mHandler.post(new Runnable() {
                        public void run() {
                            ToastText("ServerSocket accept terminated.");
                        }
                    });
                }else{
                    mHandler.post(new Runnable() {
                        public void run() {
                            ToastText("ServerSocket accept failed.");
                        }
                    });
                }
                mHandler.post(new Runnable() {
                    public void run() {
                        AudioBuffer.getInstance().setAppState(AppState.STOP);
                        setDisplayUpdater(true);
                    }
                });
                throw new RuntimeException(e);
            } finally {
                try {
                    serverSocket.close();
                } catch (final IOException e) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            ToastText("ServerSocket close failed.");
                            AudioBuffer.getInstance().setAppState(AppState.STOP);
                            setDisplayUpdater(true);
                        }
                    });
                    throw new RuntimeException(e);
                }
            }

            if(AudioBuffer.getInstance().getAppState() == AppState.ACCEPT) {
                AudioBuffer.getInstance().setAppState(AppState.INIT);
                mHandler.post(new Runnable() {
                    public void run() {
                        setDisplayUpdater(true);
                    }
                });
                bDone = false;
                try {
                    final InputStream inputStream = socket.getInputStream();
                    final OutputStream outputStream = socket.getOutputStream();
                    AudioStreamReceiver(inputStream, outputStream);
                } catch (final IOException e) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            ToastText("SocketConnection closed unexpectedly.");
                            AudioBuffer.getInstance().setAppState(AppState.STOP);
                            setDisplayUpdater(true);
                        }
                    });
                    throw new RuntimeException(e);
                } finally {
                    if(AudioTrackReady == AppState.AUDIOTRACK_READY){
                        Message msg = Message.obtain();
                        msg.what = 2;
                        ASPHandler.sendMessage(msg);
                    }
                    try {
                        socket.close();
                    } catch (final IOException e) {
                        mHandler.post(new Runnable() {
                            public void run() {
                                ToastText("Socketclose failed.");
                                AudioBuffer.getInstance().setAppState(AppState.STOP);
                                setDisplayUpdater(true);
                            }
                        });
                        throw new RuntimeException(e);
                    }
                }
                mHandler.post(new Runnable() {
                    public void run() {
                        ToastText("Playback finished.");
                        AudioBuffer.getInstance().setAppState(AppState.STOP);
                        setDisplayUpdater(true);
                    }
                });
            }else{
                try {
                    socket.close();
                } catch (final IOException e) {
                    mHandler.post(new Runnable() {
                        public void run() {
                            ToastText("Socketclose failed.");
                        }
                    });
                    throw new RuntimeException(e);
                }finally {
                    mHandler.post(new Runnable() {
                        public void run() {
                            AudioBuffer.getInstance().setAppState(AppState.STOP);
                            setDisplayUpdater(true);
                        }
                    });
                }
            }
        }
    };

    public AudioStreamReceiver(int port) {
        mPort = port;
        mHandler = new Handler();
    }

    public void setPort(int p){mPort = p;}

    public void closeServerSocket(){
        try {
            serverSocket.close();
        }catch (IOException e){
            ToastText("ServerSocket close failed.");
        }
    }

    public void setASPHandler(Handler h){
        ASPHandler = h;
    }

    public static void setbDone(){
        bDone = true;
    }

    public void setAudioTrackReady(AppState flag){
        AudioTrackReady = flag;
    }

    public void start()
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(mAcceptTask);
    }

    protected abstract void ToastText(String data);
    protected abstract void setDisplayUpdater(boolean flag);

    private void AudioStreamReceiver(final InputStream inputStream, final OutputStream outputStream) throws IOException {

        int recvdata = 0;
        int n = 0;

        //send ready
        byte[] ready = "Ready".getBytes();
        outputStream.write(ready, 0, 5);
        outputStream.flush();

        //receive information of initialization
        recvdata=0;
        byte[] initstream = new byte[16];
        while (true) {
            n = inputStream.read(initstream, recvdata, initstream.length);
            if (n == -1) return;
            recvdata += n;
            if (recvdata == initstream.length) break;
        }

        ByteBuffer bIS = ByteBuffer.wrap(initstream);
        bIS.order(ByteOrder.LITTLE_ENDIAN);
        int nSampleRate = bIS.getInt();
        int nChannels = bIS.getShort();
        int nBitPerSamples = bIS.getShort();
        int devperiod = bIS.getInt();
        int packetsize = bIS.getInt();

        if(nBitPerSamples != 16){
            mHandler.post(new Runnable() {
                public void run() {
                    ToastText("This app supports only 16bit audio stream. Process will be aborted.");
                }
            });
            throw new IOException();
        }

        int leastsize = nSampleRate * devperiod / 1000 * nChannels;
        short[] receivedbuffer = new short[leastsize*2];
        int receivedsize = 0;

        byte[] data = new byte[packetsize];

        Message msg = Message.obtain();
        msg.obj = initstream;
        msg.what = 0;
        ASPHandler.sendMessage(msg);

        synchronized (this) {
            if (AudioTrackReady == AppState.AUDIOTRACK_WAIT) {
                try {
                    this.wait(); //wait until AudioTrack is ready
                } catch (java.lang.InterruptedException e) {
                }
            }
        }

        if(AudioTrackReady != AppState.AUDIOTRACK_READY){
            switch (AudioTrackReady) {
                case AUDIOTRACK_FAILED_INITPERIODFRAME:
                    mHandler.post(new Runnable() {
                    public void run() {
                        ToastText("minBuffersize is smaller than deviceperiod of audiostream source.");
                    }
                });
                    break;
                case AUDIOTRACK_FAILED_NEWAUDIOTRACK:
                    mHandler.post(new Runnable() {
                        public void run() {
                            ToastText("Audiotrack constraction failed.");
                        }
                    });
                    break;
            }
            throw new IOException();
        }

        //send ready
        outputStream.write(ready, 0, 5);
        outputStream.flush();

        //Audiotrack playback start
        AudioBuffer.getInstance().setAppState(AppState.PLAY);
        mHandler.post(new Runnable() {
            public void run() {
                setDisplayUpdater(true);
            }
        });
        //long duration = System.currentTimeMillis();
        int datasize=0;


        byte[] _data = new byte[packetsize];

        /*int recordsize = 1;
        for(int j=0;j<22;j++){
            recordsize = recordsize * 2;
        }
        int recszmask = recordsize -1;
        byte[] record = new byte[recordsize];
        int bufstart = 0;*/

        AudioBuffer audiobuffer = AudioBuffer.getInstance();
        /*long transactiontime = 0;
        long maxtransanctiontime =0;
        short[] durationrecord = new short[1024];
        short count = 0;
        short countmask = 1023;*/

        while(!bDone){
            //transactiontime = System.currentTimeMillis();
            recvdata=0;
            while (true) {
                n = inputStream.read(data, recvdata, packetsize-recvdata);
                if (n == -1 || n == 0) {
                    bDone = true;
                    break;
                }
                recvdata += n;
                if (recvdata == packetsize) break;
            }

            /*transactiontime = System.currentTimeMillis() - transactiontime;
            durationrecord[count] = (short)transactiontime;
            count++;
            count = (short)(count & countmask);
            if(transactiontime > maxtransanctiontime)maxtransanctiontime = transactiontime;*/

            datasize = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();

            if(audiobuffer.getifstarted()){
                System.arraycopy(data,4,_data,0,datasize);
                audiobuffer.write(_data,datasize);
            }else {

            /*if(datasize==999999){
                bDone = true;
                continue;
            }
            if(bufstart+datasize>recordsize){
                System.arraycopy(data,4,record,bufstart,recordsize-bufstart);
                System.arraycopy(data,4+recordsize-bufstart,record,0,datasize-(recordsize-bufstart));
                bufstart = (bufstart + datasize) & recszmask;
            }else{
                System.arraycopy(data,4,record,bufstart,datasize);
                bufstart = bufstart +datasize;
            }
*/
                datasize = datasize /2;

                for (int i = 0; i < datasize; i++) {
                    receivedbuffer[1 + receivedsize + i] = (short) ((data[4 + i * 2 + 1] << 8) | (data[4 + i * 2] & 0xFF));
                }
                receivedsize += datasize;
                if(receivedsize >= leastsize) {
                    receivedbuffer[0] = (short) receivedsize;
                    msg = Message.obtain();
                    msg.obj = receivedbuffer;
                    msg.what = 1;
                    ASPHandler.sendMessage(msg);
                    receivedsize = 0;
                }
            }
        }

        /*
        //debug
        int recpacket = 4096;
        for(int i = 0;i < recordsize/recpacket;i++) {
            if (bufstart + recpacket > recordsize) {
                outputStream.write(record, bufstart, recordsize - bufstart);
                outputStream.write(record, 0, recpacket - (recordsize - bufstart));
                outputStream.flush();
                bufstart = (bufstart + recpacket) & recszmask;
            } else {
                outputStream.write(record, bufstart, recpacket);
                outputStream.flush();
                bufstart = bufstart + recpacket;
            }
        }
        */

        //duration = System.currentTimeMillis() - duration;
        msg = Message.obtain();
        msg.what = 2;
        ASPHandler.sendMessage(msg);
    }
}