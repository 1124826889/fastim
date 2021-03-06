package com.zyblue.fastim.common.mytest.algorithm.bit;

/**
 * @author will.zhang
 * @date 2020/6/5 11:24
 *
 * 大宇无限面试真题
 * 给定一个32位整数 . 输出二进制表示反转后的值.例如 input 43261596（二进制 00000010100101000001111010011100） ,
 * return 964176192（二进制 00111001011110000010100101000000）
 * 要求时间复杂度做到 O(log(n))
 * 实现程序前请尽可能的描述清楚思路
 *
 */
public class BitReverse {

    /**
     * 原二进制数判断最后一位是不是1，是1的话，在结果数中+1，然后左移以为，直至32次为止
     * 整体思路，判断最后一位数是不是1，一个左移，一个右移
     * 整体的时间复杂度位O(n)
     */
    public static int reverse(int n) {
        int result = 0;
        for (int i = 0; i < 32; i++) {
            result = result << 1;

            if ((n & 1) == 1) {
                result++;
            }

            n = n >> 1;

        }
        return result;
    }

    public static int reverse2(int i) {
        // HD, Figure 7-1
        i = (i & 0x55555555) << 1 | (i >>> 1) & 0x55555555;
        i = (i & 0x33333333) << 2 | (i >>> 2) & 0x33333333;
        i = (i & 0x0f0f0f0f) << 4 | (i >>> 4) & 0x0f0f0f0f;
//        i = (i << 24) | ((i & 0xff00) << 8) |
//            ((i >>> 8) & 0xff00) | (i >>> 24);
        i = (i & 0x00ff00ff) << 8 | (i >>> 8) & 0x00ff00ff;
        i = (i & 0x0000ffff) << 16 | (i >>> 16) & 0x0000ffff;
        return i;
    }

    /*public static void main(String[] args){

        System.out.println(reverse(1<<30));
        System.out.println(1<<30);
    }*/
}
