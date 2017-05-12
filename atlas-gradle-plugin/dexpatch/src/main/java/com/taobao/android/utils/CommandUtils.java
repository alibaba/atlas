package com.taobao.android.utils;

/**
 * @author lilong
 * @create 2017-05-12 下午2:16
 */

public class CommandUtils {

    public static void exec(String command) {
        Runtime run = Runtime.getRuntime();
        try {
            Process process = run.exec(command);
//            InputStream in = process.getInputStream();
//            while (in.read() != 0) {
////               System.out.println(line);
//            }
//            in.close();
         process.waitFor();
          process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
