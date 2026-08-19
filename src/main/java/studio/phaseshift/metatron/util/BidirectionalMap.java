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

package studio.phaseshift.metatron.util;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class BidirectionalMap<K, V> extends AbstractMap<K, V> {
    private final Map<K, V> keyToValue = new HashMap<>();
    private final Map<V, K> valueToKey = new HashMap<>();
    private final Object MUTEX = new Object();

    @Override
    public V put(K key, V value) {
        synchronized (MUTEX) {
            valueToKey.put(value, key);
            return keyToValue.put(key, value);
        }
    }

    @Override
    public void clear() {
        synchronized (MUTEX) {
            this.keyToValue.clear();
            this.valueToKey.clear();
        }
    }

    @Override
    public @NotNull Set<Entry<K, V>> entrySet() {
        return this.keyToValue.entrySet();
    }


    public V getValue(K key) {
        return this.keyToValue.get(key);
    }

    public K getKey(V value) {
        return valueToKey.get(value);
    }
}
