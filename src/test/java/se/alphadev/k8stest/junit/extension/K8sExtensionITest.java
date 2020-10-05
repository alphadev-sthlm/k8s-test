package se.alphadev.k8stest.junit.extension;

import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import se.alphadev.k8stest.K8sCluster;

//@SpringBootTest(
//        classes = K8sTestConfiguration.class,
//        properties = {
//        "k8s-test.local-cluster=true",
//        "k8s-test.namespace="+ TEST_NAMESPACE,
//        "k8s-test.fail-on-existing-test-namespace=false"
//    })
@K8sTest
@Slf4j
public class K8sExtensionITest  {

    public static final String TEST_NAMESPACE = "local-k3s-cluster-test-namespace";

    @K8sTestCluster
    private K8sCluster k8sCluster = K8sCluster.builder()
                                        .testNamespace(TEST_NAMESPACE)
                                        .failOnExistingTestNamespace(false).build();

    @Test
    public void testK8sClusterConnected() throws Exception {
        assertTrue(k8sCluster.pods().isEmpty());
    }

}
