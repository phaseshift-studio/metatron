/*
 * Metatron: A Distributed Computing Language and Virtual Machine
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

package studio.phaseshift.metatron.isa.m.type.impl;

import org.jspecify.annotations.NonNull;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjectMap<K, V> extends AbstractMap<Obj, Obj> implements Map<Obj, Obj> {

    protected final Map<K, V> map;

    public ObjectMap() {
        this.map = new LinkedHashMap<>();
    }

    public ObjectMap(final Map<K, V> map) {
        this.map = map;

    }

    private final Obj toObj(final Object object) {
        return MObjFactory.of().toObj(object);
    }

    public Obj put(final Obj key, final Obj value) {
        return MObjFactory.of().toObj(this.map.put(key.jvm(), value.jvm()));
    }

    public V getRaw(final K key) {
        return this.map.get(key);
    }

    public V putRaw(final K key, final V value) {
        return this.map.put(key, value);
    }

    @Override
    public Obj getOrDefault(final Object key, final Obj defaultValue) {
        final V value = this.map.get((K) ((Obj) key).jvm());
        return null == value ? defaultValue : MObjFactory.of().toObj(value);
    }

    public V computeRaw(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return this.map.compute(key, remappingFunction);
    }

    public V getOrDefaultRaw(final K key, final V defaultValue) {
        return this.map.getOrDefault(key, defaultValue);
    }

    public Obj get(final Obj key) {
        return MObjFactory.of().toObj(this.map.get((K) key.jvm()));
    }

    public Obj remove(final Obj key) {
        return MObjFactory.of().toObj(this.map.remove((K) key.jvm()));
    }

    public int size() {
        return this.map.size();
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public Rec toRec() {
        return rec(this);
    }

    @Override
    public @NonNull Set<Entry<Obj, Obj>> entrySet() {
        return this.map.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(MObjFactory.of().toObj(e.getKey()), MObjFactory.of().toObj(e.getValue())))
                .collect(Collectors.toSet());
    }
}
