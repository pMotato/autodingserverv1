package com.yich.dingserver;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.widget.TextView;

import com.yich.webcliemt.R;

/**
 * 用启动钉钉的监控服务
 */
public class MainActivity extends AppCompatActivity {
    private TextView mTips;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTips=(TextView)this.findViewById(R.id.tv_tip);
       //开启服务
        startService(new Intent(this,DingService.class));
        mTips.setText("Ding Server StartTime:"+ DateFormat.format("yyyy-MM-dd hh:mm:ss",System.currentTimeMillis()));

    }




}
