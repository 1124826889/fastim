package com.zyblue.fastim.common.algorithm.string;

/**
 * 消消乐
 * 例子： abbbeccddcdee ==>  a
 */
public class Xiaoxiaole {

    public void String(){
        String s = "accbbbddeeedc";
        //String s = "aaccca";
        for (int i=0; i<s.length()&&s.length()>2; i++){
            if (s.charAt(i)==s.charAt(i+1) && s.charAt(i)==s.charAt(i+2)){
                s = s.substring(0,i) + s.substring(i+3);
                i = -1;
            }
        }
        System.out.println(s);
    }
}