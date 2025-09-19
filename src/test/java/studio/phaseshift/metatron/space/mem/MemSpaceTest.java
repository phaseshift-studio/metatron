package studio.phaseshift.metatron.space.mem;

import org.junit.jupiter.api.BeforeAll;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.space.SpaceTest;

public class MemSpaceTest extends SpaceTest {

    @BeforeAll
    public static void setup() {
        BootLoader.load();

    }

    public MemSpaceTest() {
        this.space = new MemSpace(fURI.of("#"), fURI.of("/mnt/mem"));
    }
}
