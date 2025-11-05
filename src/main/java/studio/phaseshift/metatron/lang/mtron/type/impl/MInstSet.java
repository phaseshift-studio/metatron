/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.mtron.type.impl;

import org.petitparser.parser.Parser;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.msys.Router;
import studio.phaseshift.metatron.lang.mtron.type.Inst;
import studio.phaseshift.metatron.lang.mtron.type.InstSet;
import studio.phaseshift.metatron.lang.mtron.type.Obj;
import studio.phaseshift.metatron.lang.mtron.type.Type;
import studio.phaseshift.metatron.space.MSpace;

import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.lang.mtron.type.impl.MUri.uri;

public abstract class MInstSet extends MSpace<Map<fURI, Set<? extends Obj>>> implements InstSet {

    protected static final String[] EMPTY_STRING_ARRAY = new String[0];

    /// /////////////////////////////////////////////////////////////////////////////////////////

    protected final Map<fURI, Set<Inst>> INST_TABLE = new LinkedHashMap<>();
    protected final Map<fURI, Type> TYPE_TABLE = new LinkedHashMap<>();
    protected final Map<fURI, Obj> CONST_TABLE = new LinkedHashMap<>();
    protected final Map<fURI, Inst> REWRITE_TABLE = new LinkedHashMap<>();

    public MInstSet(final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(), Map.of(uri("pattern"), uri(tid.extend(fURI.ALL))), tid.extend(fURI.ALL), tid, vid);
        if (!this.pattern.equals(f("+/#")) && Router.loaded() && !(this instanceof Router))
            Router.global().addSpace(this.pattern, this);
        this.types().forEach(t -> this.write(t.tid(), t));
        this.consts().forEach(c -> this.write(c.vid(), c));
        this.insts().forEach(i -> this.write(i.tid(), i));
        this.rewrites().forEach(r -> this.write(r.tid(), r));
        this.types().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid()));
        this.consts().forEach(t -> Router.global().registerRewrite(f(t.vid().name()), t.vid()));
        this.insts().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid().basePath()));

    }

    @Override
    public Set<Obj> consts() {
        return new LinkedHashSet<>();
    }

    @Override
    public Set<Type> types() {
        return new LinkedHashSet<>();
    }

    @Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>();
    }

    @Override
    public Set<Inst> rewrites() {
        return new LinkedHashSet<>();
    }

    @Override
    public Map<fURI, Set<? extends Obj>> sjvm() {
        return Map.of(
                f("consts"), this.consts(),
                f("types"), this.types(),
                f("insts"), this.insts(),
                f("rewrites"), this.rewrites());
    }

    @Override
    public Obj read(final fURI vid) {
        if (Objects.equals(this.tid, vid))
            return this;
        final fURI bigvid = vid.big();
        return objs(INST_TABLE.entrySet()
                .stream()
                .filter(kv -> kv.getKey().bimatches(bigvid.basePath()))
                .flatMap(kv -> kv.getValue().stream())
                .filter(i -> !bigvid.hasDom() || i.dom().tid().bimatches(bigvid.dom()))
                .filter(i -> !bigvid.hasRng() || i.rng().tid().bimatches(bigvid.rng()))
                .map(i -> i))
                .append(objs(TYPE_TABLE.entrySet().stream().filter(kv -> kv.getKey().matches(bigvid)).map(Map.Entry::getValue)))
                .append(objs(CONST_TABLE.entrySet().stream().filter(kv -> kv.getKey().matches(bigvid)).map(Map.Entry::getValue)));
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        if (obj.isInst()) {
            final Inst inst = obj.as();
            if (inst.dom().isCode()) {
                REWRITE_TABLE.put(inst.tid(), inst);
            } else {
                Router.global().registerRewrite(fURI.of(vid.name()), vid);
                INST_TABLE
                        .computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashSet<>())
                        .add(inst);
            }
        } else if (obj.isType()) {
            TYPE_TABLE.put(vid, obj.as());
        } else {
            CONST_TABLE.put(vid, obj);
            // throw MTronException.of("inst set %s can only store insts, types, and rewrites: {{r}}!{{/r}} %s", this.simpeToString(), obj);
        }
        return obj;
    }

    public List<Parser> sugars() {
        return List.of();
    }

}
