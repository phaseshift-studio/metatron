package studio.phaseshift.metatron.util;

import studio.phaseshift.metatron.lang.obj.Objs;

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
