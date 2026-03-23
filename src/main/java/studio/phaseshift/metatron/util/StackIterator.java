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

package studio.phaseshift.metatron.util;

import studio.phaseshift.metatron.isa.m.type.Objs;

import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.Stack;

public final class StackIterator<T> implements Iterator<T> {

    private final Stack<Iterator<T>> itty;

    public StackIterator(final Iterator<T> itty) {
        this.itty = new Stack<>();
        this.itty.push(itty);
    }

    private Stack<Iterator<T>> align() {
        if (!this.itty.empty() && !this.itty.peek().hasNext())
            this.itty.pop();
        return this.itty;
    }

    @Override
    public boolean hasNext() {
        return !this.align().empty();
    }

    @Override
    public T next() {
        try {
            final T next = this.align().peek().next();
            if (next instanceof Objs) {
                this.itty.push((Iterator<T>) ((Objs) next).iterator());
                return this.next();
            } else {
                return next;
            }
        } catch (final EmptyStackException e) {
            throw FastNoSuchElementException.instance();
        }
    }
}
