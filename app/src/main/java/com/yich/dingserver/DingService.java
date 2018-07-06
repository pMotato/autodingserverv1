package com.yich.dingserver;

/**
 * Created by yich
 * on 2017/4/22.
 * Remeark:
 */


import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.sun.mail.util.MailSSLSocketFactory;
import com.yich.webcliemt.BuildConfig;
import com.yich.webcliemt.R;

import java.io.DataOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.activation.CommandMap;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.activation.MailcapCommandMap;
import javax.mail.Address;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import rx.Observable;
import rx.Observer;
import rx.schedulers.Schedulers;

public class DingService extends AccessibilityService {

    private static String TAG = "DingService";

    private boolean isFinish = false;

    public static DingService instance;

    /**
     * 大开钉钉的大步骤
     */
    public final static int STEP_OPEN_HOME = 1;
    public final static int STEP_OPEN_KAOQIN = 2;
    public final static int STEP_DO_KAOQIN = 3;
    public final static int STEP_NET_NOT_STADEABLE = 4;//打卡网络环境不好稍后上传结果
    public final static int STEP_KAOQIN_SUCC = 5;//打卡成功
    private int index = STEP_OPEN_HOME;//
    //打卡指令
    public final static int COMMAND_KAOQIN_AM = 1;//早上打卡
    public final static int COMMAND_KAOQIN_PM = 2;//下午打卡
    public final static int COMMAND_UPDATE_KAOQIN_PM = 3;//更新下午打卡
    public final static int COMMAND_NONE = 4;//默认指令
    private int mCommand = COMMAND_NONE;
    //打卡指令对应对的扣扣消息
    private final static String QQ_TEXT_AM = " 上班打卡";
    private final static String QQ_TEXT_PM = " 下班打卡";
    private static final String QQ_TEXT_STOP_DINGDING = " 关闭钉钉";
    private static final String QQ_TEXT_UPDATE_KAOQIN = " 更新下班打卡";
    private static final String MAIL_TEXT = "到达打卡界面";

    //各个打卡指令的小步骤
    private int mMiniStep = 0;
    //发送者扣扣的昵称
    private   String CONFIG_QQ_NUM = BuildConfig.QQ_NICK_NAME;
    //发送者扣扣的号码
    private   String CONFIG_QQ_NUM1 = BuildConfig.QQ_NUM;
    private   String CONFIG_QQ_MAIL = BuildConfig.QQ_MAIL_CODE;
    //下午下班时间
    private   String CONFIG_OFF_DUTY = BuildConfig.TIME_OFF_WORK;

    private final static String PKG_DINGDING = "com.alibaba.android.rimet";
    private final static String PKG_QQ = "com.tencent.mobileqq";
    public final static int MAX_CLICK_TIMES = 8;//最多尝试点击的次数
    private boolean canKaoqinPageClickable = false;
    private int kaoQinChangeCount = 0;
    private int tryCounts = 0;
    //打卡时候有发送邮件
    private boolean hasSendMail=false;
    private Rect webRect;
    private KeyguardManager.KeyguardLock kl;
    private static Context mContext;
    public final static int LIGHT_START = 4;
    public final static int CLICK_POWER_BTN = 1;
    public final static int LIGHT_END = 2;
    public final static int LIGHT_CHECK = 3;
    public static int mCountBtnCLick = 0;//电源键模拟的次数
    public final static int DELAY_TIME = 500;

