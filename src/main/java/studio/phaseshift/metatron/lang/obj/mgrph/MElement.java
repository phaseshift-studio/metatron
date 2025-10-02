package studio.phaseshift.metatron.lang.obj.mgrph;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.obj.Obj;
import studio.phaseshift.metatron.util.ObjUtil;

import java.util.Iterator;

public abstract class MElement implements Element, Obj {

    protected final Element element;

    protected MElement(final Element element) {
        this.element = element;

    }

    @Override
    public Object id() {
        return this.vid().hashCode();
    }

    @Override
    public String label() {
        return this.element.label();
    }

    @Override
    public Graph graph() {
        return MGraph.of(this.element.graph());
    }

    @Override
    public void remove() {
        this.element.remove();
    }

    /*@Override
    public <V> Iterator<? extends Property<V>> properties(String... propertyKeys) {
        return this.element.properties(propertyKeys);
    }*/

    @Override
    public Element value() {
        return this.element;
    }


    @Override
    public MElement clone(final Object value, final fURI tid, final fURI vid) {
        return this; // TODO: fix
    }

    @Override
    public String toString() {
        return ObjUtil.objToString(this);
    }

    @Override
    public int hashCode() {
        return ObjUtil.objHashCode(this);
    }

    @Override
    public boolean equals(final Object other) {
        return ObjUtil.objEquals(this, other);
    }

    @Override
    public <V> Iterator<V> values(final String... propertyKeys) {
        return MProperty.makeValues((Iterator) this.properties(propertyKeys));
    }
}
