package se.alphadev.k8stest;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.alphadev.k8stest.junit.extension.K8sTest;
import se.alphadev.k8stest.junit.extension.K8sTestCluster;


@K8sTest
public class LocalK3sClusterITest extends BaseITest {

    static final String TEST_NAMESPACE = "local-k3s-cluster-test";

    @K8sTestCluster
    private K8sCluster k8sCluster = K8sCluster.builder()
                                        .testNamespace(TEST_NAMESPACE)
                                        .failOnExistingTestNamespace(false).build();

    @Test @DisplayName("Is local cluster")
    void isLocalK3sCluster() throws Exception {
        assertThat(k8sCluster).isExactlyInstanceOf(LocalK3sCluster.class);
    }

    @Test @DisplayName("Create pod in local cluster")
    void createPod() throws Exception {

        CompletableFuture<Pod> createPodFuture = k8sCluster.createPod("nginx", "nginx");

        Pod pod = createPodFuture.get();

        assertThat(pod.getMetadata().getName()).isEqualTo("nginx");
        assertThat(pod.getMetadata().getNamespace()).isEqualTo(TEST_NAMESPACE);

    }

    @Test @DisplayName("Create deployment in local cluster")
    void createDeployment() throws Exception {

        CompletableFuture<Deployment> createFuture = k8sCluster.createDeployment("nginx", "nginx");

        Deployment deployment = createFuture.get();

        assertThat(deployment.getMetadata().getCreationTimestamp()).isNotNull();
        assertThat(deployment.getMetadata().getName()).isEqualTo("nginx");
        assertThat(deployment.getMetadata().getNamespace()).isEqualTo(TEST_NAMESPACE);
    }

    @Test @DisplayName("Create service in local cluster")
    void createService() throws Exception {

        Service service = k8sCluster.createService("nginx", 8080, 8080);

        assertThat(service.getMetadata().getCreationTimestamp()).isNotNull();
        assertThat(service.getMetadata().getName()).isEqualTo("nginx-svc");
        assertThat(service.getMetadata().getNamespace()).isEqualTo(TEST_NAMESPACE);
        assertThat(service.getSpec().getPorts().size()).isEqualTo(1);
        assertThat(service.getSpec().getPorts().get(0))
            .isEqualTo(new ServicePort( null, "http", null, 8080, "TCP", new IntOrString(8080)));
    }

}
