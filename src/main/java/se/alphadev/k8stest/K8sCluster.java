package se.alphadev.k8stest;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.walk;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class K8sCluster {

    protected final static String RESOURCES_DIR = System.getProperty("user.home") +"/.k8s-test";

    private String namespace;
    private boolean failOnExistingTestNamespace;
    private KubernetesClient client;

    protected K8sCluster(String namespace, boolean failOnExistingTestNamespace) {
        this.namespace = namespace;
        this.failOnExistingTestNamespace = failOnExistingTestNamespace;
    }

    KubernetesClient client() {
        if (client == null)
            throw new K8sClusterException("Cluster not setup. Call setup() before getting client.");
        return client;
    }

    /**
     * Connect to, (or create) a k8s cluster
     * @return
     * @return connected client
     * @throws Exception
     */
    public final K8sCluster setup() {
        createResourcesDir();
        this.client = doConnect();
        setupTestNamespace();
        logClusterInfo();
//        logEvents();
        return this;
    }

    protected void createResourcesDir() {
        try {
            Files.createDirectories(Paths.get(RESOURCES_DIR));
        } catch (IOException e) {
            throw new K8sClusterException(e);
        }
    }

    protected abstract KubernetesClient doConnect();


    private void setupTestNamespace() {
        createTestNamespace();
        createEnvConfigMap();
    }

    private void createTestNamespace() {
        Namespace testNamespace = client().namespaces().withName(namespace).get();
        if (testNamespace != null && failOnExistingTestNamespace) {
            throw new K8sClusterException("There already exist a test namespace in the cluster: "+ testNamespace +". Delete or set 'k8s-test.fail-on-existing-test-namespace=false'");
        }
        if (testNamespace != null) {
            log.info("Found existing test namespace {}, will delete it.", testNamespace.getStatus());
            deleteNamespaceAndWait(60, namespace);
        }
        createTestNamespaceAndWait(30, namespace);
    }

    private void createEnvConfigMap() {
        ConfigMap configMap = client().configMaps().inNamespace(namespace).withName("cluster-config").create(
                new ConfigMapBuilder()
                    .withNewMetadata().withName("cluster-config").endMetadata()
                    .addToData("cluster-name", "e2e").build());
        log.info("Created environment configmap: {}, data: {}", configMap.getMetadata().getName(), configMap.getData());
    }

    public void tearDown() {
        deleteNamespaceNoWait(namespace);
    }

    private void logClusterInfo() {
        log.info("connected to cluster {}", client().getMasterUrl());
        log.info("namespace {}", namespace);
        log.info("deployments: {}", deployments());
        log.info("images: {}", images());
    }

    public List<String> deployments() {
        return client().apps().deployments().inAnyNamespace().list().getItems()
                .stream().map(d -> d.getMetadata().getName())
                .collect(Collectors.toList());
    }

    protected void logEvents() {
        client().events().inAnyNamespace().watch(new Watcher<io.fabric8.kubernetes.api.model.Event>() {
            @Override
            public void eventReceived(Action action, io.fabric8.kubernetes.api.model.Event resource) {
                log.info("event " + action.name() + " " + resource.toString());
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                log.info("Watcher close due to " + cause);
            }
        });
    }

    public List<String> images() {
        return pods().stream()
            .flatMap(p -> p.getSpec().getContainers().stream())
            .map(c -> c.getImage())
            .collect(Collectors.toList());
    }

    public List<Pod> pods() {
        return client().pods().inNamespace(namespace).list().getItems();
    }

//    public void waitUntilAllPodsInNamespaceAreReady(int timeoutInSeconds) {
//
//        await().atMost(5, TimeUnit.SECONDS)
//            .until(() -> pods().size() > 0);
//
//
//        log.info("Wating {} seconds for pods in namespace {} to be ready", timeoutInSeconds, namespace);
//        await().atMost(timeoutInSeconds, TimeUnit.SECONDS)
//            .ignoreException(K8sClusterException.class)
//            .until(() -> pods().parallelStream().map(p -> waitUntilReady(1, p) != null).allMatch(isReady -> true));
//    }

//    public HasMetadata waitUntilReady(int timeoutInSeconds, HasMetadata resources) {
//        waitUntilReady(timeoutInSeconds, resources);
//        return resources;
//    }

    private Namespace createTestNamespaceAndWait(int timeoutInSeconds, String name) {

        log.info("Create namespace {}", name);
        Namespace namespace = new NamespaceBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withLabels(ImmutableMap.of("is-test-namespace", "true"))
                .endMetadata()
                .build();
        namespace = client().namespaces().withName(name).createOrReplace(namespace);

        await().atMost(Duration.of(timeoutInSeconds, ChronoUnit.SECONDS)).ignoreExceptions()
            .until(() -> client().namespaces().withName(name).get() != null);

        //TODO: namespace is stuck in terminating until metrics server comes online
//        namespace = client().namespaces().withName(name).edit().editSpec().withFinalizers(new ArrayList<>()).endSpec().done();
        return namespace;
    }

    private void deleteNamespaceNoWait(String namespace) {
        log.info("Delete {}", namespace);
        try {
            client().namespaces().withName(namespace).cascading(true).withGracePeriod(0).delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteNamespaceAndWait(int timeoutInSeconds, String namespace) {
        log.info("Delete namespace {}", namespace);
        await().atMost(Duration.of(timeoutInSeconds, ChronoUnit.SECONDS))
            .until(() -> {
                try {
                    Boolean deleteStatus = client().namespaces().withName(namespace).cascading(true).withGracePeriod(0).delete();
                    return deleteStatus == null? true: !deleteStatus;
                } catch (KubernetesClientException e) {
                    return false;
                }
            });
    }

    public CompletableFuture<Pod> createPod(String name, String image) {
        return createPod(name, image, null);
    }

    public CompletableFuture<Pod> createPod(String name, String image, String profile) {
        EnvVarBuilder envVarBuilder = new EnvVarBuilder()
                .withName("SPRING_PROFILES_ACTIVE");

        if (profile != null ) {
            envVarBuilder.withValue(profile).build();
        } else {
            envVarBuilder
                .withName("SPRING_PROFILES_ACTIVE")
                .withNewValueFrom()
                    .withNewConfigMapKeyRef("cluster-config", "cluster-name", true)
                .endValueFrom();
        }

        log.info("Create pod {}, image {}", name, image);
        Pod pod = new PodBuilder()
                .withNewMetadata().withName(name).addToLabels("app", name).endMetadata()
                .withNewSpec()
                    .addNewContainer()
                        .withName(name)
                        .withImage(image)
                        .addToEnv(envVarBuilder.build())
                    .endContainer()
                .endSpec()
                .build();

          client().pods().inNamespace(namespace).createOrReplace(pod);

          log.info("Wait until pod {} are ready", name);
          return CompletableFuture.supplyAsync(() -> {
                    try {
                        return client().pods().inNamespace(namespace).withName(name).waitUntilReady(60, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {throw new K8sClusterException(e);}
                });
    }

    public CompletableFuture<Deployment> createDeployment(String name, String image) {
            log.info("createDeployment. name: {}, image: {}", name, image);
            Deployment deployment = new DeploymentBuilder()
                    .withNewMetadata().withName(name).endMetadata()
                    .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector().addToMatchLabels("app", name).endSelector()
                        .withNewTemplate().withNewMetadata().addToLabels("app", name).endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withName(name)
                            .withImage(image)
    //                    .addNewPort()
    //                        .withContainerPort(8080).endPort()
//                            .withNewLivenessProbe()
//                                .withNewHttpGet()
//                                    .withPath("/probe/liveness")
//                                    .withPort(new IntOrString(8080))
//                                    .withScheme("HTTP")
//                                .endHttpGet()
//                                .withInitialDelaySeconds(5)
//                                .withPeriodSeconds(1)
//                                .withFailureThreshold(60)
//                            .endLivenessProbe()
                        .endContainer()
                    .endSpec().endTemplate().endSpec().build();

            client().apps().deployments().inNamespace(namespace).createOrReplace(deployment);

            log.info("Wait until deployment {} are ready", name);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return client().apps().deployments().inNamespace(namespace).withName(name).waitUntilReady(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {throw new K8sClusterException(e);}
            });
    }

    public Service createService(String name, int port, int targetPort) {
        return createService(name, port, targetPort, null);
    }

    public Service createService(String forApp, int port, int targetPort, Integer nodePort) {
        log.info("createService. name: {}, port: {}, targetPort: {}, nodePort", forApp, port, targetPort, nodePort);
        Service service = new ServiceBuilder()
                .withNewMetadata().withName(forApp +"-svc").endMetadata()
                .withNewSpec()
                    .withSelector(ImmutableMap.of("app", forApp))
                    .addNewPort()
                        .withName("http")
                        .withProtocol("TCP")
                        .withPort(port)
                        .withTargetPort(new IntOrString(targetPort))
                        .withNodePort(nodePort)
                    .endPort()
                    .withType(nodePort != null? "NodePort":"ClusterIP")
                .endSpec()
                .build();

        return client().services().inNamespace(namespace).createOrReplace(service);
    }

    public CompletableFuture<List<HasMetadata>> createFromSpecifications(Path dir, String name, String image, int nodePort) {
        log.info("create from path: {}", dir);
        try (Stream<Path> walk = walk(dir)) {
           List<HasMetadata> hasMetadatas = walk.map(p -> p.toFile())
                   .sorted().parallel()
                   .filter(f -> f.isFile() && f.getName().matches(".*\\.ya?ml"))
                   .flatMap(f -> createOrReplace(f, name, image, nodePort).stream())
                   .collect(Collectors.toList());

           log.info("Wait until items {} are ready", name);
           return CompletableFuture.supplyAsync(() -> {
               try {
                   return client().resourceList(new KubernetesListBuilder().withItems(hasMetadatas).build())
                           .inNamespace(namespace)
                           .waitUntilReady(60, TimeUnit.SECONDS);
               } catch (InterruptedException e) {throw new K8sClusterException(e);}
           });
        } catch (IOException e) {
            throw new K8sClusterException(e);
        }
    }

    protected List<HasMetadata> createOrReplace(File f, String name, String image, int nodePort) {
        try {
            String spec = new String(Files.readAllBytes(f.toPath()));
            //String spec = Files.readString(f.toPath());
            spec = spec.replace("<name>", name)
                    .replace("<namespace>", namespace)
                    .replace("<image>", image)
                    .replace("<nodeport>", ""+nodePort);

            return client().load(new ByteArrayInputStream(spec.getBytes())).get().stream()
                    .map(h -> client().resource(h).createOrReplace() )
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new K8sClusterException(e);
        }
    }

    protected Path copyToResourcesdDir(String source, String destination, boolean executable) {
        try {
            Path src = Paths.get(source);
            if (!src.toFile().exists() ) {
                src = Paths.get(getClass().getClassLoader().getResource(source).toURI());
            }
            Path dest = Paths.get(RESOURCES_DIR + destination);
            log.info("Copy {} to {}", src, dest);
            copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
//            dest.toFile().deleteOnExit();
            dest.toFile().setExecutable(executable);
            return dest;
        } catch (URISyntaxException | IOException e) {
            log.warn("a", e);
            throw new K8sClusterException(e);
        } catch (Exception e) {
            log.warn("b", e);
            throw new K8sClusterException(e);
        }
    }

    boolean isConnected(KubernetesClient client) {
        try {
            return client.namespaces().withName("default").get() != null;
        } catch (Exception e) {
            log.info("Unable to connect to {}, {}, {}", client.getMasterUrl(), client.getConfiguration().getErrorMessages(), e.getMessage());
            return false;
        }
    }

    public static K8sClusterBuilder builder() {
        return new K8sClusterBuilder();
    }

    public static class K8sClusterBuilder {
        private boolean local = true;
        private String namespace = "k8s-test";
        private boolean failOnExistingTestNamespace = true;
        private File configFile;

//        public K8sClusterBuilder asLocal() {
//            this.local = true;
//            return this;
//        }

        public K8sClusterBuilder config(File configFile) {
            this.local = false;
            this.configFile = configFile;
            return this;
        }

        public K8sClusterBuilder testNamespace(String name) {
            this.namespace = name;
            return this;
        }

        public K8sClusterBuilder failOnExistingTestNamespace(boolean b) {
            this.failOnExistingTestNamespace = b;
            return this;
        }

        public K8sCluster build() {
            if (local) {
                return new LocalK3sCluster(namespace, failOnExistingTestNamespace);
            }
            return new CustomCluster(namespace, failOnExistingTestNamespace, configFile);
        }

    }
}
