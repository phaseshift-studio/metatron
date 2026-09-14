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

package studio.phaseshift.metatron.isa.m.type;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.impl.MType;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.router.NoObjRouter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;

/**
 * the type-structure cache: memoization hits, invalidation on type-path
 * writes, no spurious invalidation on unrelated writes, and the router
 * rebind clearing a stale registry.
 */
public class TypeGraphTest extends AbstractMetatronTest {

    private static final fURI PROBE = f("/m/tgraphProbe");

    @BeforeAll
    public static void setup() {
        InstSet.importInstSet(f("/m/math"), f("math"));
    }

    @AfterEach
    public void cleanTypeGraph() {
        try {
            Router.writeToSpace(PROBE, noobj());
        } finally {
            TypeGraph.global().clear();
        }
    }

    @Test
    public void testMemoHit() {
        final TypeGraph graph = TypeGraph.global();
        final AtomicInteger resolves = new AtomicInteger(0);
        final Supplier<Type> resolve = () -> {
            resolves.incrementAndGet();
            return MType.T(f("int"));
        };
        final TypeGraph.Key key = new TypeGraph.Key(f("int").big(), null, null, null);
        final Type first = graph.memo(key, resolve);
        final Type second = graph.memo(key, resolve);
        assertSame(first, second, "the memo must hand back the cached instance: first= " + first + " second= " + second);
        assertEquals(1, resolves.get(), "a cached resolution must not re-run the resolver: resolves=" + resolves.get());
    }

    @Test
    public void testInvalidationOnTypeWrite() {
        final TypeGraph graph = TypeGraph.global();
        final AtomicInteger resolves = new AtomicInteger(0);
        // the parsed type carries its own (registered) path -- resolve and
        // watch through that path so the write below aliases a watched entry
        final Obj type = ObjmtronSerializer.parse("int::T@tgraphProbe");
        final fURI typePath = type.vid();
        final Supplier<Type> resolve = () -> {
            resolves.incrementAndGet();
            return MType.T(typePath);
        };
        final TypeGraph.Key key = new TypeGraph.Key(typePath.big(), null, null, null);
        final Type before = graph.memo(key, resolve);
        assertEquals(1, resolves.get(), "first resolution must run the resolver");
        // register the type at the watched path -- the write must invalidate
        Router.writeToSpace(typePath, type);
        final Type after = graph.memo(key, resolve);
        assertEquals(2, resolves.get(), "a write to the type path must force a re-resolution: resolves=" + resolves.get());
        assertNotEquals(before, after, "the invalidated resolution must see the written type: before=" + before + " after=" + after);
    }

    @Test
    public void testNoSpuriousInvalidationOnUnrelatedWrite() {
        final TypeGraph graph = TypeGraph.global();
        final AtomicInteger resolves = new AtomicInteger(0);
        final Supplier<Type> resolve = () -> {
            resolves.incrementAndGet();
            return MType.T(f("int"));
        };
        final TypeGraph.Key key = new TypeGraph.Key(f("int").big(), null, null, null);
        graph.memo(key, resolve);
        // an unrelated data write must not disturb the memo
        Router.writeToSpace(f("/m/tgraphUnrelated"), jnt(42));
        graph.memo(key, resolve);
        assertEquals(1, resolves.get(), "an unrelated write must not invalidate the memo: resolves=" + resolves.get());
    }

    @Test
    public void testConcurrentResolveDuringWrites() throws InterruptedException {
        final TypeGraph graph = TypeGraph.global();
        final AtomicInteger resolves = new AtomicInteger(0);
        final Supplier<Type> resolve = () -> {
            resolves.incrementAndGet();
            return MType.T(PROBE);
        };
        final TypeGraph.Key key = new TypeGraph.Key(PROBE.big(), null, null, null);
        final int workers = 4;
        final CountDownLatch ready = new CountDownLatch(workers);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(workers);
        final AtomicInteger errors = new AtomicInteger(0);
        for (int i = 0; i < workers; i++) {
            new Thread(() -> {
                try {
                    ready.countDown();
                    go.await();
                    for (int j = 0; j < 25; j++) {
                        graph.memo(key, resolve);
                        if (j % 4 == 0)
                            Router.writeToSpace(f("/m/tgraphHammer"), jnt(j));
                    }
                } catch (final Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        ready.await();
        go.countDown();
        done.await();
        assertEquals(0, errors.get(), "concurrent resolve/write must not throw: errors=" + errors.get());
        // quiesced: the memo must have settled into hits for the same key
        final Type finalState = graph.memo(key, resolve);
        final int settlesAt = resolves.get();
        graph.memo(key, resolve);
        assertEquals(settlesAt, resolves.get(), "after the storm the memo must be stable and hit: delta=" + (resolves.get() - settlesAt));
        assertNotNull(finalState);
    }

    @Test
    public void testRouterRebindClearsStaleRegistry() {
        final TypeGraph graph = TypeGraph.global();
        final AtomicInteger resolves = new AtomicInteger(0);
        final Supplier<Type> resolve = () -> {
            resolves.incrementAndGet();
            return MType.T(f("real"));
        };
        final TypeGraph.Key key = new TypeGraph.Key(f("real").big(), null, null, null);
        graph.memo(key, resolve);
        assertEquals(1, resolves.get(), "first resolution must run the resolver");
        final Router saved = Router.global();
        try {
            // a different router instance stands in for a re-boot / reload:
            // the stale registry must be dropped and the re-resolution forced
            BootLoader.ROUTER = NoObjRouter.single();
            graph.memo(key, resolve);
            assertEquals(2, resolves.get(), "a new router instance must clear the memo: resolves=" + resolves.get());
        } finally {
            BootLoader.ROUTER = saved;
        }
        assertNotNull(BootLoader.ROUTER);
    }
}