    KeyguardManager km;
    public static Handler lightHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == CLICK_POWER_BTN || msg.what == LIGHT_START) {
                mCountBtnCLick++;
                Log.w(TAG, "light screen by power btn,try time:" + mCountBtnCLick);
                execShellCmd("input keyevent " + KeyEvent.KEYCODE_POWER);
                sendEmptyMessageDelayed(LIGHT_CHECK, DELAY_TIME);
            }
            if (msg.what == LIGHT_CHECK) {
                if (mContext != null) {
                    PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                    if (pm.isScreenOn()) {
                        sendEmptyMessage(LIGHT_END);
                    } else {
                        sendEmptyMessageDelayed(CLICK_POWER_BTN, DELAY_TIME);
                    }
                } else {
                    Log.e(TAG, "DingService is dead ,please reset");
                }
            }
            if (msg.what == LIGHT_END) {
                mCountBtnCLick = 0;
            }
        }

        boolean isWorking() {
            if (mCountBtnCLick > 0) {
                return true;
            } else {
                return false;
            }
        }
    };

    @Override
    public void onCreate() {
        Log.e(TAG, "----######---server is onCreate ----######---");
        super.onCreate();
        /**解锁屏幕**/
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        kl = km.newKeyguardLock("unLock");
        //设置前台应用
        createNotifition();
        initSetting();
        mContext = this;
    }

    private void initSetting() {
        SharedPreferences sp=getSharedPreferences("yich",MODE_PRIVATE);
        CONFIG_QQ_MAIL= sp.getString(SettingActivity.QQ_EMAIL_CODE,"");
        CONFIG_QQ_NUM1=  sp.getString(SettingActivity.QQ_NAME,"");
        CONFIG_QQ_NUM=sp.getString(SettingActivity.QQ_NUM,"");

    }

    /**
     */


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        ArrayList<String> texts = new ArrayList<String>();
        Log.i(TAG, "事件---->" + event.getEventType() + "app包名---->" + event.getPackageName());
        String pkgName = event.getPackageName().toString();
        if (PKG_DINGDING.equals(pkgName) && tryCounts < MAX_CLICK_TIMES && mCommand != COMMAND_NONE) {
            if (mCommand == COMMAND_KAOQIN_PM) {
                if (!checkTime()) {//为到下午打卡时间
                    return;
                }
            }
            callDingServer(event);
        } else if (PKG_QQ.equals(pkgName)) {
            getQQCommand(event);
        }


    }

    public boolean stopProcessByPKG(String PKGname) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        try {
            execShellCmd("am force-stop " + PKGname);
        } catch (SecurityException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void getQQCommand(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Log.i(TAG, "Recieved event");
            Parcelable data = e.getParcelableData();
            if (data != null && data instanceof Notification) {
                Notification notification = (Notification) data;
                if (notification.tickerText == null) {
                    Log.e(TAG, "++++++++command text is null");
                    return;
                }
                String notiText = notification.tickerText.toString();
                Log.i(TAG, "Recieved notification text:" + notification.tickerText.toString());

                if ((CONFIG_QQ_NUM + ":" + QQ_TEXT_PM).equals(notiText)) {
                    initCommand(COMMAND_KAOQIN_PM);
                    sendQQMail("开始下班打卡");
                    prepareForKaoQin();
                } else if ((CONFIG_QQ_NUM + ":" + QQ_TEXT_AM).equals(notiText)) {
                    initCommand(COMMAND_KAOQIN_AM);
                    sendQQMail("开始上班打卡");
                    prepareForKaoQin();
                } else if ((CONFIG_QQ_NUM + ":" + QQ_TEXT_STOP_DINGDING).equals(notiText)) {
                    wakeUpAndUnlock(this);
                    initCommand(COMMAND_NONE);
                    stopProcessByPKG(PKG_DINGDING);
                } else if ((CONFIG_QQ_NUM + ":" + QQ_TEXT_UPDATE_KAOQIN).equals(notiText)) {
                    initCommand(COMMAND_UPDATE_KAOQIN_PM);
                    sendQQMail("开始更新下班打卡");
                    prepareForKaoQin();
                }
            }
        }
    }

    private void sendQQMail(String msg1) {
        if (hasSendMail){
            Log.e(TAG, "hasSendMail already");
            return ;
        }
        Observable.just(msg1).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).subscribe(new Observer<String>() {
            @Override
            public void onCompleted() {
                Log.e(TAG, "onCompleted");
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(String o) {
                if (o.contains(MAIL_TEXT)){
                           hasSendMail=true;
                    schedulerSendImgTask(o+"截图");
                }else{
                    sendMailMsg(o);
                }
            }
        });

    }

    public void sendMailMsgwithImg(String msg1,String imgurl) {
        try {
            String sendMsg = "[" + DateFormat.format("yyyy-MM-dd hh:mm:ss",System.currentTimeMillis()).toString() + "]" + msg1;
            Properties props = new Properties();
            // 开启debug调试
            MailSSLSocketFactory sf = new MailSSLSocketFactory();
            sf.setTrustAllHosts(true);
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.socketFactory", sf);

            props.setProperty("mail.debug", "false");
            // 发送邮件协议名称
            props.setProperty("mail.transport.protocol", "smtp");
            props.setProperty("mail.smtp.host", "smtp.qq.com");

            props.setProperty("mail.smtp.auth", "true");

            // 设置环境信息
            Session session= Session.getInstance(props);

            // 创建邮件对象
            javax.mail.Message msg = new MimeMessage(session);
            msg.setSubject("打卡记录");
            /***********************/
            MimeBodyPart text = new MimeBodyPart();
            // setContent(“邮件的正文内容”,”设置邮件内容的编码方式”)
            text.setContent(msg1+"<img src='cid:a'>","text/html;charset=utf-8");
            MimeBodyPart img = new MimeBodyPart();
         /*JavaMail API不限制信息只为文本,任何形式的信息都可能作茧自缚MimeMessage的一部分.
        * 除了文本信息,作为文件附件包含在电子邮件信息的一部分是很普遍的.
        * JavaMail API通过使用DataHandler对象,提供一个允许我们包含非文本BodyPart对象的简便方法.*/
            DataHandler dh = new DataHandler(new FileDataSource(imgurl));
            img.setDataHandler(dh);
            //创建图片的一个表示用于显示在邮件中显示
            img.setContentID("a");
            //关系   正文和图片的
            MimeMultipart mm = new MimeMultipart();
            mm.addBodyPart(text);
            mm.addBodyPart(img);
            mm.setSubType("related");//设置正文与图片之间的关系
            //图班与正文的 body
            MimeBodyPart all = new MimeBodyPart();
            all.setContent(mm);
            //附件与正文（text 和 img）的关系
            MimeMultipart mm2 = new MimeMultipart();
            mm2.addBodyPart(all);
            mm2.setSubType("mixed");//设置正文与附件之间的关系
            msg.setContent(mm2);
            msg.saveChanges(); //保存修改
            MailcapCommandMap mc = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
            mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html");
            mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml");
            mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain");
            mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
            mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
            CommandMap.setDefaultCommandMap(mc);
            /***********************/
            // 设置发件人
            msg.setFrom(new InternetAddress(CONFIG_QQ_NUM1+"@qq.com"));
            //发送邮件
            Transport transport = session.getTransport();
            transport.connect("smtp.qq.com",CONFIG_QQ_NUM1+"@qq.com", CONFIG_QQ_MAIL);

            transport.sendMessage(msg, new Address[] { new InternetAddress(CONFIG_QQ_NUM1+"@qq.com") });
            transport.close();

            System.out.println("成功");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void initCommand(int commandKaoqinPm) {
        mMiniStep = 0;
        mCommand = commandKaoqinPm;
        index = STEP_OPEN_HOME;
        canKaoqinPageClickable = false;
        isPageLoaded = false;
        kaoQinChangeCount = 0;
        pageLoadedCount = 0;
        tryCounts = 0;//单次命令尝试点击次数
        hasSendMail=false;
    }

    public boolean startAPP(String appPackageName) {
        try {
            Intent intent = this.getPackageManager().getLaunchIntentForPackage(appPackageName);
            startActivity(intent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "没有安装", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /**
     * 关闭钉钉，在开启钉钉，保证打卡的流程唯一
     */
    private void prepareForKaoQin() {
        wakeUpAndUnlock(this);
        if (stopProcessByPKG(PKG_DINGDING)) {
            try {
                doOpenDingding();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "stopProcessByPKG PKG_DINGDING failed");
        }
    }

    private void doOpenDingding() throws InterruptedException {
        Thread.sleep(5000);
        boolean isOpen = startAPP(PKG_DINGDING);
        if (!isOpen) {
            doOpenDingding();
        }
    }

    private void callDingServer(AccessibilityEvent event) {
        if (isFinish) {
            return;
        }

        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            Log.w(TAG, "rootWindow为空");
            return;
        }

        System.out.println("index:" + index);
        switch (index) {
            case STEP_OPEN_HOME: //进入主页
                OpenHome(event.getEventType(), nodeInfo);
                break;
            case STEP_OPEN_KAOQIN: //进入签到页
                OpenQianDao(event.getEventType(), nodeInfo);
                break;
            case STEP_DO_KAOQIN:
                doQianDao(event.getEventType(), nodeInfo);
                break;

            default:
                break;
        }
    }


    private void OpenHome(int type, AccessibilityNodeInfo nodeInfo) {
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            //判断当前是否是钉钉主页
            List<AccessibilityNodeInfo> homeList = nodeInfo.findAccessibilityNodeInfosByText("工作");
            if (!homeList.isEmpty()) {
                //点击
                Log.w(TAG, "开始点击进入主页工作");
                boolean isHome = click("工作");
                Log.w(TAG, "---->" + isHome);
                if (isHome) {
                    index = STEP_OPEN_KAOQIN;
                } else {
                    Log.e(TAG, "点击进入工作页面失败");
                }

            }
        }

    }

    private void OpenQianDao(int type, AccessibilityNodeInfo nodeInfo) {
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            //判断当前是否是主页的签到页
            List<AccessibilityNodeInfo> qianList = nodeInfo.findAccessibilityNodeInfosByText("工作");
            if (!qianList.isEmpty()) {
                Log.w(TAG, "开始点击进入考勤打卡页面");
                boolean ret = click("考勤打卡");
                if (ret) {
                    index = STEP_DO_KAOQIN;
                } else {
                    Log.e(TAG, "点击进入考勤打卡页面失败");
                }
            }

//           index = ret?3:1;
        }

    }

    public List<AccessibilityNodeInfo> findAllChildNodes(List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo parentNode) {
        int nodeCount = parentNode.getChildCount();
        for (int i = 0; i < nodeCount; i++) {
            AccessibilityNodeInfo nodeinfo = parentNode.getChild(i);
            nodes.add(parentNode.getChild(i));
            if (nodeinfo != null) {
                int childCount = nodeinfo.getChildCount();
                if (childCount > 0) {
                    findAllChildNodes(nodes, nodeinfo);
                }
            }

        }
        return nodes;
    }

    private long lastTime;
    private float lastDelt;
    private boolean isPageLoaded = false;
    private int pageLoadedCount = 0;

    private void doQianDao(int type, AccessibilityNodeInfo nodeInfo) {
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            kaoQinChangeCount++;
//            Log.w(TAG, "考勤页面的变化次数"+kaoQinChangeCount );
            if (kaoQinChangeCount > 1) {
                float delt = (System.currentTimeMillis() - lastTime) / 1000.f;
//                Log.w(TAG, "考勤页面的距上次变化时差:"+delt);
                if (kaoQinChangeCount > 2) {
//                    Log.w(TAG, "考勤页面的距上次变化速度:"+delt/lastDelt);
                    //变化很大的时候可以判断页面加载完毕了
                    if (isPageLoaded) {
                        if (pageLoadedCount > 2) {
                            canKaoqinPageClickable = true;
                        } else {
                            pageLoadedCount++;
                        }
                    }
                    //变化率大于5 并且时差要大于1s；
                    if (delt / lastDelt > 5 && delt > 1.5 && kaoQinChangeCount > 6) {
                        isPageLoaded = true;
                        Log.w(TAG, "当前页面加载完毕，加载次数为：" + kaoQinChangeCount);
                    }
                }
                lastDelt = delt;
            }
            lastTime = System.currentTimeMillis();
            if (!canKaoqinPageClickable) {
//                Log.i(TAG, "***考勤页面还未加载完毕，请稍等");
                return;
            }
            List<AccessibilityNodeInfo> allChildNodes = new ArrayList<>();
            if (mMiniStep > 0) {
                doKaoQinNextStep(mCommand);
            } else {
                findAllChildNodes(allChildNodes, nodeInfo);
                for (AccessibilityNodeInfo node : allChildNodes) {
                    if (node != null && node.getClassName() != null && node.getClassName().toString().equals("android.webkit.WebView") && mMiniStep == 0) {
                        webRect = new Rect();
                        node.getBoundsInScreen(webRect);
                        Log.w(TAG, "找到webView,当前指令code：" + mCommand);
                        Log.w(TAG, "找到webView,left,top,right,bottom：" + webRect.left + ',' + webRect.top + ',' + webRect.right + ',' + webRect.bottom + ',');
                        if (mCommand == COMMAND_KAOQIN_PM) {
                            if (mMiniStep == 0) {
                                clickPointOnScreen(webRect.centerX(), 3 * (webRect.bottom - webRect.top) / 4 + webRect.top);
                                mMiniStep++;
                                break;
                            }
                        } else if (mCommand == COMMAND_KAOQIN_AM) {
                            if (mMiniStep == 0) {
                                clickPointOnScreen(webRect.centerX(), (webRect.bottom - webRect.top) / 4 + webRect.top);
                                mMiniStep++;
                                break;
                            }
                        } else if (mCommand == COMMAND_UPDATE_KAOQIN_PM) {
                            //点击更新打卡
                            if (mMiniStep == 0) {
                                clickPointOnScreen(webRect.centerX() / 2, (int) (16.2 * (webRect.bottom - webRect.top) / 27) + webRect.top);
                                mMiniStep++;
                                break;
                            }
                        } else {
                            Log.e(TAG, "unkonw command code is:" + mCommand);
                        }
                        break;
                    }
                    ;
                }
            }

        }


    }

    private void doKaoQinNextStep(int command) {
        if (tryCounts > MAX_CLICK_TIMES) {
            Log.e(TAG, "click times is:" + tryCounts);
            return;
        }
        tryCounts++;
        switch (command) {
            case COMMAND_KAOQIN_AM:
            case COMMAND_KAOQIN_PM:
                if (mMiniStep >= 1) {
                    if (click("确定")) {
                        mMiniStep++;
                        Log.w(TAG, "点击了第" + (mMiniStep - 1) + "个确定，当前的command的code：" + mCommand);
                        //
                    }
                    //点击我知道了的按钮
                    clickPointOnScreen(webRect.centerX(), (int) (21.2 * (webRect.bottom - webRect.top) / 27) + webRect.top);
                    if (command==COMMAND_KAOQIN_AM){
                        sendQQMail("【上班】"+MAIL_TEXT);
                    }else{
                        sendQQMail("【下班】"+MAIL_TEXT);
                    }


                }

                break;
            case COMMAND_UPDATE_KAOQIN_PM:
                //如果是更新打卡，会弹出对话框是否更新打卡,或者出现网络不好请确认打卡的对话框,必定会出现一个
                if (mMiniStep == 1) {
                    if (click("确定")) {
                        mMiniStep++;
                        Log.w(TAG, "更新打卡点击了第" + (mMiniStep - 1) + "个确定，当前的command的code：" + mCommand);
                    } else {
                        Log.w(TAG, "COMMAND_UPDATE_KAOQIN_PM：点击了第" + mMiniStep + "个确定失败");
                    }
                    return;
                }
                if (mMiniStep > 1) {
                    if (click("确定")) {
                        mMiniStep++;
                        Log.w(TAG, "点击了第二个确定，当前的command的code：" + mCommand);
                        //
                    } else {
                        Log.w(TAG, "点击了第个" + mMiniStep + "确定失败，当前的command的code：" + mCommand);
                    }
                    //点击确认早退的确认按钮
                    clickPointOnScreen(3 * webRect.centerX() / 2, (int) (21.2 * (webRect.bottom - webRect.top) / 27) + webRect.top);
                    //点击我知道了的按钮
                    clickPointOnScreen(webRect.centerX(), (int) (21.2 * (webRect.bottom - webRect.top) / 27) + webRect.top);
                    sendQQMail("【更新下班】"+MAIL_TEXT);
                }
                break;
            default:
                Log.w(TAG, "unkown comand: code is:" + command);
                break;
       }
    }

    private void schedulerSendImgTask(final String msg) {
        Timer timer=new Timer();
        //小米的截屏

        TimerTask  mTask=new TimerTask() {
            @Override
            public void run() {
                String mSavedPath=getSDPath();
                   execShellCmd("screencap -p  " + mSavedPath+"/cardImg.png");
            }
        };
        TimerTask  mSendTask=new TimerTask() {
            @Override
            public void run() {
                String mSavedPath=getSDPath();
                sendMailMsgwithImg(msg,mSavedPath+"/cardImg.png");
            }
        };
        timer.schedule(mTask,15*1000);
        timer.schedule(mSendTask,17*1000);

    }

    /**
     * 检测打卡时间
     */
    private boolean checkTime() {
        Date cur = Calendar.getInstance().getTime();
        Date m9 = (Date) cur.clone();
        Date m18 = (Date) cur.clone();
        int offwork = 18;
        if (null != CONFIG_OFF_DUTY) {
            try {
                offwork = Integer.valueOf(CONFIG_OFF_DUTY);
            } catch (Exception e) {
                offwork = 18;
            }
        }
        m18.setHours(offwork);
        if (mCommand == COMMAND_KAOQIN_PM && cur.getTime() < m18.getTime()) {
            Log.e(TAG, "未到下午打卡时间");
            return false;
        } else {
            return true;
        }

    }

    private AccessibilityNodeInfo getClickParent(AccessibilityNodeInfo node) {
        if (!node.isClickable()) {
            if (node.getParent() != null) {
                return getClickParent(node.getParent());
            }
            return node;
        }
        return node;
    }

    public boolean clickPointOnScreen(int x, int y) {

        Log.w(TAG, "+++++++++++++模拟点击的点x:" + x + ",y:" + y);
        try {
            //点击前滞留1s
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        execShellCmd("input tap " + x + " " + y);
        return true;
    }


    //通过文字点击
    private boolean click(String viewText) {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
//        List<AccessibilityWindowInfo> windowInfo=getWindows();
        try {
            //点击前滞留1s
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (nodeInfo == null) {
            Log.w(TAG, "点击失败，rootWindow为空");
            return false;
        }
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(viewText);

        if (list.isEmpty()) {
            //没有该文字的控件
            Log.w(TAG, "点击失败，" + viewText + "控件列表为空");
            return false;
        } else {
            for (AccessibilityNodeInfo info : list) {
                if (viewText.equals(info.getText().toString())) {
                    return onclick(info);  //遍历点击
                }
            }
            return false;
        }

    }

    public static void execShellCmd(String cmd) {
        try {
            // 申请获取root权限，这一步很重要，不然会没有作用
            Process process = Runtime.getRuntime().exec("su");
            // 获取输出流
            OutputStream outputStream = process.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(
                    outputStream);
            dataOutputStream.writeBytes(cmd);
            dataOutputStream.flush();
            dataOutputStream.close();
            outputStream.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private boolean onclick(AccessibilityNodeInfo view) {
        if (view == null) {
            Log.w(TAG, "node 为空无法点击");
            return false;
        }
        if (view.isClickable()) {
            view.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.w(TAG, "view name" + view.getClassName() + "+点击成功");
            Log.w(TAG, "view text" + view.getText());
            return true;
        } else {

            AccessibilityNodeInfo parent = view.getParent();
            if (parent == null) {
                return false;
            }
            return onclick(parent);
        }
    }

    //点击返回按钮事件
    private void back() {
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected void onServiceConnected() {

        super.onServiceConnected();
        Log.i(TAG, "service connected!");
        Toast.makeText(getApplicationContext(), "连接成功！", Toast.LENGTH_LONG).show();
        instance = this;
    }

    public void setServiceEnable() {
        isFinish = false;
        Toast.makeText(getApplicationContext(), "服务可用开启！", Toast.LENGTH_LONG).show();
        index = 1;
    }

    public void wakeUpAndUnlock(Context context) {
        //获取电源管理器对象
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        //获取PowerManager.WakeLock对象,后面的参数|表示同时传入两个值,最后的是LogCat里用的Tag
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
        //点亮屏幕
        wl.acquire();
        //释放
        km.isKeyguardLocked();
        wl.release();
        if (!pm.isScreenOn()) {
            if (lightHandler != null && mCountBtnCLick == 0) {
                lightHandler.sendEmptyMessage(LIGHT_START);
            } else {
                Log.e(TAG, "lightHandler can not be null or is click working  now");
            }
        }
        if (km.isKeyguardLocked()) {
            kl.disableKeyguard();
            km.exitKeyguardSecurely(new KeyguardManager.OnKeyguardExitResult() {
                @Override
                public void onKeyguardExitResult(boolean success) {
                    if (success) {
                        Log.i(TAG, " keyGarud is Unlocked -_-");
                    } else {
                        Log.i(TAG, " keyGarud is locked ,unlock again");
                        if (kl != null) {
                            kl.reenableKeyguard();
                        }
                        kl.disableKeyguard();
                        km.exitKeyguardSecurely(this);
                    }
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "----######---server is onDestroy  ----######---");
        super.onDestroy();

        mContext = null;
        lightHandler = null;
    }

    private void createNotifition() {
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentText("钉钉打卡服务监控");
        builder.setContentTitle("钉钉打卡");
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setTicker("钉钉打卡服务监控");
        builder.setAutoCancel(true);
        builder.setWhen(System.currentTimeMillis());
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 10, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(pendingIntent);
        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);
    }

    public static int NOTIFICATION_ID = 10000;

    public String getLastImgPath() {
        String fullPicPath="none";
        if (mContext!=null){
              File piDir=new File(getSDPath()+"/DCIM/Screenshots/");
            if (piDir!=null&&piDir.isDirectory()){
                File[] pics=piDir.listFiles();
                if (pics!=null&&pics.length>0){
                    for (int i = 0; i <pics.length ; i++) {
                        if (i==0){
                            fullPicPath=pics[i].getAbsolutePath();
                        }else{
                            if (pics[i].lastModified()>pics[i-1].lastModified()){
                                fullPicPath=pics[i].getAbsolutePath();
                            }
                        }
                    }
                }
            }
        }
        return fullPicPath;
    }

    public String getSDPath(){
        File sdDir = new File( "/mnt/sdcard");
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED);//判断sd卡是否存在
        if(sdCardExist)
        {
            sdDir = Environment.getExternalStorageDirectory();//获取跟目录
        }
        return sdDir.toString();
    }
    public void sendMailMsg(String msg1) {
        try {
            String sendMsg = "[" + DateFormat.format("yyyy-MM-dd hh:mm:ss",System.currentTimeMillis()).toString() + "]" + msg1;
            Properties props = new Properties();
            // 开启debug调试
            MailSSLSocketFactory sf = new MailSSLSocketFactory();
            sf.setTrustAllHosts(true);
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.socketFactory", sf);

            props.setProperty("mail.debug", "false");
            // 发送邮件协议名称
            props.setProperty("mail.transport.protocol", "smtp");
            props.setProperty("mail.smtp.host", "smtp.qq.com");

            props.setProperty("mail.smtp.auth", "true");

            // 设置环境信息
            Session session= Session.getInstance(props);

            // 创建邮件对象
            javax.mail.Message msg = new MimeMessage(session);
            msg.setSubject("打卡记录");
            // 设置邮件内容
            msg.setText(sendMsg);
            // 设置发件人
            msg.setFrom(new InternetAddress(CONFIG_QQ_NUM+"@qq.com"));
            //发送邮件
            Transport transport = session.getTransport();
            transport.connect("smtp.qq.com",CONFIG_QQ_NUM+"@qq.com", BuildConfig.QQ_MAIL_CODE);

            transport.sendMessage(msg, new Address[] { new InternetAddress(CONFIG_QQ_NUM+"@qq.com") });
            transport.close();

            System.out.println("成功");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
