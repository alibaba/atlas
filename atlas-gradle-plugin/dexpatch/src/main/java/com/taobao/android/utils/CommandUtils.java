package com.taobao.android.utils;

import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * @author lilong
 * @create 2017-05-12 下午2:16
 */

public class CommandUtils {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CommandUtils.class);

    public static void exec(File workingDir,String command) {

        logger.info("start excute commond:"+command);

        String[]commands = command.split(" ");
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(commands);
            processBuilder.directory(workingDir);
            Process process = processBuilder.start();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null;
            while ((line = bufferedReader.readLine())!= null){
                System.out.println(line);
            }
            process.waitFor();
            process.destroy();
        } catch (Throwable e) {
            if (commands[0].equals("zip")){
                try {
                    ZipUtils.addFileAndDirectoryToZip(new File(commands[2]),workingDir);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

            }else if (commands[0].equals("unzip")){
                ZipUtils.unzip(new File(commands[1]),commands[3]);
            }
            e.printStackTrace();
        }
    }
}
