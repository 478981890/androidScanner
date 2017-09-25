package com.android.scan.util;

/**
 * Created by lenovo on 2017/9/25.
 */

public class   DecodeType {
    public  static  final  int KEY_DECODE_1D_PRODUCT=1<<0 ; //商品条码
    public  static  final  int KEY_DECODE_1D_INDUSTRIAL =1<<1; //工业条码
    public  static  final  int KEY_DECODE_QR=1<<2 ;//二维码
    public  static  final  int KEY_DECODE_DATA_MATRIX=1<<3;//
    public  static  final  int KEY_DECODE_AZTEC =1<<4;
    public  static  final  int KEY_DECODE_PDF417=1<<5;
}
