package com.audiostreamplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
    TextView textview1;
    TextView textView2;
    EditText editText;
    EditText etbufferSize;
    CheckBox bcustombufsize;
    Button button;
    Button button2;
    DisplayUpdater displayUpdater;
    DisplayUpdater _displayUpdater;
    int defaultPort = 9876;
    SharedPreferences sharedPref;

    private AudioStreamReceiver mAudioStreamReceiver;
    private AudioStreamPlayer ASPThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textview1 = (TextView)findViewById(R.id.text_view2);
        textView2 = (TextView)findViewById(R.id.text_view4);
        editText = (EditText)findViewById(R.id.edit_text);
        button = (Button)findViewById(R.id.button);
        button2 = (Button)findViewById(R.id.button2);
        etbufferSize = (EditText)findViewById(R.id.edit_text2);
        bcustombufsize = (CheckBox)findViewById(R.id.checkbox);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        etbufferSize.setInputType(InputType.TYPE_CLASS_NUMBER);

        editText.setText(Integer.toString(sharedPref.getInt("PORT", defaultPort)));
        bcustombufsize.setChecked(sharedPref.getBoolean("IFCUSTOMBUFSIZE",false));
        int cbufsize = sharedPref.getInt("CUSTOMBUFSIZE", 0);
        if(cbufsize>0)etbufferSize.setText(Integer.toString(cbufsize));

        displayUpdater = new DisplayUpdater(1000) {
            @Override
            public void init() {
                AppState state = AudioBuffer.getInstance().getAppState();
                if(state == AppState.ACCEPT){
                    textview1.setText("Waiting for a client connection...");
                    button.setText("STOP");
                    textView2.setText("");
                }else if(state == AppState.INIT){
                    textview1.setText("AudioTrack Initializing...");
                    button.setText("STOP");
                }else if(state == AppState.PLAY){
                    int textwidth = textview1.getWidth();
                    Paint paint = new Paint();
                    paint.setTextSize(textview1.getTextSize());
                    String content = "♪Now Playing♪";
                    while(paint.measureText(content+"　")<textwidth){
                        content = content+"　";
                    }
                    textview1.setText(content);
                    button.setText("STOP");
                    textView2.setText(ASPThread.getPlaybackDescription());
                }else if(state == AppState.STOP || state == AppState.FIRSTEXECUTE){
                    textview1.setText("Stopped.");
                    button.setText("PLAY");
                }
            }
            @Override
            public void updater() {
                AppState state = AudioBuffer.getInstance().getAppState();
                if(state == AppState.ACCEPT){
                    if("".equals(textview1.getText().toString())){textview1.setText("Waiting for a client connection...");}
                    else{textview1.setText("");}
                }else if(state == AppState.INIT){
                    if("".equals(textview1.getText().toString())){textview1.setText("AudioTrack Initializing...");}
                    else{textview1.setText("");}
                }else if(state == AppState.PLAY){
                    String content = textview1.getText().toString();
                    content = content.substring(1,content.length())+content.charAt(0);
                    textview1.setText(content);
                }else if(state == AppState.STOP){
                    stop();
                }
            }
        };
        displayUpdater.savePort(Integer.toString(defaultPort));

        if(AudioBuffer.getInstance().getAppState() == AppState.FIRSTEXECUTE) {
            AudioBuffer.getInstance().setAppState(AppState.STOP);

            mAudioStreamReceiver = new AudioStreamReceiver(defaultPort) {
                @Override
                protected void ToastText(final String data) {
                    Toast.makeText(getApplicationContext(), data, Toast.LENGTH_LONG).show();
                }
                @Override
                public void setNotification() {
                    activateNotification();
                }
                @Override
                public void cancelNotification() {
                    deactivateNotification();
                }
            };
            mAudioStreamReceiver.setDisplayUpdater(displayUpdater);

            ASPThread = new AudioStreamPlayer();
            ASPThread.start();
            mAudioStreamReceiver.setASPHandler(getASPHandler());
            AudioBuffer.getInstance().setAudioStreamReceiver(mAudioStreamReceiver);
            AudioBuffer.getInstance().setAudioStreamPlayer(ASPThread);
        }else{
            mAudioStreamReceiver = AudioBuffer.getInstance().getAudioStreamReceiver();
            _displayUpdater = mAudioStreamReceiver.getDisplayUpdater();
            displayUpdater.savePort(_displayUpdater.loadPort());
            displayUpdater.saveText(_displayUpdater.loadText());
            displayUpdater.saveifcbufsize(_displayUpdater.loadifcbufsize());
            displayUpdater.savecbufsize(_displayUpdater.loadcbufsize());
            mAudioStreamReceiver.setDisplayUpdater(displayUpdater);
            ASPThread = AudioBuffer.getInstance().getAudioStreamPlayer();
        }

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioBuffer ab = AudioBuffer.getInstance();
                if(ab.getAppState() == AppState.ACCEPT) {
                    AudioBuffer.getInstance().setAppState(AppState.STOP);
                    mAudioStreamReceiver.closeServerSocket();
                    displayUpdater.start();
                }else if(ab.getAppState() == AppState.INIT){
                    AudioBuffer.getInstance().setAppState(AppState.STOP);
                    mAudioStreamReceiver.closeSocket();
                    displayUpdater.start();
                }else if(ab.getAppState() == AppState.PLAY) {
                    mAudioStreamReceiver.setbDone();
                }
                else if(ab.getAppState() == AppState.STOP || ab.getAppState() == AppState.FIRSTEXECUTE){
                    if(bcustombufsize.isChecked()){
                        if(etbufferSize.getText().toString().equals("") || Integer.parseInt(etbufferSize.getText().toString()) <= 0){
                            Toast.makeText(getApplicationContext(), "Invalid buffer size.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    if(editText.getText().toString().equals("")){
                        Toast.makeText(getApplicationContext(), "Invalid port number.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    int port = Integer.parseInt(editText.getText().toString());
                    mAudioStreamReceiver.setPort(port);
                    ab.setUseCustomBufferSize(bcustombufsize.isChecked());
                    if(bcustombufsize.isChecked())ab.setCustomBufferSize(Integer.parseInt(etbufferSize.getText().toString()));
                    //if(!(ASPThread.getLooper().getThread() == mAudioStreamReceiver.getASPHandler().getLooper().getThread())) {
                    //    mAudioStreamReceiver.setASPHandler(getASPHandler());
                    //}
                    AudioBuffer.getInstance().setAppState(AppState.ACCEPT);
                    displayUpdater.start();
                    mAudioStreamReceiver.start();
                }
            }
        });
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editText.setText(Integer.toString(defaultPort));
                bcustombufsize.setChecked(false);
                etbufferSize.setText("");
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus){
            displayUpdater.init();
            editText.setText(displayUpdater.loadPort());
            textview1.setText(displayUpdater.loadText());
            bcustombufsize.setChecked(displayUpdater.loadifcbufsize());
            etbufferSize.setText(displayUpdater.loadcbufsize());
            AppState as = AudioBuffer.getInstance().getAppState();
            if(as != AppState.FIRSTEXECUTE && as != AppState.STOP)displayUpdater.resume();
        }else{
            displayUpdater.savePort(editText.getText().toString());
            displayUpdater.saveText(textview1.getText().toString());
            displayUpdater.saveifcbufsize(bcustombufsize.isChecked());
            displayUpdater.savecbufsize(etbufferSize.getText().toString());
            displayUpdater.stop();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        SharedPreferences.Editor editor = sharedPref.edit();
        if(Integer.parseInt(editText.getText().toString()) != defaultPort){
            editor.putInt("PORT",Integer.parseInt(editText.getText().toString()));
        }else{editor.remove("PORT");}
        editor.putBoolean("IFCUSTOMBUFSIZE",bcustombufsize.isChecked());
        if(etbufferSize.getText().toString().equals("")){editor.remove("CUSTOMBUFSIZE");}
        else{editor.putInt("CUSTOMBUFSIZE",Integer.parseInt(etbufferSize.getText().toString()));}
        editor.commit();
    }

    public void activateNotification(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder = new Notification.Builder(getApplicationContext());
        builder.setTicker("Audio playback started.");
        builder.setContentTitle("AudioStreamPlayer");
        builder.setContentText("Now Playing");
        builder.setSmallIcon(R.drawable.asp_icon);
        builder.setWhen(System.currentTimeMillis());
        Intent intent = new Intent(MainActivity.this,MainActivity.class);
        PendingIntent contentIntent =PendingIntent.getActivity(this, 0, intent, 0);
        builder.setContentIntent(contentIntent);
        builder.setOngoing(true);
        notificationManager.notify(R.string.app_name, builder.build());
    }
    public void deactivateNotification(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(R.string.app_name);
    }

    public Handler getASPHandler(){
        Handler ASPHandler = new Handler(ASPThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0:
                        AppState result = ASPThread.initBuffer((byte[]) msg.obj);
                        synchronized (mAudioStreamReceiver) {
                            mAudioStreamReceiver.setAudioTrackReady(result);
                            mAudioStreamReceiver.notifyAll();
                        }
                        break;
                    case 2:
                        ASPThread.audioclose();
                        break;
                    default:
                        ASPThread.writeBuffer((short[]) msg.obj);
                        break;
                }
            }
        };
        return ASPHandler;
    }
}