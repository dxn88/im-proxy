package com.dxn.im.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class TimeUtil {
    public static String getCurrentTime() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        return df.format(new Date());// new Date()为获取当前系统时间
    }
}
