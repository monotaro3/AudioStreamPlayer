package com.audiostreamplayer;

import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
    TextView textview1;
    EditText editText;
    Button button;
    DisplayUpdater displayUpdater = new DisplayUpdater(1000) {
        @Override
        public void init() {
            AppState state = AudioBuffer.getInstance().getAppState();
            if(state == AppState.ACCEPT){
                textview1.setText("Waiting for a client connection...");
                button.setText("STOP");
            }else if(state == AppState.INIT){
                textview1.setText("AudioTrack Initializing...");
            }else if(state == AppState.PLAY){
                //textview1.getWidth()/getResources().getDisplayMetrics().density
                int textwidth = textview1.getWidth();
                Paint paint = new Paint();
                paint.setTextSize(textview1.getTextSize());
                String content = "♪Now Playing♪";
                while(paint.measureText(content+"　")<textwidth){
                    content = content+"　";
                }
                textview1.setText(content);
            }else if(state == AppState.STOP){
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

    private AudioStreamReceiver mAudioStreamReceiver = new AudioStreamReceiver(9876) {
        @Override
        protected void ToastText(final String data) {
            Toast.makeText(getApplicationContext(), data, Toast.LENGTH_LONG).show();
        }
        @Override
        protected void setDisplayUpdater(boolean flag) {
            if(flag){
                displayUpdater.start();}else{
                displayUpdater.stop();}
        }
    };
    private AudioStreamPlayer ASPThread;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textview1 = (TextView)findViewById(R.id.text_view2);
        editText = (EditText)findViewById(R.id.edit_text);
        button = (Button)findViewById(R.id.button);

        editText.setInputType(InputType.TYPE_CLASS_NUMBER);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AudioBuffer ab = AudioBuffer.getInstance();
                if(ab.getAppState() == AppState.ACCEPT){
                    AudioBuffer.getInstance().setAppState(AppState.STOP);
                    mAudioStreamReceiver.closeServerSocket();
                    displayUpdater.start();
                }else if(ab.getAppState() == AppState.PLAY) {
                    mAudioStreamReceiver.setbDone();
                }
                else if(ab.getAppState() == AppState.STOP){
                    int port = Integer.parseInt(editText.getText().toString());
                    mAudioStreamReceiver.setPort(port);
                    AudioBuffer.getInstance().setAppState(AppState.ACCEPT);
                    displayUpdater.start();
                    mAudioStreamReceiver.start();
                }
            }
        });

        ASPThread = new AudioStreamPlayer();
        ASPThread.start();

        Handler ASPHandler = new Handler(ASPThread.getLooper()){
            @Override
            public void handleMessage(Message msg){
                switch(msg.what) {
                    case 0:
                        AppState result = ASPThread.initBuffer((byte[]) msg.obj);
                        synchronized (mAudioStreamReceiver){
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

        mAudioStreamReceiver.setASPHandler(ASPHandler);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mAudioStreamReceiver.setbDone();
    }
}