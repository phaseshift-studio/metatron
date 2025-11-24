package studio.phaseshift.metatron.lang.db.grph.type.mtp3;

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;

import java.lang.reflect.InvocationTargetException;

import static studio.phaseshift.metatron.furi.fURI.f;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class mFeatures implements Graph.Features {

    protected final mGraph graph;

    public mFeatures(final mGraph graph) {
        this.graph = graph;
    }

    @Override
    public GraphFeatures graph() {
        return new GraphF();
    }

    @Override
    public VertexFeatures vertex() {
        return new VertexF();
    }

    @Override
    public EdgeFeatures edge() {
        return new EdgeF();
    }

    @Override
    public boolean supports(Class<? extends FeatureSet> featureClass, String feature) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return Graph.Features.super.supports(featureClass, feature);
    }

    public static class GraphF implements Graph.Features.GraphFeatures {

        @Override
        public boolean supportsComputer() {
            return false;
        }

        @Override
        public boolean supportsPersistence() {
            return false;
        }

        @Override
        public boolean supportsConcurrentAccess() {
            return false;
        }

        @Override
        public boolean supportsTransactions() {
            return false;
        }

        @Override
        public boolean supportsThreadedTransactions() {
            return false;
        }

        @Override
        public boolean supportsIoRead() {
            return true;
        }

        @Override
        public boolean supportsIoWrite() {
            return false;
        }

        @Override
        public boolean supportsOrderabilitySemantics() {
            return true;
        }

        @Override
        public boolean supportsServiceCall() {
            return true;
        }

        @Override
        public Graph.Features.VariableFeatures variables() {
            return new Graph.Features.VariableFeatures() {
                @Override
                public boolean supportsVariables() {
                    return true;
                }
            };
        }
    }

    public class VertexF extends ElementF implements Graph.Features.VertexFeatures {

        @Override
        public VertexProperty.Cardinality getCardinality(final String key) {
            return VertexProperty.Cardinality.list;
        }

        @Override
        public boolean supportsAddVertices() {
            return true;
        }

        @Override
        public boolean supportsRemoveVertices() {
            return true;
        }

        @Override
        public boolean supportsMultiProperties() {
            return false;
        }

        @Override
        public boolean supportsDuplicateMultiProperties() {
            return true;
        }

        @Override
        public boolean supportsMetaProperties() {
            return false;
        }

        @Override
        public boolean supportsUpsert() {
            return false;
        }

        @Override
        public Graph.Features.VertexPropertyFeatures properties() {
            return new VertexPropertyF();
        }
    }

    public class EdgeF extends ElementF implements EdgeFeatures {

        @Override
        public boolean supportsAddEdges() {
            return true;
        }

        @Override
        public boolean supportsRemoveEdges() {
            return true;
        }

        @Override
        public boolean supportsUpsert() {
            return true;
        }

        @Override
        public EdgePropertyFeatures properties() {
            return new EdgePropertyF();
        }
    }

    public static class EdgePropertyF implements EdgePropertyFeatures {

        @Override
        public boolean supportsProperties() {
            return true;
        }
    }

    public class VertexPropertyF implements Graph.Features.VertexPropertyFeatures {

        @Override
        public boolean supportsNullPropertyValues() {
            return false;
        }

        @Override
        public boolean supportsRemoveProperty() {
            return true;
        }

        @Override
        public boolean supportsUserSuppliedIds() {
            return false;
        }

        @Override
        public boolean supportsNumericIds() {
            return true;
        }

        @Override
        public boolean supportsStringIds() {
            return true;
        }

        @Override
        public boolean supportsUuidIds() {
            return true;
        }

        @Override
        public boolean supportsCustomIds() {
            return true;
        }

        @Override
        public boolean supportsAnyIds() {
            return true;
        }

        @Override
        public boolean willAllowId(final Object id) {
            return f(id.toString()).matches(graph.getBaseGraph().pattern());
        }
    }

    public class ElementF implements ElementFeatures {

        @Override
        public boolean supportsNullPropertyValues() {
            return false;
        }

        @Override
        public boolean supportsAddProperty() {
            return true;
        }

        @Override
        public boolean supportsRemoveProperty() {
            return true;
        }

        @Override
        public boolean supportsUserSuppliedIds() {
            return true;
        }

        @Override
        public boolean supportsNumericIds() {
            return true;
        }

        @Override
        public boolean supportsStringIds() {
            return true;
        }

        @Override
        public boolean supportsUuidIds() {
            return true;
        }

        @Override
        public boolean supportsCustomIds() {
            return false;
        }

        @Override
        public boolean supportsAnyIds() {
            return false;
        }

        @Override
        public boolean willAllowId(Object id) {
            return f(id.toString()).matches(graph.getBaseGraph().pattern());
        }
    }
}
