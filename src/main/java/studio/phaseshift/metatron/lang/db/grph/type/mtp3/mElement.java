package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedElement;
import studio.phaseshift.metatron.lang.core.m.type.Lst;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.core.m.type.Uri;
import studio.phaseshift.metatron.lang.db.grph.type.RElement;

import java.util.Iterator;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class mElement implements Element, WrappedElement<RElement> {

    protected final RElement base;


    protected Obj stringToUriLabels(final String[] labels) {
        final Lst uris = lst();
        for (final String label : labels) {
            uris.addMutate(uri(label));
        }
        return uris;
    }

    /*protected String[] uriToStringLabels(final Uri[] labels) {
        final Uri[] uris = new Uri[labels.length];
        for (int i = 0; i < labels.length; i++) {
            uris[i] = uri(labels[i]);
        }
        return uris;
    }*/

    protected mElement(final RElement base) {
        this.base = base;
    }

    @Override
    public Object id() {
        return this.getBaseElement().id();
    }

    @Override
    public String label() {
        return this.getBaseElement().label().toString();
    }

    @Override
    public Graph graph() {
        return null;
    }

    @Override
    public void remove() {

    }

    @Override
    public <V> Iterator<? extends Property<V>> properties(final String... propertyKeys) {
        return null;
    }

    @Override
    public RElement getBaseElement() {
        return this.base;
    }
}
