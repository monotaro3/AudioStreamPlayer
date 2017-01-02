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

    //スリープメソッド
    public void sleep(long delayMills) {
        //使用済みメッセージの削除
        removeMessages(0);
        sendMessageDelayed(obtainMessage(0),delayMills);  //4.
    }
}
