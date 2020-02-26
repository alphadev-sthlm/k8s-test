# K8s-test

A library that makes it possible to interact with a local or custom(remote) kubernetes cluster in tests.

An annotated test can through a handle, connected to a kubernetes cluster, manage resources needed for the specific test.

All resources will be managed in a separate namespace.

More info:

<https://k3s.io/>

<https://github.com/rancher/k3d>

To use a non local cluster, set ```k8s-test.local-cluster=false```. A kubernetes config file, named cluster-config, must exists in the root of the classpath.

## Prerequisities

JUnit 5

## How to use

Add dependency to pom

```xml
<dependency>
  <groupId>se.alphadev</groupId>
  <artifactId>k8s-test</artifactId>
  <version>x.x.x</version>
</dependency>
```

Register K8sExtension, and inject a cluster handle into the test.

```java
@K8sTest
public class MyTest {

    @K8sCluster
    private K8sCluster cluster;
    
    @BeforeAll
    public static void beforeAll() {
        cluster.createDeployment("nginx", "nginx:latest");
        cluster.createService("nginx-svc", 8080, 8080);
        cluster.waitUntilAllPodsInNamespaceIsReady(60);
    }
    
    @Test
    public void testNginxExists() {
        assertTrue(cluster.deployments().contains("nginx"));
    }
```

## Tips and tricks

View test cluster in k9s

    export KUBECONFIG="$(k3d get-kubeconfig --name='k3s-test-cluster')"; k9s

