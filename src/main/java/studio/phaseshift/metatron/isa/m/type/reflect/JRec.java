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

package studio.phaseshift.metatron.isa.m.type.reflect;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.impl.MObj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.GraphittyLogger;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instLambda;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class JRec<OBJECT> extends MObj implements Rec {

    protected final GraphittyLogger LOG = Graphitty.log(this);
    protected OBJECT sjvm;

    protected record MethodAnnotation(Method method, JRecElement annotation) {
    }

    protected record FieldAnnotation(Field field, JRecElement annotation) {
    }

    public JRec(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super();
        this.self(jvm, tid.big(), vid);
        this.sjvm = (OBJECT) this;
        JInst.Helper.processInst(this);
        Obj.Helper.objCheckAndSave(this);
    }

    @Override
    public Rec clone(final Object jvm, final fURI tid, final fURI vid) {
        final JRec<OBJECT> clone = super.clone(jvm, tid, vid);
        clone.sjvm = (OBJECT) clone;
        return this;
    }

    @Override
    public <O extends Obj> O at(final Obj key) {
        final Obj o = objs(Stream.concat(Stream.concat(this.findField(key).stream().map(f -> {
            f.field().setAccessible(true);
            final O temp = (O) JObjFactory.single().toObj(MTronException.wrap(() -> f.field().get(this.sjvm)), f(f.annotation().rng()), null);
            this.jvm().put(key, temp);
            return temp;
        }), this.findMethod(key).stream().map(m -> {
            try {
                if (m.annotation().mimic() == JRecElement.Mimic.FIELD) {
                    return this.invokeMethod(this.sjvm, this.createArgs(m), m);
                } else {
                    final fURI jrecKey = f(m.annotation().key());
                    final fURI jinstTID = this.vid() == null ? jrecKey : this.vid().extend(jrecKey);
                    return instC(jinstTID.dom(f(m.annotation.dom())).rng(f(m.annotation.rng())), this.createArgs(m),
                            (lhs, inst) -> this.invokeMethod(this.sjvm, inst.args().asLst(), m));
                }
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        })), Rec.super.at(key).stream()));
        return (O) o;
    }

    @Override
    public Rec at(final Obj key, final Obj value) {
        try {
            this.jvm().put(key, value);
            this.findField(key).forEach(f -> MTronException.wrap(() -> {
                f.field().setAccessible(true);
                f.field().set(this.sjvm, Obj.class.isAssignableFrom(f.field().getType()) ? value : value.jvm());
            }));
            return this;
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    @Override
    public Map<Obj, Obj> jvm() {
        final Map<Obj, Obj> base = (Map<Obj, Obj>) (null == this.jvm ? new LinkedHashMap<>() : this.jvm);
        if (null == this.sjvm)
            return base;
        final Map<Obj, Obj> temp = new LinkedHashMap<>();
        this.findField(uri("#")).forEach(f -> {
                f.field().setAccessible(true);
                temp.put(uri(f.annotation().key()),
                        JObjFactory.single().toObj(MTronException.wrap(() -> f.field().get(this.sjvm)), JRecElement.Helper.getRng(f.annotation()), null));
        });
        this.findMethod(uri("#")).forEach(m -> {
            if (m.annotation().mimic() == JRecElement.Mimic.FIELD) {
                if (m.method.getParameterCount() != 0)
                    throw MTronException.of("method %s has parameters but is marked to mimic field", m.method.getName());
                temp.put(uri(m.annotation().key()), this.invokeMethod(this.sjvm, this.createArgs(m), m));
            } else {
                final fURI jrecKey = f(m.annotation().key());
                //final fURI jinstTID = this.vid() == null ? jrecKey : this.vid().extend(jrecKey);
                temp.put(uri(m.annotation().key()),
                        instLambda(f(m.annotation.dom()), f(m.annotation.rng()),
                                (lhs, inst) -> this.invokeMethod(this.sjvm, this.createArgs(m), m)));
            }
        });
        temp.putAll(base);
        return temp;
    }

    /**
     * Read the source-of-truth JVM for this object.  If the object has a
     * persistent address ({@link #vid()}), the latest state is fetched from
     * the space via {@link Router#global() Router.global().read(vid())}.
     * Otherwise the local construction-time JVM is returned.
     *
     * <p>Subclasses should call this before every rendering pass so that
     * {@code >>=} mutations from mtron are always visible.
     */
    protected final Map<Obj, Obj> jvmRead() {
        if (this.vid() == null) return this.jvm();
        try {
            final Obj fresh = Router.global().read(this.vid());
            return fresh.isRec() ? fresh.jvm() : this.jvm();
        } catch (final Exception e) {
            return this.jvm(); // fallback: space unavailable
        }
    }

    /**
     * Write a single field to the persistent store with {@code >>=}-style
     * merge semantics: the current state is read from the space, the field
     * is merged in, and the whole rec is written back.  Other fields are
     * never clobbered.
     *
     * <p>If the object has no {@link #vid()}, writes to the local JVM via
     * {@link #at(Obj, Obj)} so that {@link #jvmRead()} still picks it up.
     */
    protected final void jvmWrite(final Obj key, final Obj value) {
        if (this.vid() == null) {
            // Ephemeral object — write directly to the base map so
            // jvmRead() (which returns the merged jvm()) picks it up.
            ((Map<Obj, Obj>) this.jvm).put(key, value);
            this.findField(key).forEach(f -> MTronException.wrap(() -> {
                f.field().setAccessible(true);
                f.field().set(this.sjvm, Obj.class.isAssignableFrom(f.field().getType()) ? value : value.jvm());
            }));
            return;
        }
        try {
            final Obj current = Router.global().read(this.vid());
            final Map<Obj, Obj> merged = new LinkedHashMap<>(current.isRec() ? current.jvm() : this.jvm());
            merged.put(key, value);
            Router.global().write(this.vid(), rec(merged, current.tid(), this.vid()));
        } catch (final Exception e) {
            // Fallback: direct sub-path write
            Router.global().write(this.vid().extend(key.uriValue()), value);
        }
    }

    // ── typed value extractors for jvmRead() ──────────────────────

    /** Extract a {@code str::T} value from a JVM map. */
    protected static String jvmStr(final Map<Obj, Obj> jvm, final String key) {
        return jvmStr(jvm, uri(key));
    }
    protected static String jvmStr(final Map<Obj, Obj> jvm, final Obj key) {
        final Obj o = jvm.get(key);
        return (o != null && o.isStr()) ? o.strValue() : "";
    }

    /** Extract a {@code bool::T} value from a JVM map (defaults to {@code true} if absent). */
    protected static boolean jvmBool(final Map<Obj, Obj> jvm, final String key) {
        return jvmBool(jvm, uri(key));
    }
    protected static boolean jvmBool(final Map<Obj, Obj> jvm, final Obj key) {
        final Obj o = jvm.get(key);
        return o == null || !o.isBool() || o.boolValue();
    }

    /** Extract an {@code int::T} value from a JVM map. */
    protected static int jvmInt(final Map<Obj, Obj> jvm, final String key, final int fallback) {
        return jvmInt(jvm, uri(key), fallback);
    }
    protected static int jvmInt(final Map<Obj, Obj> jvm, final Obj key, final int fallback) {
        final Obj o = jvm.get(key);
        return (o != null && o.isInt()) ? o.asInt().intValue().intValue() : fallback;
    }

    /** Extract a body (str or lst-of-str) from a JVM map into a list of lines. */
    protected static List<String> jvmBody(final Map<Obj, Obj> jvm, final String key) {
        return jvmBody(jvm, uri(key));
    }
    protected static List<String> jvmBody(final Map<Obj, Obj> jvm, final Obj key) {
        final List<String> lines = new java.util.ArrayList<>();
        final Obj b = jvm.get(key);
        if (b != null && !b.isNoObj()) {
            if (b.isStr()) java.util.Arrays.asList(b.strValue().replace("\\n", "\n").split("\n", -1)).forEach(lines::add);
            else b.stream().filter(Obj::isStr).forEach(o -> lines.add(o.strValue()));
        }
        return lines;
    }

    @Override
    public Rec self(Object jvm, fURI tid, fURI vid) {
        return super.self(jvm, tid, vid);
    }

    protected final List<FieldAnnotation> findField(final Obj key) {
        String javaName = key.isStr() ? key.strValue() : key.isUri() ? key.uriValue().toString() : null;
        if (null == javaName)
            return List.of();
        boolean allWildcard = javaName.endsWith("#");
        final String finalJavaName = javaName;
        return Arrays.stream(this.getClass().getDeclaredFields())
                .filter(f -> f.getAnnotation(JRecElement.class) != null)
                .filter(f -> f.getAnnotation(JRecElement.class).key().equals(finalJavaName) || allWildcard || f.getName().equals(finalJavaName))
                .map(f -> new FieldAnnotation(f, f.getAnnotation(JRecElement.class))).toList();
    }

    protected final List<MethodAnnotation> findMethod(final Obj key) {
        String javaName = key.isStr() ? key.strValue() : key.isUri() ? key.uriValue().toString() : null;
        if (null == javaName)
            return List.of();
        boolean allWildcard = javaName.endsWith("#");
        final String finalJavaName = javaName;
        return Arrays.stream(this.getClass().getDeclaredMethods())
                .filter(m -> m.getAnnotation(JRecElement.class) != null)
                .filter(m -> (m.getAnnotation(JRecElement.class).key().equals(finalJavaName) || allWildcard || m.getName().equals(finalJavaName)))
                .map(m -> new MethodAnnotation(m, m.getAnnotation(JRecElement.class))).toList();
    }

    protected final Obj invokeMethod(final Object source, final Lst args, final MethodAnnotation methodAnnotation) {
        try {
            final Method m = methodAnnotation.method();
            if (m.getParameterCount() == 0)
                return JObjFactory.single().toObj(MTronException.wrap(() -> m.invoke(source)), JRecElement.Helper.getRng(methodAnnotation.annotation()), null);
            else if (m.getParameterCount() == 1)
                return JObjFactory.single().toObj(MTronException.wrap(() -> m.invoke(source, args.at(0))), JRecElement.Helper.getRng(methodAnnotation.annotation()), null);
            else if (m.getParameterCount() == 2)
                return JObjFactory.single().toObj(MTronException.wrap(() -> m.invoke(source, args.at(0), args.at(1))), JRecElement.Helper.getRng(methodAnnotation.annotation()), null);
            else if (m.getParameterCount() == 3)
                return JObjFactory.single().toObj(MTronException.wrap(() -> m.invoke(source, args.at(0), args.at(1), args.at(2))), JRecElement.Helper.getRng(methodAnnotation.annotation()), null);
            return JObjFactory.single().toObj(MTronException.wrap(() -> m.invoke(source, args)), JRecElement.Helper.getRng(methodAnnotation.annotation()), null);
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    protected final Lst createArgs(final MethodAnnotation methodAnnotation) {
        if (methodAnnotation.method().getParameterCount() == 0)
            return lst();
        if (methodAnnotation.method().getParameterCount() == 1)
            return lst(
                    JObjFactory.single().createType(methodAnnotation.method.getParameterTypes()[0]));
        if (methodAnnotation.method().getParameterCount() == 2)
            return lst(
                    JObjFactory.single().createType(methodAnnotation.method.getParameterTypes()[0]),
                    JObjFactory.single().createType(methodAnnotation.method.getParameterTypes()[1]));
        if (methodAnnotation.method().getParameterCount() == 3)
            return lst(
                    JObjFactory.single().createType(methodAnnotation.method.getParameterTypes()[0]),
                    JObjFactory.single().createType(methodAnnotation.method.getParameterTypes()[1]),
                    JObjFactory.single().createType(methodAnnotation.method.getParameterTypes()[2]));
        return lst(T(ALL), INST_TYPE);
    }

}
