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
            this.findField(key).forEach(f -> MTronException.wrap(() -> f.field().set(this.sjvm, value.jvm())));
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
        this.findField(uri("#")).forEach(f ->
                temp.put(uri(f.annotation().key()),
                        JObjFactory.single().toObj(MTronException.wrap(() -> f.field().get(this.sjvm)), JRecElement.Helper.getRng(f.annotation()), null)));
        this.findMethod(uri("#")).forEach(m -> {
            if (m.annotation().mimic() == JRecElement.Mimic.FIELD) {
                if (m.method.getParameterCount() != 0)
                    throw MTronException.of("method %s has parameters but is marked to mimic field", m.method.getName());
                temp.put(uri(m.annotation().key()), this.invokeMethod(this.sjvm, this.createArgs(m), m));
            } else {
                final fURI jrecKey = f(m.annotation().key());
                final fURI jinstTID = this.vid() == null ? jrecKey : this.vid().extend(jrecKey);
                temp.put(uri(m.annotation().key()),
                        instLambda(f(m.annotation.dom()), f(m.annotation.rng()),
                                (lhs, inst) -> this.invokeMethod(this.sjvm, this.createArgs(m), m)));
            }
        });
        temp.putAll(base);
        return temp;
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
