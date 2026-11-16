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

package studio.phaseshift.metatron.isa;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.WILD_ONE;
import static studio.phaseshift.metatron.isa.m.mInstSet.ALL_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec0;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * A space over a database whose collections (tables, document collections, graph labels) have types. A collection's
 * type is DECLARED (by the space's schema route, or by a type written to the collection's path) or INFERRED from the
 * records the store holds. One algorithm infers them all: {@link Helper}. A space supplies only what its store knows.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface SchemaSpace extends Space {

    /**
     * THE COLLECTIONS THE STORE HOLDS, named as the store names them, read from the store: a collection created past
     * the space is one of them.
     */
    Stream<String> collections();

    /**
     * WHAT A COLLECTION'S RECORDS HOLD, as the predicate of its inferred type: each field keyed by its name, required
     * when every record holds it and {@code {?}} otherwise, valued by the LCD of the types of its values; the rec's tid
     * is the records' own type. Empty when the store holds no such collection. A store folds its records with
     * {@link Helper#fields(Stream)}.
     */
    Rec fields(final String collection);

    default InstSet schema() {
        return Helper.schema(this);
    }

    /**
     * The type of a collection — declared, or inferred — for a collection-level read ({@code /db/collection}); a
     * wildcard collection answers the type of every collection.
     */
    default Iterator<IdObj> resolveCollectionSchema(final String collectionName) {
        final boolean wildcard = ALL.name().equals(collectionName) || WILD_ONE.name().equals(collectionName);
        return (wildcard ? Helper.types(this) : Stream.ofNullable(Helper.type(this, collectionName)))
                .map(type -> IdObj.of(type.vid(), type)).iterator();
    }

    @Override
    default void close() {
        this.at(uri(SCHEMA)).<InstSet>ifPresent(InstSet::close);
    }

    /**
     * ONE SCHEMA-INFERENCE ALGORITHM FOR EVERY DATABASE SPACE (Marko, 2026-11-16).
     * <ul>
     *   <li>THE SPACE'S OWN INSTSET HOLDS THE SCHEMA, and the collection of the DataPath is the type: a collection's
     *   type is at {@code <space>/instset/<collection>}.</li>
     *   <li>A FIELD IS REQUIRED WHEN EVERY RECORD HOLDS IT, counted over every record, not a sample (a SQL NULL is an
     *   absent field); a field's type is the LCD of its values' types ({@code Type.Helper.findLCD}).</li>
     *   <li>A DECLARED TYPE IS DECLARED: a type the schema route holds for the collection, or one written to the
     *   collection's path, is never inferred, and a write that breaks it is refused.</li>
     *   <li>A WRITE TO AN UNDECLARED COLLECTION WIDENS ITS TYPE, so the type holds every record: a new field is
     *   {@code {?}}, a field the record leaves out becomes {@code {?}}, a value of another type widens the field to
     *   the LCD.</li>
     *   <li>DISCOVERY IS LAZY: a collection is inferred when its type is first read.</li>
     * </ul>
     * An inferred type's vid is its address; a declared type keeps its own, which is how the two are told apart.
     * An inferred type is reached by its address or its collection, not by its bare name: a graph element carries its
     * label as its tid, and a label name that resolved to the inferred type would refuse the write that widens it.
     * <p>
     * The space's {@code infer} rec switches the defaults: {@code infer=>[required=>false]} makes every field
     * {@code {?}}, {@code widen=>false} refuses a write an inferred type does not hold, and {@code discover=>eager}
     * infers every collection when the space mounts.
     */
    class Helper {

        /**
         * The space's schema instset, made the first time it is asked for. A deliberate read of a collection's type
         * there infers it, and a read of every type infers every collection the store holds; a fallback read
         * ({@code readDeclared}) never does, since a machine reaching for instruction memory must not cause work.
         */
        public static InstSet schema(final SchemaSpace space) {
            final Obj held = space.at(uri(SCHEMA));
            if (held.isInstSet())
                return held.as();
            synchronized (space) {
                final Obj raced = space.at(uri(SCHEMA));
                if (raced.isInstSet())
                    return raced.as();
                final fURI vid = space.vid().extend(INSTSET);
                final InstSet schema = new AbstractInstSet(mutableMap(uri(PATTERN), uri(vid.extend(ALL))), INSTSET_TID, vid) {
                    @Override
                    public Obj read(final fURI pattern) {
                        if (pattern.hasPrefix(this.vid())) {
                            if (pattern.hasPattern())
                                space.collections().forEach(collection -> infer(space, collection));
                            else if (pattern.segments().size() == this.vid().segments().size() + 1)
                                infer(space, pattern.name());
                        }
                        return super.read(pattern);
                    }
                };
                Machine.current().memory().addSpace(schema);
                schema.setup();
                space.at(uri(SCHEMA), schema, MUTABLE);
                return schema;
            }
        }

        /**
         * The type of every collection: each one the store holds, and each one declared at the space's instset.
         */
        public static Stream<Type> types(final SchemaSpace space) {
            return Stream.concat(space.collections().map(collection -> type(space, collection)), space.schema().types().stream())
                    .filter(Objects::nonNull)
                    .distinct();
        }

        /**
         * The type of a collection: its declared type, else its inferred one (inferred now if the store holds the
         * collection and nothing has read its type yet); null when the space knows no such collection.
         */
        public static Type type(final SchemaSpace space, final String collection) {
            final Type declared = declared(space, collection);
            if (null != declared)
                return declared;
            final Obj inferred = space.schema().read(space.schema().vid().extend(collection));
            return inferred.isType() ? inferred.as() : null;
        }

        /**
         * The declared type of a collection: the type the space's schema route ({@code route=>[…/S=>schema]}) holds
         * for it, or a type written to the collection's path (held at its address under its own vid); null when the
         * collection's type is inferred.
         */
        public static Type declared(final SchemaSpace space, final String collection) {
            final fURI route = schemaRoute(space);
            if (null != route) {
                final Obj routed = Machine.read(route.extend(collection));
                if (routed.isType())
                    return routed.as();
            }
            final fURI address = space.schema().vid().extend(collection);
            final Obj held = space.schema().readDeclared(address);
            return held.isType() && !address.equals(held.vid()) ? held.as() : null;
        }

        /**
         * The schema the space's route declares its collections in: the value of the route whose key names
         * {@code S} ({@code /g/S => /m/db/grph/schema/modern}); null when the space routes no schema.
         */
        public static fURI schemaRoute(final SchemaSpace space) {
            return space.at(ROUTE).orElse(rec0()).asRec().elements()
                    .filter(entry -> "S".equals(entry.first().uriValue().name()))
                    .findFirst().map(entry -> entry.second().uriValue()).orElse(null);
        }

        /**
         * A RECORD IS ABOUT TO BE WRITTEN TO A COLLECTION, as the whole record the collection will hold. A declared type
         * refuses a record it does not hold; an inferred type widens to hold it (or, with {@code widen=>false}, refuses
         * it); a collection's first record is its type.
         */
        public static void admit(final SchemaSpace space, final String collection, final Obj record) {
            final Type declared = declared(space, collection);
            if (null != declared) {
                if (!record.test(declared))
                    throw MTronException.of("collection %s is declared %s: %s is not one", collection, declared.vid(), record);
                return;
            }
            final InstSet schema = space.schema();
            final fURI address = schema.vid().extend(collection);
            synchronized (schema) {
                final Obj held = schema.read(address); // the records the store already holds, inferred on a miss
                if (!held.isType())
                    publish(space, address, fields(Stream.of(record)));
                else if (setting(space, WIDEN).isBool() && !setting(space, WIDEN).boolValue()) {
                    if (!record.test(held))
                        throw MTronException.of("collection %s is %s: %s is not one", collection, held.vid(), record);
                } else
                    publish(space, address, widen(held.as(), record.as()));
            }
        }

        /**
         * THE FOLD a store infers a collection with: over every record, a field is required when every record holds it,
         * and its type is the LCD of the types of its values; the rec's tid is the records' own type, when they agree.
         */
        public static Rec fields(final Stream<? extends Obj> records) {
            final Map<Obj, Set<Type>> types = new LinkedHashMap<>();
            final Map<Obj, Long> present = new HashMap<>();
            final Set<fURI> tids = new HashSet<>();
            final long[] count = {0L};
            records.forEach(record -> {
                count[0]++;
                tids.add(record.tid());
                record.<Rec>as().jvm().forEach((key, value) -> {
                    if (value.isNoObj() || value.isNone())
                        return;
                    types.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(value.type());
                    present.merge(key, 1L, Long::sum);
                });
            });
            final Map<Obj, Obj> fields = new LinkedHashMap<>();
            types.forEach((key, valueTypes) -> fields.put(present.get(key) == count[0] ? key : key.maybe(),
                    Type.Helper.findLCD(new ArrayList<>(valueTypes))));
            return rec(fields, 1 == tids.size() ? tids.iterator().next() : REC_TID, null);
        }

        /**
         * The space has mounted: its schema instset is there to be read from now on (a read of it is what infers the
         * types it holds), and with {@code infer=>[discover=>eager]} every collection the store holds is inferred now.
         */
        public static void mount(final SchemaSpace space) {
            space.schema();
            if (setting(space, DISCOVER).isUri() && EAGER.equals(setting(space, DISCOVER).uriValue().toString()))
                types(space).forEach(type -> {
                });
        }

        /**
         * Infer a collection the store holds, unless its type is declared or already held.
         */
        private static void infer(final SchemaSpace space, final String collection) {
            final fURI address = space.schema().vid().extend(collection);
            if (space.schema().readDeclared(address).isType() || null != declared(space, collection))
                return;
            final Rec fields = space.fields(collection);
            if (!fields.isEmpty() || space.collections().anyMatch(collection::equals))
                publish(space, address, fields);
        }

        /**
         * The record widens the type: a field it holds widens to the LCD of the field's type and its value's, a field
         * it leaves out becomes {@code {?}}, and a field the type lacks is added as {@code {?}}.
         */
        private static Rec widen(final Type type, final Rec record) {
            final Map<String, Obj> values = new LinkedHashMap<>();
            record.jvm().forEach((key, value) -> {
                if (key.isUri() && !value.isNoObj() && !value.isNone())
                    values.put(key.uriValue().name(), value);
            });
            final Map<Obj, Obj> fields = new LinkedHashMap<>();
            Objects.requireNonNullElse(type.isPredicateObj(), rec0()).asRec().jvm().forEach((key, fieldType) -> {
                if (!key.isUri()) // the open entry: publish states it again
                    return;
                final Obj value = values.remove(key.uriValue().name());
                fields.put(null == value ? key.maybe() : key, null == value || !fieldType.isType()
                        ? fieldType
                        : Type.Helper.findLCD(List.of(fieldType.asType(), value.type())));
            });
            values.forEach((name, value) -> fields.put(uri(name).maybe(), value.type()));
            return rec(fields, type.tid().equals(record.tid()) ? type.tid() : REC_TID, null);
        }

        /**
         * Hold the inferred type at its address, when it differs from the one held there. It is open, as tble's types
         * always were: a field yet to come is admitted.
         */
        private static void publish(final SchemaSpace space, final fURI address, final Rec fields) {
            final boolean required = !(setting(space, REQUIRED).isBool() && !setting(space, REQUIRED).boolValue());
            final Map<Obj, Obj> predicate = new LinkedHashMap<>();
            fields.jvm().forEach((key, type) -> predicate.put(required ? key : key.maybe(), type));
            predicate.put(T(URI_TID.maybe()), ALL_TYPE);
            final Type type = T(fields.tid(), address, isa_(rec(predicate)).tryToInst(), null);
            if (!type.equals(space.schema().readDeclared(address)))
                Machine.write(address, type);
        }

        private static Obj setting(final SchemaSpace space, final String key) {
            return space.at(uri(INFER)).orElse(rec0()).at(uri(key));
        }
    }
}
