package se.alphadev.k8stest;

import static java.nio.file.Files.copy;
import static se.alphadev.k8stest.K8sCluster.RESOURCES_DIR;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Utils {

    static Path copyToResourcesdDir(String sourcePath, String destName) {
        return copyToResourcesdDir(sourcePath, destName, false);
    }

    static Path copyToResourcesdDir(String sourcePath, String destName, boolean executable) {
        try {
            Path src = Paths.get(sourcePath);
            if (!src.toFile().exists() ) {
                src = Paths.get(ClassLoader.getSystemClassLoader().getResource(sourcePath).toURI());
            }
            Path dest = Paths.get(RESOURCES_DIR + destName);

            log.info("Copy {} to {}", src, dest);
            copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
//            dest.toFile().deleteOnExit();
            dest.toFile().setExecutable(executable);
            return dest;
        } catch (Exception e) {
            log.warn("b", e);
            throw new K8sClusterException(e);
        }
    }
}
