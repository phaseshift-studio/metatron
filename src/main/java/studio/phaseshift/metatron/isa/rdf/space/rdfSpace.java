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

package studio.phaseshift.metatron.isa.rdf.space;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class rdfSpace { /*<R extends Repository> extends AbstractSpace<R> {

    public static fURI SPARQL_INST_TID = RDF_INST_TID.extend(SPARQL);
    public static fURI RDF_SPACE_TID = RDF_ISA_SPACE_TID.extend("rdfspace");
    protected ObjSerializer<?> serializer;

    public static <R extends Repository> rdfSpace<R> of(final Map<Obj, Obj> config, final fURI vid) {
        final fURI host = config.getOrDefault(uri(HOST), uri("http://localhost:8080")).uriValue();
        Graphitty.log(rdfSpace.class).debug("rdfSpace host: %s", host);
        if (host.scheme().equals(HTTP)) {
            if (host.path().getLast().equals("sparql"))
                return new rdfSpace<>((R) new SPARQLRepository(host.toString()), new LinkedHashMap<>(config), RDF_SPACE_TID, vid);
            else
                return new rdfSpace<>((R) new HTTPRepository(host.toString()), new LinkedHashMap<>(config), RDF_SPACE_TID, vid);
        } else
            return new rdfSpace<>((R) new SailRepository(new MemoryStore()), new LinkedHashMap<>(config), RDF_SPACE_TID, vid);
    }

    public rdfSpace(final R sjvm, final Map<Obj, Obj> config, final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
        if (!this.sjvm().isInitialized())
            this.sjvm().init();
    }

    private String getPrefixHeader() {
        return String.join("\n", this.routes().entrySet().stream().map(e -> "PREFIX %s <%s>".formatted(e.getKey().uriValue().toString(), e.getValue().uriValue().toString())).toList());
    }

    public Function<fURI, Iterator<IdObj>> directReader() {
        return vid -> {
            final String query = this.getPrefixHeader() + "\n" + "SELECT ?y ?z WHERE { %s ?y ?z. } LIMIT 10".formatted(Space.Helper.routeFromSpace(vid, this.routes()).toString());
            LOG.info("executing sparql query: %s", query);
         
            final TupleQueryResult result = this.sjvm().getConnection().prepareTupleQuery(query).evaluate();
            LOG.info("result: %s", result.getBindingNames());
            final List<IdObj> results = new ArrayList<>();
            result.forEach(bindingSet -> {
                final Value y = bindingSet.getValue("y");
                final Value z = bindingSet.getValue("z");
                results.add(IdObj.of(ObjRDFSerializer.single().read(Stream.of(z))));
            });
            LOG.info("result2: %s", results);
            result.close();
            return results.iterator();
        };
    }*/
}
