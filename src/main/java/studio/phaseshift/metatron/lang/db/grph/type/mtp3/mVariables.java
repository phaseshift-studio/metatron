package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.util.wrapped.WrappedVariables;
import studio.phaseshift.metatron.lang.core.m.type.ObjFactory;
import studio.phaseshift.metatron.lang.core.m.type.Rec;
import studio.phaseshift.metatron.lang.core.m.type.impl.MObjFactory;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mVariables implements Graph.Variables, WrappedVariables<Rec> {

    protected final Rec variables;

    public mVariables(final Rec variables) {
        this.variables = variables;
    }

    @Override
    public Set<String> keys() {
        return this.variables.elements().map(r -> r.first().uriValue().toString()).collect(Collectors.toSet());
    }

    @Override
    public <R> Optional<R> get(final String key) {
        return Optional.ofNullable(this.variables.at(key).orElse(null));
    }

    @Override
    public void set(final String key, final Object value) {
        this.variables.jvm().put(uri(key), MObjFactory.of().create(value));
    }

    @Override
    public void remove(final String key) {
        this.variables.jvm().remove(uri(key));
    }

    @Override
    public Rec getBaseVariables() {
        return this.variables;
    }
}
