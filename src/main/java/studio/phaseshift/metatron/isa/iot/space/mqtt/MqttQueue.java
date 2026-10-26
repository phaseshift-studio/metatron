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

import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.impl.MLst;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

import static studio.phaseshift.metatron.Tokens.LST_TID;

/**
 * MQTT_QUEUE — a {@code Lst} that is also a blocking queue: its backing list <em>is</em> the pending
 * {@code lst[pub, uri]} pairs, so the queue is metatron-readable (health, debugging) at the address that holds it. A
 * {@link Semaphore} gates {@link #takeNext()} on the count of pending pairs, and {@link #offer} appends. The backing
 * store is the {@link CopyOnWriteArrayList} handed to {@link MLst} — never a separate field — so the {@code Lst}
 * introspection and the queue share one list.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class MqttQueue extends MLst {

    private final Semaphore available = new Semaphore(0);

    public MqttQueue() {
        super(new CopyOnWriteArrayList<>(), LST_TID, null);
    }

    @Override
    public List<Obj> jvm() {
        return super.jvm();
    }

    /**
     * Enqueue one {@code lst[pub, uri]} pair, releasing the permit that wakes a blocked {@link #takeNext()}.
     */
    public void offer(final Obj pair) {
        this.jvm().add(pair);
        this.available.release();
    }

    /**
     * Block for the next pair and remove it. Named {@code takeNext} to avoid colliding with {@code Obj.take()}.
     */
    public Obj takeNext() throws InterruptedException {
        this.available.acquire();
        return this.jvm().remove(0);
    }
}
