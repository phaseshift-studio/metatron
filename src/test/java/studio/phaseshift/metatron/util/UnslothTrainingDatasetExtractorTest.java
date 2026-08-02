package studio.phaseshift.metatron.util;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;

public class UnslothTrainingDatasetExtractorTest extends AbstractMetatronTest {
    @Test
    public void runExtractor() {
        UnslothTrainingDatasetExtractor.main(new String[]{});
    }
}
