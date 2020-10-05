package se.alphadev.k8stest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.alphadev.k8stest.junit.extension.K8sTest;
import se.alphadev.k8stest.junit.extension.K8sTestCluster;

//@SpringBootTest(
//        classes = K8sTestConfiguration.class,
//        properties = {
//        "k8s-test.local-k8sCluster=false",
//        "k8s-test.namespace="+ TEST_NAMESPACE,
//        "k8s-test.fail-on-existing-test-namespace=false"
//    })
@K8sTest
public class CustomClusterITest extends BaseITest {

    static final String TEST_NAMESPACE = "test-namespace";

    @K8sTestCluster
    private K8sCluster k8sCluster = K8sCluster.builder()
                                        .config(new File("./src/test/resources/custom-config"))
                                        .testNamespace(TEST_NAMESPACE)
                                        .failOnExistingTestNamespace(false).build();

    @Test @DisplayName("Is custom k8sCluster")
    void isLocalK3sCluster() throws Exception {
        assertThat(k8sCluster).isExactlyInstanceOf(CustomCluster.class);
    }

    @Test @DisplayName("Test connect to custom k8sCluster")
    public void testConnect() throws Exception {
        KubernetesClient client = k8sCluster.client();

        Boolean namespaceExists = client.namespaces().withName(TEST_NAMESPACE).get().getMetadata().getCreationTimestamp() != null;

        assertTrue( namespaceExists );
    }

    @Test @DisplayName("Test tear down of custom k8sCluster")
    public void testTearDown() throws Exception {
        KubernetesClient client = k8sCluster.setup().client();

        k8sCluster.tearDown();

        await().until(() -> client.namespaces().withName(TEST_NAMESPACE).get() == null);

    }

}
