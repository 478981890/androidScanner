package com.android.scan.util;

import com.android.scan.camera.FrontLightMode;

/**
 * Created by lenovo on 2017/9/25.
 */

public class CameraConfig {
    //自动对焦
    public  static  final boolean KEY_AUTO_FOCUS = true;
    //不持续对焦
    public  static  final boolean KEY_DISABLE_CONTINUOUS_FOCUS = true;
    //反色
    public  static  final boolean KEY_INVERT_SCAN = false;
    //不进行条码场景匹配
    public static final boolean KEY_DISABLE_BARCODE_SCENE_MODE = true;
    //不距离测试
    public static final boolean KEY_DISABLE_METERING = true;
    //不曝光
    public static final boolean KEY_DISABLE_EXPOSURE = true;
    //闪光灯
    public static final FrontLightMode KEY_FRONT_LIGHT_MODE = FrontLightMode.AUTO;
    //是否播放声音
    public static final boolean KEY_PLAY_BEEP = true;
    // 是否震动
    public static final boolean KEY_VIBRATE = true;
    // 扫码类型
    public static final int KEY_DECODE_TYPE=DecodeType.KEY_DECODE_1D_PRODUCT|DecodeType.KEY_DECODE_1D_INDUSTRIAL|DecodeType.KEY_DECODE_QR;
}
