package se.alphadev.k8stest;

import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.File;
import java.net.ConnectException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ManageLocalK3sClusterITest extends BaseITest {

    private static String TEST_NAMESPACE = "test";

    @BeforeAll
    public static void beforeAll() {
        new LocalK3sCluster(TEST_NAMESPACE, false)
            .deleteK3dCluster();
    }

    @Order(1)
    @Test @DisplayName("Download and install k3d")
    public void downloadAndInstallK3d() throws Exception {
        String k8sResourcesDir = System.getProperty("user.home")+"/.k8s-test";
        deleteQuietly(new File(k8sResourcesDir));

//        LocalK3sCluster localK3sCluster = new LocalK3sCluster(TEST_NAMESPACE, false);
        new K3dCommands().installK3d();

        assertThat(new File(k8sResourcesDir)).exists();
        assertThat(new File(k8sResourcesDir+ "/k3d/k3d")).exists();

        String version =ShellExec.runAndGetOutput(k8sResourcesDir + "/k3d/k3d version | grep 'k3d version' | cut -d \" \" -f3");
        System.out.println( version );
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

        localK3sCluster.deleteK3dCluster();

        KubernetesClient client = localK3sCluster.setup().client();

        localK3sCluster.deleteK3dCluster();

        KubernetesClientException kubernetesClientException = assertThrows(
            KubernetesClientException.class,
            () -> client.namespaces().withName(TEST_NAMESPACE).get());
        assertTrue(kubernetesClientException.getCause() instanceof ConnectException);
    }
}
