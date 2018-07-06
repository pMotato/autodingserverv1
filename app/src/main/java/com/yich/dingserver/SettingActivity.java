package com.yich.dingserver;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.yich.webcliemt.R;

/**
 * @author yich  2016928148@qq.com
 * @Description: 钉钉相关配置的类
 * @date 2018/7/5  下午4:00
 */
public class SettingActivity extends AppCompatActivity {
    Button save;
    EditText emailCode,qq_name,qq_num;
    SharedPreferences sp;
    public  static  final String QQ_NAME="name";
    public  static  final String QQ_NUM="num";
    public  static  final String QQ_EMAIL_CODE="email";
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        save=(Button) findViewById(R.id.save_info);
        emailCode=(EditText) findViewById(R.id.email_code);
        qq_name=(EditText) findViewById(R.id.qq_name);
        qq_num=(EditText) findViewById(R.id.qq_num);
        sp=getSharedPreferences("yich",MODE_PRIVATE);
        emailCode.setText(sp.getString(QQ_EMAIL_CODE,""));
        qq_name.setText(sp.getString(QQ_NAME,""));
        qq_num.setText(sp.getString(QQ_NUM,""));

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkData())
                {
                    SharedPreferences.Editor editor=sp.edit();
                    editor.putString(QQ_NAME,qq_name.getText().toString().trim());
                    editor.putString(QQ_NUM,qq_num.getText().toString().trim());
                    editor.putString(QQ_EMAIL_CODE,emailCode.getText().toString().trim());
                   boolean succ=  editor.commit();
                   if (succ){
                       Intent intent=new Intent(SettingActivity.this,MainActivity.class);
                       startActivity(intent);
                       finish();
                   }else{
                       Toast.makeText(SettingActivity.this,"保存信息失败，请稍后重试",Toast.LENGTH_LONG).show();

                   }
                }
            }
        });
    }

    private boolean checkData() {
        if (TextUtils.isEmpty(qq_name.getText().toString())){
            Toast.makeText(this,"qq昵称必填",Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }
}
