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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.util.Tuple;

import java.util.*;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.INSTSET_TID;
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

    @Override
    public boolean isResolved(boolean nested) {
        return super.isResolved(nested);
    }

    protected boolean checkPattern(final Obj obj) {
        if (obj.isInst()) {
            if (!obj.tid().test(this.pattern())) {
                LOG.warn("migrating obj at {{b}}%s{{X}} to respective instset: %s", obj.tid(), obj);
                return false;
            }
        } else if (null != obj.vid() && !obj.vid().test(this.pattern())) {
            LOG.warn("migrating obj at {{b}}%s{{X}} to respective instset: %s", obj.tid(), obj);
            return false;
        }
        return true;
    }

    protected boolean checkDepth(final Obj obj, final fURI requiredPrefix) {
        if (false && null != obj.tid() && !obj.tid().hasPrefix(requiredPrefix.toString())) {
            LOG.warn("obj at %s must have prefix at %s: (ignoring) %s", obj.tid(), requiredPrefix, obj);
            return false;
        }
        return true;
    }

    private boolean old = true;

    public AbstractInstSet(final boolean noDocq) {
        super(new LinkedHashMap<>(), mutableMap(
                uri(Tokens.PATTERN), uri(ALL)), INSTSET_TID, null);
    }

    public AbstractInstSet(final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(), mutableMap(
                uri(Tokens.PATTERN), uri(vid.extend(ALL))), tid, vid);
        this.at(uri(Tokens.QSTRING), lst(QCollection.docQ()), MUTABLE);
        if (Router.loaded()) {
            this.sugars().forEach(mParser::addSugar);
            this.consts().forEach(c -> Router.global().registerRedirect(f(c.vid().name()), c.vid()));
            this.types().stream().filter(t -> null != t.vid()).forEach(t -> Router.global().registerRedirect(f(t.vid().name()), t.vid()));
            this.insts().forEach(t -> Router.global().registerRedirect(f(t.tid().name()), t.tid().basePath()));
            /// //////////////////////////////////////////////////////////////////////////////////////////////
            this.types().forEach(t -> {
                if (null != t.vid()) {
                    if (t.vid().test(this.pattern)) this.write(t.vid(), t);
                    else Router.writeToSpace(t.vid(), t);
                } else if (null != t.tid()) {
                    if (t.tid().test(this.pattern)) this.write(t.tid(), t);
                    else Router.writeToSpace(t.tid(), t);
                }
            });
            Router.writeToSpace(NOOBJ_TID, NOOBJ_TYPE); // every inst set must have a noobj so it can operate independently of /m/inst
            this.consts().forEach(c -> {
                if (c.vid().test(this.pattern)) this.write(c.vid(), c.vid(null));
                else Router.writeToSpace(c.vid(), c.vid(null));
            });
            this.insts().forEach(i -> {
                if (i.tid().test(this.pattern)) this.write(i.tid(), i);
                else Router.writeToSpace(i.tid(), i);
            });
            this.rewrites().forEach(r -> {
                if (r.tid().test(this.pattern)) this.write(r.tid(), r);
                else Router.writeToSpace(r.tid(), r);
            });
        }
    }

    public AbstractInstSet(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(new LinkedHashMap<>(), jvm, tid, vid);
        this.at(uri(Tokens.QSTRING), lst(QCollection.docQ()), MUTABLE);
        this.sugars().forEach(mParser::addSugar);
        old = false;
    }

    @Override
    public void setup() {
        this.jvm().forEach((k, v) -> {
            if (k.equals(uri(CONST))) {
                v.lstValue().stream()
                        //.filter(this::checkPattern)
                        .filter(c -> checkDepth(c, this.tid.extend(CONST)))
                        .forEach(c -> {
                            if (!checkPattern(c))
                                Router.writeToSpace(c);
                            else {
                                CONST_TABLE.put(c.vid(), c);
                                Router.global().registerRedirect(f(c.vid().name()), c.vid());
                            }
                        });
            } else if (k.equals(uri(TYPE))) {
                v.lstValue().stream()
                        //.filter(this::checkPattern)
                        .filter(t -> checkDepth(t, this.tid))
                        .forEach(t -> {
                            if (!checkPattern(t))
                                Router.writeToSpace(t);
                            else {
                                TYPE_TABLE.put(t.vid(), t.as());
                                Router.global().registerRedirect(f(t.vid().name()), t.vid());
                            }
                        });
            } else if (k.equals(uri(INST))) {
                v.lstValue().stream()
                        //.filter(this::checkPattern)
                        .filter(r -> checkDepth(r, this.tid.extend(INST)))
                        .forEach(i -> {
                            if (!checkPattern(i))
                                Router.writeToSpace(i.tid(), i);
                            else {
                                INST_TABLE.computeIfAbsent(i.tid().basePath(), kk -> new LinkedHashSet<>()).add(i.as());
                                Router.global().registerRedirect(f(i.tid().name()), i.tid().basePath());
                            }
                        });
            } else if (k.equals(uri(REWRITE))) {
                v.lstValue().stream()
                        //.filter(this::checkPattern)
                        .filter(r -> checkDepth(r, this.tid.extend(INST).extend(REWRITE)))
                        .forEach(r -> {
                            if (!checkPattern(r))
                                Router.writeToSpace(r.tid(), r);
                            else
                                REWRITE_TABLE.put(r.tid(), r.as());
                        });
            } else if (k.equals(uri(SUGAR))) {
                LOG.warn("unable to load sugar: %s", v);
                //v.lstValue().forEach(s -> Router.global().addSugar(s.as()));
            }
        });
    }

    @Override
    public Set<Obj> consts() {
        return old ? new LinkedHashSet<>() : new LinkedHashSet<>(CONST_TABLE.values());
    }

    @Override
    public Set<Type> types() {
        return old ? new LinkedHashSet<>() : new LinkedHashSet<>(TYPE_TABLE.values());
    }

    @Override
    public Set<Inst> insts() {
        return old ? new LinkedHashSet<>() : new LinkedHashSet<>(INST_TABLE.values().stream().flatMap(Set::stream).toList());
    }

    @Override
    public Set<Inst> rewrites() {
        return old ? new LinkedHashSet<>() : new LinkedHashSet<>(REWRITE_TABLE.values());
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
        if (Objects.equals(this.vid, pattern))
            return this;
        return Q.Helper.processPreRead(this.qs(), pattern).orElseGet(() -> {
            final Obj result = objs(INST_TABLE.entrySet()
                    .stream()
                    .filter(kv -> kv.getKey().test(pattern.basePath().asNode()))
                    .flatMap(kv -> kv.getValue().stream())
                    .filter(i -> !pattern.hasDom() || i.dom().tid().test(pattern.dom().big()))
                    .filter(i -> !pattern.hasRng() || i.rng().tid().test(pattern.rng().big()))
                    .map(i -> pattern.isNode() ? i : rel(i.tid().toUri(), i)))
                    .append(objs(TYPE_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))))
                    .append(objs(REWRITE_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))))
                    .append(objs(CONST_TABLE.entrySet()
                            .stream()
                            .filter(kv -> kv.getKey().test(pattern.asNode()))
                            .map(kv -> pattern.isNode() ?
                                    kv.getValue() :
                                    rel(kv.getKey().toUri(), kv.getValue()))));
            return Q.Helper.processPostRead(this.qs(), vid, result).orElse(result);
        });
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return Q.Helper.processPreWrite(this.qs(), vid, obj).orElseGet(
                () -> Q.Helper.processQlessWrite(this.qs(), vid, obj).orElseGet(
                        () -> {
                            if (obj.isInst()) {
                                final Inst inst = obj.as();
                                if (inst.dom().isCode()) {
                                    REWRITE_TABLE.put(inst.tid(), inst);
                                } else {
                                    Router.global().registerRedirect(f(vid.name()), vid);
                                    INST_TABLE.computeIfAbsent(inst.tid().basePath(), k -> new LinkedHashSet<>()).add(inst);
                                }
                            } else if (obj.isType()) {
                                TYPE_TABLE.put(vid, obj.as());
                            } else if (obj.isNoObj()) {
                                final Set<Inst> insts = INST_TABLE.get(vid.basePath());
                                insts.removeIf(i -> i.tid().test(vid));
                            } else {
                                CONST_TABLE.put(vid, obj);
                                // throw MTronException.of("inst set %s can only store insts, types, and rewrites: {{r}}!{{/r}} %s", this.simpeToString(), obj);
                            }
                            return Q.Helper.processPostWrite(this.qs(), vid, obj).orElse(obj);
                        }));
    }

    public Set<Tuple.Triplet<Tuple.Pair<String, String>, List<fURI>, Integer>> sugars() {
        return Set.of();
    }
    
    
    /*@Override
    public AbstractInstSet clone(){
        return this;
    }

    @Override
    public AbstractInstSet clone(final Object jvm, final fURI tid, final fURI vid){
        return this;
    }*/

}
