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

package studio.phaseshift.metatron.isa.m.mach.type.network;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;

/**
 * A FORKED NODE LIVES NO LONGER THAN ITS PARENT. {@link PeerSpawn} reaps its nodes when a frame pops or the machine
 * closes, but a parent that died without reaping (a hard kill, a crash, a rebuild's restart) left its nodes serving
 * their ports and announcing themselves, and the next spawn at those ports collided. A node watches the pid its parent
 * names ({@link MachNode#PARENT}) and exits with it. A stand-in process plays the parent here, since the test VM cannot
 * die mid-test.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MachNodeTest extends AbstractMetatronTest {

    /**
     * The parent dies after the node is serving: the node exits, and its port with it.
     */
    @Test
    public void testANodeExitsWithItsParent() throws Exception {
        final Process parent = new ProcessBuilder("sleep", "300").start();
        final fURI authority = f("ws://localhost:" + generatePort() + "/reaped");
        final Path log = Files.createTempFile("metatron-node-reap-", ".log");
        final Process node = PeerSpawn.fork(authority, log, null, parent.pid());
        try {
            PeerSpawn.awaitReady(node, log, authority);
            parent.destroy();
            assertTrue(node.waitFor(30, TimeUnit.SECONDS), "the node outlived its parent:\n" + Files.readString(log));
        } finally {
            node.destroyForcibly();
            parent.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }

    /**
     * The parent is gone before the node starts: the node exits at once, never mistaking the process it was handed to
     * for a parent.
     */
    @Test
    public void testANodeWhoseParentIsGoneExitsAtOnce() throws Exception {
        final Process parent = new ProcessBuilder("true").start();
        parent.waitFor();
        final fURI authority = f("ws://localhost:" + generatePort() + "/orphaned");
        final Path log = Files.createTempFile("metatron-node-orphan-", ".log");
        final Process node = PeerSpawn.fork(authority, log, null, parent.pid());
        try {
            assertTrue(node.waitFor(30, TimeUnit.SECONDS),
                    "a node whose parent was gone kept running:\n" + Files.readString(log));
        } finally {
            node.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }
}
