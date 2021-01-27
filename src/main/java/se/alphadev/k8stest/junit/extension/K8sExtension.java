package se.alphadev.k8stest.junit.extension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import se.alphadev.k8stest.K8sCluster;

@Slf4j
public class K8sExtension implements TestInstancePostProcessor, AfterAllCallback {

    private static final Namespace STORE_NAMESPACE = Namespace.create(K8sExtension.class);
    private static final String TEST_CLUSTER = "testCluster";

    @Override
    public void postProcessTestInstance(final Object testInstance, final ExtensionContext context) {
        context.getTestClass()
            .orElseThrow(() -> new ExtensionConfigurationException("K8sExtension is only supported for test classes"));

        K8sCluster cluster = setupK8sCluster(testInstance, context);

        ExtensionContext.Store store = context.getStore(STORE_NAMESPACE);
        store.put(TEST_CLUSTER, cluster);
    }

    protected K8sCluster setupK8sCluster(final Object testInstance, final ExtensionContext context) {
        try {
            List<Field> annotatedFields = findK8sTestClusterAnnotatedFields(context.getTestClass().get());
            if (!annotatedFields.isEmpty()) {
                for (Field field : annotatedFields) {
                    field.setAccessible(true);
                    K8sCluster cluster = (K8sCluster) field.get(testInstance);
                    cluster.setup();
                    return cluster;
                }
            }
            throw new ExtensionConfigurationException("");
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new ExtensionConfigurationException("", e);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(STORE_NAMESPACE);
        K8sCluster cluster = store.get(TEST_CLUSTER, K8sCluster.class);
        if (cluster != null)
            cluster.tearDown();
    }

    private List<Field> findK8sTestClusterAnnotatedFields(Class<?> testClass) {
        return ReflectionUtils.findFields(
                testClass,
                annotatedWithK8sTestCluster(),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN);
    }

    private static Predicate<Field> annotatedWithK8sTestCluster() {
        return field -> {
            boolean isAnnotatedWithK8sTestCluster = AnnotationSupport.isAnnotated(field, K8sTestCluster.class);
            if (isAnnotatedWithK8sTestCluster) {
                if (!se.alphadev.k8stest.K8sCluster.class.isAssignableFrom(field.getType())) {
                    throw new ExtensionConfigurationException(String.format("FieldName: %s must be of type K8sCluster", field.getName()));
                }
                return true;
            }
            return false;
        };
    }

}
