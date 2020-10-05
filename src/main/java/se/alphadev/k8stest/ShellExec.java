package se.alphadev.k8stest;

import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import org.apache.commons.io.IOUtils;

public class ShellExec {

    static Process run(String cmd) {
        return ShellExec.run(cmd, null);
    }

    static Process run(String cmd, Map<String,String> env) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);

            if (env != null) {
                pb.environment().putAll(env);
            }
            return pb.inheritIO().start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String runAndGetOutput(String cmd) {
        try {
            Process process = run(cmd);
            return ShellExec.captureStdOut(process);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static String captureStdOut(Process process) throws IOException {
        return IOUtils.readLines(process.getInputStream(), Charset.defaultCharset()).stream()
            .reduce((line1, line2) -> Strings.isNullOrEmpty(line2) ? line1 : line1 +"\n"+ line2)
                .orElse("");
    }

    protected static String captureStdErr(Process process) throws IOException {
        return IOUtils.readLines(process.getErrorStream(), Charset.defaultCharset()).stream()
            .reduce((line1, line2) -> Strings.isNullOrEmpty(line2) ? line1 : line1 +"\n"+ line2)
                .orElse("");
    }

}
