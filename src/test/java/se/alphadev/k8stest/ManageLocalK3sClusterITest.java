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
        final K3dCommands k3dCommands = new K3dCommands();

        //given
        String k8sResourcesDir = System.getProperty("user.home")+"/.k8s-test";
        deleteQuietly(new File(k8sResourcesDir));

        //when
        k3dCommands.installK3d(LocalK3sCluster.K3D_VERSION_TAG);

        // then
        assertThat(new File(k8sResourcesDir))
            .exists();
        assertThat(new File(k8sResourcesDir+ "/k3d/k3d"))
            .exists();
        assertThat(k3dCommands.getVersion())
            .isEqualTo(LocalK3sCluster.K3D_VERSION_TAG);
    }

    @Order(2)
    @Test @DisplayName("Create and delete k3d cluster")
    public void createAndDeleteK3dCluster() throws Exception {

        //given
        LocalK3sCluster localK3sCluster = new LocalK3sCluster(TEST_NAMESPACE, false);

        //when
        K8sCluster cluster = localK3sCluster.setup();

        //then
        KubernetesClient client = cluster.client();

        assertTrue( client.namespaces()
                .withName(TEST_NAMESPACE).get().getMetadata()
                .getCreationTimestamp() != null );

        //when
        localK3sCluster.deleteK3dCluster();

        //then
        KubernetesClientException kubernetesClientException = assertThrows(
            KubernetesClientException.class,
            () -> client.namespaces().withName(TEST_NAMESPACE).get());
        assertTrue(kubernetesClientException.getCause() instanceof ConnectException);
    }

}
