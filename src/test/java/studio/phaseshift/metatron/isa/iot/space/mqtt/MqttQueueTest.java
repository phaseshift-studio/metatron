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

package studio.phaseshift.metatron.isa.iot.space.mqtt;

import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * The mqtt queue — a blocking list and a readable list at once: offers are answered in order, pending pairs are
 * visible through the {@code Lst} while they wait, and a blocked take wakes on the next offer.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MqttQueueTest extends AbstractMetatronTest {

    @Test
    public void offersAreHandedOutInOrder() throws InterruptedException {
        final MqttQueue queue = new MqttQueue();
        final Obj firstPub = lst(uri(f("/foundry/press/1/temp")), jnt(907));
        final Obj secondPub = lst(uri(f("/foundry/press/2/temp")), jnt(911));
        queue.offer(firstPub);
        queue.offer(secondPub);
        assertEquals(firstPub, queue.takeNext());
        assertEquals(secondPub, queue.takeNext());
    }

    @Test
    public void pendingPairsStayReadableThroughTheLst() {
        final MqttQueue queue = new MqttQueue();
        final Obj firstPub = lst(uri(f("/foundry/press/1/temp")), jnt(907));
        queue.offer(firstPub);
        final Obj secondPub = lst(uri(f("/foundry/press/2/temp")), jnt(911));
        queue.offer(secondPub);
        // the queue is a Lst: while a pair waits for the dispatcher it is still readable in order
        assertEquals(2, queue.jvm().size());
        assertEquals(firstPub, queue.jvm().get(0));
        assertEquals(secondPub, queue.jvm().get(1));
    }

    @Test
    public void aBlockedTakeWakesOnTheNextOffer() throws InterruptedException {
        final MqttQueue queue = new MqttQueue();
        final AtomicReference<Obj> taken = new AtomicReference<>();
        final Thread taker = new Thread(() -> {
            try {
                taken.set(queue.takeNext());
            } catch (final InterruptedException ignored) {
                // waking is what is under test
            }
        });
        taker.start();
        Thread.sleep(200);
        assertNull(taken.get(), "an empty queue must not answer a take");
        final Obj firstPub = lst(uri(f("/foundry/press/1/temp")), jnt(907));
        queue.offer(firstPub);
        taker.join(2_000);
        assertEquals(firstPub, taken.get());
    }
}
