package com.yich.dingserver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.yich.webcliemt.R;

import java.util.Date;

/**
 * 用启动钉钉的监控服务
 */
public class MainActivity extends AppCompatActivity {
    private TextView mTips;
    private Button setting;
   private String startTime;
    public static String ACTION_LIGHT_SCREEN="light_screen";
    NetworkConnectChangedReceiver receiver;
    private Intent serviceIntent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toast.makeText(this,"记得在系统->设置->辅助功能中 开启DingdingTool",Toast.LENGTH_LONG).show();
        mTips=(TextView)this.findViewById(R.id.tv_tip);
        setting=(Button)this.findViewById(R.id.setting);
        setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(serviceIntent);
                Intent intent=new Intent(MainActivity.this,SettingActivity.class);
                startActivity(intent);
            }
        });
        //开启网络监听
        IntentFilter filter = new IntentFilter();
         receiver=new NetworkConnectChangedReceiver();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction("android.net.wifi.STATE_CHANGE");
        filter.addAction("light_screen");
        registerReceiver(receiver,filter);
       //开启服务
        serviceIntent=null;
        serviceIntent=new Intent(this,DingService.class);
        startService(serviceIntent);
        if (savedInstanceState != null
                && savedInstanceState.getString("startTime")!=null) {
            mTips.setText("Ding Server StartTime:\n"+savedInstanceState.getString("startTime"));
        }else{
            startTime=DateFormat.format("yyyy-MM-dd hh:mm:ss",System.currentTimeMillis()).toString();
            mTips.setText("Ding Server StartTime:\n"+ startTime);

        }
        //创建Intent对象，action为ELITOR_CLOCK，附加信息为字符串“你该打酱油了”
        Intent intent = new Intent(ACTION_LIGHT_SCREEN);
        intent.putExtra("msg","你该打酱油了");

//定义一个PendingIntent对象，PendingIntent.getBroadcast包含了sendBroadcast的动作。
//也就是发送了action 为"ELITOR_CLOCK"的intent
        PendingIntent pi = PendingIntent.getBroadcast(this,0,intent,0);

//AlarmManager对象,注意这里并不是new一个对象，Alarmmanager为系统级服务
        AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);

//设置闹钟从当前时间开始，每隔5s执行一次PendingIntent对象pi，注意第一个参数与第二个参数的关系
// 5秒后通过PendingIntent pi对象发送广播//计算到早上9点的
        long curTime=System.currentTimeMillis();
        Date curDate=new Date(curTime);
        //过了早上9点
       if (curDate.getHours()>9){
           Date date = new Date();
           date.setDate(date.getDate()+1);
           date.setHours(9);
           date.setMinutes(0);
           date.setSeconds(0);
           am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,System.currentTimeMillis()+(date.getTime()-curTime),24*60*60*1000,pi);
       }else{
           Date date = new Date();
           date.setDate(date.getDate());
           date.setHours(9);
           date.setMinutes(0);
           date.setSeconds(0);
           am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,System.currentTimeMillis()+(date.getTime()-curTime),24*60*60*1000,pi);
       }

    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        outState.putString("startTime", startTime);
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver!=null){
            unregisterReceiver(receiver);
        }
    }
}
