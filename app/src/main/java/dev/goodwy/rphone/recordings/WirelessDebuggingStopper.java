package dev.goodwy.rphone.recordings;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Shell-UID helper only. Fixed disable/readback, never enables or changes USB debugging. */
final class WirelessDebuggingStopper {
    interface Command { String execute(String[] command) throws Exception; }
    static boolean disable(Command command) {
        try {
            command.execute(new String[]{"/system/bin/settings","put","global","adb_wifi_enabled","0"});
            return "0".equals(command.execute(new String[]{"/system/bin/settings","get","global","adb_wifi_enabled"}).trim());
        } catch(Exception failure){return false;}
    }
    static boolean disable() {
        return disable(command->{
            Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
            try {
                if(!process.waitFor(3,TimeUnit.SECONDS))throw new java.io.IOException("Settings timeout");
                if(process.exitValue()!=0)throw new java.io.IOException("Settings refused");
                byte[] output=new byte[128];int count=process.getInputStream().read(output);
                return count<0?"":new String(output,0,count,StandardCharsets.UTF_8);
            }finally{process.destroy();}
        });
    }
}
