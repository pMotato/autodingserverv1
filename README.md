# autodingserver
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**这是一款实现了钉钉的自动打卡上下班辅助软件，纯属自娱自乐。**


### 1.软件使用环境
     a.已root的android手机（推荐kingroot软件，具体手机root方法不一样）
     b.安装本辅助软件，并且在系统设置的里面找到辅助功能里面去开启本软件的辅助开关
     c.安装QQ软件，因为我是通过QQ消息来控制打卡的，申请一个小号登录该手机上的qq，等待接受命令，
     d.通过系统的设置让本软件常驻内存（小米的手机可以通过菜单键来加锁应用），也可以让应用变成系统应用（可以通过re文件管理器来实现，具体去百度）
     
#####最重要的一点：当然是手机在可以打卡的范围内(例如：公司的wifi下,我的测试机整天在公司哈哈)
     
### 打包
     打包配置，在打包前在local.proties 配置发送命令者的QQ昵称。
###3.使用说明
     场景：首先要有2个扣扣号并且用户有2部手机：一部测试机，放在打卡范围内（例如就放在公司），一部是自己的手机用来控制测试机
     控制方式：自己的手机通过手机qq的软件（简称为：发送者qq，发送者qq的相关信息可以在代码里面配置也可以在app的设置里面配置）可以发送如下命令来控制钉钉
     a.上班打卡
     b.下班打卡
     c.更新下班打卡
     d.关闭钉钉
 ###  其他
 
      新增配置页面
![image](https://github.com/yuqiyich/autodingserverv1/blob/master/art/addsetting_hd.jpg)
     打卡成功发送邮件的截图
![image](https://github.com/yuqiyich/autodingserverv1/blob/master/art/%E4%B8%8A%E7%8F%AD%E8%87%AA%E5%8A%A8%E6%89%93%E5%8D%A1%E6%88%90%E5%8A%9F%E4%B9%8B%E5%90%8E%E5%8F%91%E9%80%81%E9%82%AE%E4%BB%B6%E5%88%B0%E8%87%AA%E5%B7%B1%E9%82%AE%E7%AE%B1.png)
          