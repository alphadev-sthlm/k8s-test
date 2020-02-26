package se.alphadev.k8stest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import se.alphadev.k8stest.LocalK3sCluster;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ManageLocalK3sClusterITest extends BaseITest {

    private String TEST_NAMESPACE = "test";

    @Order(1)
    @Test @DisplayName("Download and install k3d")
    public void downloadAndInstallK3d() throws Exception {
        String k8sResourcesDir = System.getProperty("user.home")+"/.k8s-test";
        FileUtils.deleteQuietly(new File(k8sResourcesDir));

        new LocalK3sCluster(TEST_NAMESPACE, false).installK3d();

        assertThat(new File(k8sResourcesDir)).exists();
        assertThat(new File(k8sResourcesDir+ "/k3d/k3d")).exists();
    }

    @Order(2)
    @Test @DisplayName("Setup k3d cluster")
    public void setupK3dCluster() throws Exception {

        LocalK3sCluster localK3sCluster = new LocalK3sCluster(TEST_NAMESPACE, false);

        KubernetesClient client = localK3sCluster.setup().client();

        Boolean namespaceExists = client.namespaces()
                .withName(TEST_NAMESPACE).get().getMetadata().getCreationTimestamp() != null;

        assertTrue( namespaceExists );
    }

    @Order(3)
    @Test @DisplayName("Delete k3d cluster")
    public void deleteK3dCluster() throws Exception {

        LocalK3sCluster localK3sCluster = new LocalK3sCluster(TEST_NAMESPACE, false);

        KubernetesClient client = localK3sCluster.setup().client();

        localK3sCluster.deleteK3dCluster();

        assertThrows(Exception.class, () -> client.namespaces().withName(TEST_NAMESPACE).get());
    }
}
