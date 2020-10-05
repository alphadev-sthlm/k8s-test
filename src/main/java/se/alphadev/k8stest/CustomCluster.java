package se.alphadev.k8stest;

import static se.alphadev.k8stest.Utils.copyToResourcesdDir;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.File;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class CustomCluster extends K8sCluster {

    private File customConfig;;

    protected CustomCluster(String namespace, boolean failOnExistingTestNamespace, File customConfig) {
        super(namespace, failOnExistingTestNamespace);
        this.customConfig = customConfig;
    }

    protected KubernetesClient doConnect() {
        try {
            Path config = copyToResourcesdDir(customConfig.getPath(), "/custom-config", false);
            System.setProperty("kubeconfig", config.toString());
            return new DefaultKubernetesClient();
        } catch (KubernetesClientException e) {
            throw new K8sClusterException(e);
        }
    }

}
