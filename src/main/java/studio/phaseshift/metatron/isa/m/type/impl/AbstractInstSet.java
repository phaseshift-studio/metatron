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

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.DocQ;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.sys.type.Router;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.NOOBJ_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.NOOBJ_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

public abstract class AbstractInstSet extends AbstractSpace<Map<fURI, Set<? extends Obj>>> implements InstSet {

    protected static final String[] EMPTY_STRING_ARRAY = new String[0];

    /// /////////////////////////////////////////////////////////////////////////////////////////

    protected final Map<fURI, Set<Inst>> INST_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());
    protected final Map<fURI, Type> TYPE_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());
    protected final Map<fURI, Obj> CONST_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());
    protected final Map<fURI, Inst> REWRITE_TABLE = Collections.synchronizedMap(new LinkedHashMap<>());

    public AbstractInstSet(final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(), mutableMap(
                uri(Tokens.PATTERN), uri(tid.extend(fURI.ALL)),
                uri(Tokens.Q), lst(new DocQ())), tid, vid);
        if (Router.loaded()) {
            //if (!this.pattern.equals(f("+/#")) && !(this instanceof Router))
            //    Router.global().addSpace(this);
            this.types().forEach(t -> {
                if (null != t.vid()) {
                    if (t.vid().matches(this.pattern)) this.write(t.vid(), t);
                    else Router.writeToSpace(t.vid(), t);
                } else if (null != t.tid()) {
                    if (t.tid().matches(this.pattern)) this.write(t.tid(), t);
                    else Router.writeToSpace(t.tid(), t);
                }
            });
            Router.writeToSpace(NOOBJ_TID, NOOBJ_TYPE); // every inst set must have a noobj so it can operate independently of /m/inst
            this.consts().forEach(c -> {
                if (c.vid().matches(this.pattern)) this.write(c.vid(), c);
                else Router.writeToSpace(c.vid(), c);
            });
            this.insts().forEach(i -> {
                if (i.tid().matches(this.pattern)) this.write(i.tid(), i);
                else Router.writeToSpace(i.tid(), i);
            });
            this.rewrites().forEach(r -> {
                if (r.tid().matches(this.pattern)) this.write(r.tid(), r);
                else Router.writeToSpace(r.tid(), r);
            });
            /// //////////////////////////////////////////////////////////////////////////////////////////////
            this.consts().forEach(t -> Router.global().registerRewrite(f(t.vid().name()), t.vid()));
            this.types().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid()));
            this.insts().forEach(t -> Router.global().registerRewrite(f(t.tid().name()), t.tid().basePath()));
        }
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
    public void close() {
       /* this.types().forEach(t -> Router.global().registerRewrite(fURI.of(t.tid().name()), null));
        this.consts().forEach(c -> Router.global().registerRewrite(fURI.of(c.vid().name()), null));
        this.insts().forEach(i -> Router.global().registerRewrite(fURI.of(i.tid().name()),null));
        this.rewrites().forEach(r -> Router.global().registerRewrite(fURI.of(r.tid().name()), null));
        this.types().forEach(t -> Router.writeToSpace(t.tid(),noobj()));
        this.consts().forEach(c -> Router.writeToSpace(c.vid(),noobj()));
        this.insts().forEach(i -> Router.writeToSpace(i.tid(),noobj()));
        this.rewrites().forEach(r -> Router.writeToSpace(r.tid(),noobj()));*/
    }

    @Override
    public Obj read(final fURI pattern) {
        if (Objects.equals(this.tid, pattern))
            return this;
        return Q.Helper.processPreRead(this.qs(), this.vid, pattern).orElse(
                objs(INST_TABLE.entrySet()
                        .stream()
                        .filter(kv -> kv.getKey().bimatches(pattern.basePath().asNode()))
                        .flatMap(kv -> kv.getValue().stream())
                        .filter(i -> !pattern.hasDom() || i.dom().tid().bimatches(pattern.dom().big()))
                        .filter(i -> !pattern.hasRng() || i.rng().tid().bimatches(pattern.rng().big()))
                        .map(i -> pattern.isNode() ? i : rel(i.tid().toUri(), i)))
                        .append(objs(TYPE_TABLE.entrySet()
                                .stream()
                                .filter(kv -> kv.getKey().matches(pattern.asNode()))
                                .map(kv -> pattern.isNode() ?
                                        kv.getValue() :
                                        rel(kv.getKey().toUri(), kv.getValue()))))
                        .append(objs(CONST_TABLE.entrySet()
                                .stream()
                                .filter(kv -> kv.getKey().matches(pattern.asNode()))
                                .map(kv -> pattern.isNode() ?
                                        kv.getValue() :
                                        rel(kv.getKey().toUri(), kv.getValue())))));

    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return Q.Helper.processPreWrite(this.qs(), this.vid, vid, obj).orElseGet(() -> {
            if (obj.isInst()) {
                final Inst inst = obj.as();
                if (inst.dom().isCode()) {
                    REWRITE_TABLE.put(inst.tid(), inst);
                } else {
                    Router.global().registerRewrite(fURI.of(vid.name()), vid);
                    INST_TABLE.computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashSet<>()).add(inst);
                }
            } else if (obj.isType()) {
                TYPE_TABLE.put(vid, obj.as());
            } else if (obj.isNoObj()) {
                final Set<Inst> insts = INST_TABLE.get(vid.basePath());
                insts.removeIf(i -> i.tid().matches(vid));
            } else {
                CONST_TABLE.put(vid, obj);
                // throw MTronException.of("inst set %s can only store insts, types, and rewrites: {{r}}!{{/r}} %s", this.simpeToString(), obj);
            }
            return obj;
        });
    }

    public Set<Tuple.Triplet<Tuple.Pair<String, String>, List<fURI>, Integer>> sugars() {
        return Set.of();
    }

}
