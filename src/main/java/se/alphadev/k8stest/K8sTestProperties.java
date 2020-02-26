package se.alphadev.k8stest;

import lombok.Data;

@Data
//@ConfigurationProperties(prefix = "k8s-test")
public class K8sTestProperties {

    /**
     * If true; a local cluster will by installed and used
     * for testing, else kube configs current-context will
     * be used.
     */
    private Boolean localCluster = true;

    /**
     * Namespace that will be created and used.
     */
    private String namespace = "test-namespace";

    /**
     * Should the test execution fail if there exists
     * a test namespace, probably from a previous run.
     */
    Boolean failOnExistingTestNamespace = true;
}