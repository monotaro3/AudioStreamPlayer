package com.audiostreamplayer;

import android.os.Message;

import android.os.Handler;

/**
 * Created on 2016/12/30.
 */

public abstract class DisplayUpdater extends Handler {

    private long delaymills;
    private long firstdelaymills = 1000;
    private boolean active = false;
    private String port;
    private String text;
    private boolean ifcustombufsize;
    private String cbufsize;

    public DisplayUpdater(long mills){
        delaymills = mills;
    }

    public void setDelaymills(long mills){
        delaymills = mills;
    }

    public void start(){
        init();
        active = true;
        sleep(firstdelaymills);
    }

    public void resume(){
        active = true;
        sleep(delaymills);
    }

    public void stop(){
        active = false;
    }

    @Override
    public void handleMessage(Message msg) {
        updater();  //2.
        if(active)sleep(delaymills);  //3.
    }

    public abstract void init();
    public abstract void updater();
    public void savePort(String p){port = p;}
    public String loadPort(){return port;}
    public void saveText(String t){text = t;}
    public String loadText(){return text;}
    public void saveifcbufsize(boolean c){ifcustombufsize = c;}
    public boolean loadifcbufsize(){return ifcustombufsize;}
    public void savecbufsize(String cbsize){cbufsize = cbsize;}
    public String loadcbufsize(){return cbufsize;}

    //スリープメソッド
    public void sleep(long delayMills) {
        //使用済みメッセージの削除
        removeMessages(0);
        sendMessageDelayed(obtainMessage(0),delayMills);  //4.
    }
}
