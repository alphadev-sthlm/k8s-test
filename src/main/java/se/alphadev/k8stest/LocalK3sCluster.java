package se.alphadev.k8stest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class LocalK3sCluster extends K8sCluster {


    private final static String CLUSTER_NAME = "k3s-test-cluster";

    private final static String K3D_VERSION = "v1.6.0";
    private final static String K3D_INSTALL_SCRIPT_LOCATION = "k3d-install-script/install.sh";
    private final static String K3D_EXEC = RESOURCES_DIR +"/k3d/k3d";
    private final static String K3D_KUBECONFIG_FILE = System.getProperty("user.home") +"/.config/k3d/"+ CLUSTER_NAME +"/kubeconfig.yaml";

    protected LocalK3sCluster(String namespace, boolean failOnExistingTestNamespace) {
        super(namespace, failOnExistingTestNamespace);
    }

    protected KubernetesClient doConnect() {

        log.info("Connect to local k3s cluster");
        if (Files.exists(Paths.get(K3D_EXEC))) { //should verify version
            log.info("k3d already installed at {}", K3D_EXEC);
        } else {
            installK3d();
        }

        try {
            return createClient( Duration.of(1, ChronoUnit.SECONDS));
        } catch (Exception e) { log.info("Unable to connect to existing local cluster. Will create one."); }

        try {
            deleteK3dCluster();
        } catch (K8sClusterException e) {};
        createCluster();
        return createClient(Duration.of(30, ChronoUnit.SECONDS));
    }

    /**
     * Install k3d into {user.home}/.k8s-test/k3d
     */
    protected void installK3d() {
        try {
            Files.createDirectories(Paths.get(RESOURCES_DIR+"/k3d"));
            Path k3dInstallScript = copyToResourcesdDir(K3D_INSTALL_SCRIPT_LOCATION, "/k3d/install.sh", true);

            log.info("Run k3d install script");
            ProcessBuilder pb = new ProcessBuilder(
                    "bash", "-c", k3dInstallScript.toAbsolutePath() +" --no-sudo");
            pb.environment().put("K3D_INSTALL_DIR", RESOURCES_DIR+"/k3d");
            pb.environment().put("K3D_VERSION", K3D_VERSION);
            pb.environment().put("PATH", System.getenv("PATH") +":"+ RESOURCES_DIR+"/k3d");

            Process process = pb.inheritIO().start();

            if (!process.waitFor(45, SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("Unable to download and install k3d. See log for more info");
            }
            //./k3d version
            new ProcessBuilder(
                    "bash", "-c",  K3D_EXEC +" version").inheritIO().start().waitFor(10, SECONDS);

            log.info("k3d installed succesfully");
        } catch (Exception e) {
            throw new K8sClusterException(e);
        }
    }

    protected void createCluster() {
        try {
            log.info("Create local k3s cluster {}", CLUSTER_NAME);

            Path k3sRegistriesFile = copyToResourcesdDir("k3s-registries.yaml", "/k3s-registries.yaml", false).toAbsolutePath();
            Process process = new ProcessBuilder(
                    "bash", "-c",
                    K3D_EXEC +" create --name "+ CLUSTER_NAME +
                    publishPorts() +
                    " --registry-name localhost" +
                    " --registry-port 8082" +
                    " --registries-file "+ k3sRegistriesFile)
                    .inheritIO().start();

            if (!process.waitFor(30, SECONDS) || process.exitValue() != 0) {
                throw new K8sClusterException("Unable to create k3s cluster. See log for more info");
            }
        } catch (IOException | InterruptedException e) {
            throw new K8sClusterException(e);
        }
    }

    private String publishPorts() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append(String.format(" --publish 310%1$02d:310%1$02d@master ", i));
        }
        return sb.toString();
    }

    public void deleteK3dCluster() {
        try {
            log.info("delete {}", CLUSTER_NAME);
            new ProcessBuilder(
                    "bash", "-c",  K3D_EXEC +" delete --prune --name "+ CLUSTER_NAME)
                .inheritIO().start().waitFor(10, SECONDS);
            //Delete kubeconfig?
        } catch (InterruptedException | IOException e) {
            throw new K8sClusterException(e);
        }
    }

    private KubernetesClient createClient(Duration timeout) {
        await().atMost(timeout).until(
                () -> createKubeConfigFile() && isConnected(new DefaultKubernetesClient()));
        return new DefaultKubernetesClient();
    }

    private boolean createKubeConfigFile() {
        //Abort if
        //FATA[0000] No cluster(s) found
        try {
            Files.deleteIfExists(Paths.get(K3D_KUBECONFIG_FILE));
            if ( new ProcessBuilder("bash", "-c",  K3D_EXEC +" get-kubeconfig --name "+ CLUSTER_NAME).inheritIO().start().waitFor() == 0 ) {
                Path k3dConfigFile = copyToResourcesdDir(K3D_KUBECONFIG_FILE, "kubeconfig.yaml", false);
                System.setProperty(Config.KUBERNETES_KUBECONFIG_FILE, k3dConfigFile.toAbsolutePath() +"");
                log.info("get-kubeconfig done");
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
