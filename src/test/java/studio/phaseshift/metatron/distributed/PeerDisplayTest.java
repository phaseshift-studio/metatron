/*
 * metatron: a distributed virtual machine and language
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.distributed;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the display half of the tooling: the {@code TestReport}-style table
 * {@link PeerCluster#peerAnalysis} and {@link PeerCluster#peerProfile} print.
 * <p>
 * It boots the VM, unlike {@link PeerClusterInfraTest}, because the renderer is built from VM components —
 * {@code TableWidget} reads its own fields and {@code Graphitty} rewrites colour markers — and without a booted
 * VM the table comes back empty rather than wrong, which is exactly the kind of silent nothing a display test
 * exists to catch.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PeerDisplayTest extends AbstractMetatronTest {

    @Test
    public void testTableIsTitledAndBordered() throws Exception {
        final String table = PeerCluster.table("peer analysis", List.of("idx", "port", "live", "*</n/#>"),
                List.of(List.of("{{C}}1{{X}}", "9001", "{{G}}yes{{X}}", "[a=>1,b=>10]"),
                        List.of("{{C}}2{{X}}", "9002", "{{R}}no{{X}}", "{{R}}-[peer down]-{{X}}")));
        // the rendered artifact is the point of this test: leave it where it can be looked at
        final java.nio.file.Path out = java.nio.file.Path.of(System.getProperty("user.dir"), "target", "peer-display.txt");
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, table);
        assertFalse(table.isBlank(), "the table renders at all");
        assertTrue(table.contains("peer analysis"), "the title is rendered: " + table);
        assertTrue(table.contains("│"), "cell dividers are drawn: " + table);
        assertTrue(table.indexOf("┌") >= 0 && table.indexOf("└") > table.indexOf("┌"),
                "top and bottom borders are drawn in order: " + table);
    }

    /**
     * A coloured cell must not be wider than a plain one: {@code TableWidget} measures cells through
     * {@code Highlighter}, and if that ever stops holding, every column in every diagnostic table shifts.
     */
    @Test
    public void testColouredCellDoesNotShiftColumns() {
        final String plain = PeerCluster.table("t", List.of("a", "b"), List.of(List.of("xyz", "1")));
        final String coloured = PeerCluster.table("t", List.of("a", "b"), List.of(List.of("{{G}}xyz{{X}}", "1")));
        assertFalse(plain.isBlank(), "the table renders at all");
        assertEquals(visualWidths(plain), visualWidths(coloured),
                "a coloured cell must occupy the same width as a plain one:\n" + plain + coloured);
    }

    /** per-line display width, ANSI colour escapes ignored */
    private static List<Integer> visualWidths(final String rendered) {
        return rendered.lines().map(line -> line.replaceAll("\u001B\\[[0-9;]*m", "").length()).toList();
    }
}
