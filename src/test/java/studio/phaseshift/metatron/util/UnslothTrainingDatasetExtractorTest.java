package studio.phaseshift.metatron.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;

@Disabled
public class UnslothTrainingDatasetExtractorTest extends AbstractMetatronTest {
    @Test
    public void runExtractor() {
        UnslothTrainingDatasetExtractor.main(new String[]{});
    }
}
