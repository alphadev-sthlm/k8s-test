package se.alphadev.k8stest;

import static java.nio.file.Files.exists;
import static java.util.concurrent.TimeUnit.SECONDS;
import static se.alphadev.k8stest.K8sCluster.RESOURCES_DIR;
import static se.alphadev.k8stest.Utils.copyToResourcesdDir;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class K3dCommands {

    final static String K3D_TAG = "v3.0.2";

    private final String CLUSTER_NAME = "k3s-test-cluster";

    private final String K3D_INSTALL_SCRIPT_LOCATION = "k3d-install-script/install.sh";
    private final String K3D_EXEC = RESOURCES_DIR +"/k3d/k3d";


    protected boolean checkK3dInstalled() {
        return exists(Paths.get(K3D_EXEC))
            && ShellExec.runAndGetOutput(K3D_EXEC + " --version").contains(K3D_TAG);
    }

    /**
     * Install k3d into {user.home}/.k8s-test/k3d
     */
    void installK3d() {
        try {
            Files.createDirectories(Paths.get(RESOURCES_DIR + "/k3d"));
            Path k3dInstallScript = copyToResourcesdDir(
                K3D_INSTALL_SCRIPT_LOCATION,
                "/k3d/install.sh", true);

            log.info("Run k3d install script");

            Map<String, String> env = ImmutableMap.of(
                "K3D_INSTALL_DIR",  RESOURCES_DIR + "/k3d",
                "K3D_TAG", K3D_TAG,
                "PATH", System.getenv("PATH") +":"+ RESOURCES_DIR + "/k3d");

            Process process = ShellExec.run(k3dInstallScript.toAbsolutePath() +" --no-sudo", env);
            if (!process.waitFor(45, SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("Unable to download and install k3d. See log for more info");
            }

            printVersion();

            log.info("k3d installed succesfully");
        } catch (Exception e) {
            throw new K8sClusterException(e);
        }
    }

    protected void printVersion() throws InterruptedException, IOException {
        Process process = ShellExec.run(K3D_EXEC +" version");
        process.waitFor(10, SECONDS);
        System.out.println(ShellExec.captureStdOut(process));
    }

    protected void createCluster() {
        try {
            log.info("Create local k3s cluster {}", LocalK3sCluster.CLUSTER_NAME);

            Path k3sRegistriesFile = copyToResourcesdDir(
                "k3s-registries.yaml",
                "/k3s-registries.yaml").toAbsolutePath();

            Process process = ShellExec.run(K3D_EXEC +
                " cluster create " + CLUSTER_NAME +
                " --agents 1 "+ publishPorts() +
                " --volume "+ k3sRegistriesFile +":/etc/rancher/k3s/registries.yaml"
            );

            if (!process.waitFor(30, SECONDS) || process.exitValue() != 0) {
                throw new K8sClusterException("Unable to create k3s cluster. See log for more info");
            }
        } catch (InterruptedException e) {
            throw new K8sClusterException(e);
        }
    }

    private String publishPorts() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append(String.format(" --port 310%1$02d:310%1$02d@agent[0] ", i));
        }
        return sb.toString();
    }
}
