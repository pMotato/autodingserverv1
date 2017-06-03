package com.yich.dingserver;

import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.widget.TextView;

import com.yich.webcliemt.R;

/**
 * 用启动钉钉的监控服务
 */
public class MainActivity extends AppCompatActivity {
    private TextView mTips;
   private String startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTips=(TextView)this.findViewById(R.id.tv_tip);
       //开启服务
        startService(new Intent(this,DingService.class));
        if (savedInstanceState != null
                && savedInstanceState.getString("startTime")!=null) {
            mTips.setText("Ding Server StartTime:\n"+savedInstanceState.getString("startTime"));
        }else{
            startTime=DateFormat.format("yyyy-MM-dd hh:mm:ss",System.currentTimeMillis()).toString();
            mTips.setText("Ding Server StartTime:\n"+ startTime);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        outState.putString("startTime", startTime);
        super.onSaveInstanceState(outState, outPersistentState);
    }
}
