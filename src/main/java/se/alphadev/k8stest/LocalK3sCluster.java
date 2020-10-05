package se.alphadev.k8stest;

import static java.nio.charset.Charset.defaultCharset;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.awaitility.Awaitility.await;
import static se.alphadev.k8stest.ShellExec.run;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.buildobjects.process.ExternalProcessFailureException;
import org.buildobjects.process.ProcBuilder;

@Slf4j
class LocalK3sCluster extends K8sCluster {

    final static String CLUSTER_NAME = "k3s-test-cluster";

    final static String K3D_INSTALL_SCRIPT_LOCATION = "k3d-install-script/install.sh";
    final static String K3D_EXEC = RESOURCES_DIR +"/k3d/k3d";
    private final static String K3D_KUBECONFIG_FILE = System.getProperty("user.home") +"/.config/k3d/"+ CLUSTER_NAME +"/kubeconfig.yaml";

    public K3dCommands k3dCmd = new K3dCommands();

    protected LocalK3sCluster(String namespace, boolean failOnExistingTestNamespace) {
        super(namespace, failOnExistingTestNamespace);
    }

    protected KubernetesClient doConnect() {

        log.info("Connect to local k3s cluster");
        if (!k3dCmd.checkK3dInstalled()) {
            k3dCmd.installK3d();
        }

        try {
            deleteQuietly(new File(K3D_KUBECONFIG_FILE));
            return setupClient( Duration.of(1, ChronoUnit.SECONDS));
        } catch (ConditionTimeoutException e) {
            log.info("Unable to connect to existing local cluster. Will create one."); }

        try {
            deleteK3dCluster();
        } catch (K8sClusterException e) {};

        k3dCmd.createCluster();

        return setupClient(Duration.of(30, ChronoUnit.SECONDS));
    }

    public void deleteK3dCluster() {
        try {
            log.info("delete {}", CLUSTER_NAME);
//            new ProcBuilder(K3D_EXEC, "delete", "--prune", "--name", CLUSTER_NAME)
//                .withOutputStream(System.err)
//                .withTimeoutMillis(10000).run();

            run(K3D_EXEC +" cluster delete "+ CLUSTER_NAME).waitFor(10, TimeUnit.SECONDS);

        } catch (ExternalProcessFailureException | InterruptedException e) {
            throw new K8sClusterException(e);
        }
    }

    private KubernetesClient setupClient(Duration timeout) {
        await().atMost(timeout).until(
                () ->  tryCreateKubeConfigFile() && isConnected(new DefaultKubernetesClient()));
        return new DefaultKubernetesClient();
    }

    private boolean tryCreateKubeConfigFile() {

        try {
            String config = ProcBuilder.run(K3D_EXEC, "kubeconfig", "get", CLUSTER_NAME);

            FileUtils.write(new File(K3D_KUBECONFIG_FILE), config, defaultCharset());

            System.setProperty(Config.KUBERNETES_KUBECONFIG_FILE, K3D_KUBECONFIG_FILE);
            log.info("kubeconfig written to {}", K3D_KUBECONFIG_FILE);
            return true;
        } catch (ExternalProcessFailureException e) {
            log.warn("{} -> {}", e.getCommand(), e.getStderr());
            return false;
        } catch (IOException e) {
            throw new K8sClusterException(e);
        }
    }
}
