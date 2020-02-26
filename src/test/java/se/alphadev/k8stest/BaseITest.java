package se.alphadev.k8stest;

import static java.lang.System.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public class BaseITest {

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        out.println("*** "+ testInfo.getDisplayName() +" ***");
    }

}
