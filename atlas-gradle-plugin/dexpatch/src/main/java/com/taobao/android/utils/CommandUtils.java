package com.taobao.android.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author lilong
 * @create 2017-05-12 下午2:16
 */

public class CommandUtils {

    public static void exec(String command) {
        Runtime run = Runtime.getRuntime();
        try {
            Process process = run.exec(command);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null;
            while ((line = bufferedReader.readLine())!= null){
                System.out.println(line);
            }
         process.waitFor();
          process.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
