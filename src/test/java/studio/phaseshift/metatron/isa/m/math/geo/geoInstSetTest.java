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

package studio.phaseshift.metatron.isa.m.math.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.isa.AbstractInstSetTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.isa.m.mach.type.Machine.Helper.evalAt;
import static studio.phaseshift.metatron.isa.m.mach.type.Machine.current;
import static studio.phaseshift.metatron.isa.m.mach.type.Machine.parse;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;

/**
 * The {@code /m/math/geo} instruction set — the GeoJSON (RFC 7946) model as mtron types, and the algorithms.
 *
 * <p>The fixture mounts the instset, so its types are read by SHORT name ({@code point::[...]}), never by the
 * {@code geo:} prefix alias, which only exists after {@code import(/m/math/geo,geo)}.
 *
 * <p>THE VALUE IS THE GEOMETRY. There is no {@code coordinates} key and no {@code type} key in the model: a point is a
 * list of two or three reals, a line is an ordered list of points, a polygon is a list of lines. GeoJSON's vocabulary
 * lives only in {@link ObjGeoJSONSerializer}.
 */
public class geoInstSetTest extends AbstractInstSetTest {

    private static final String POINT = "point::[-123.13,44.47]";
    private static final String LINE = "line::[[-123.13,44.47],[-121.18,45.61]]";
    private static final String POLYGON = "polygon::[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.4]]]";

    public geoInstSetTest() {
        super(geoInstSet::new);
    }

    /** evaluate one mtron expression in the mounted machine */
    private static studio.phaseshift.metatron.isa.m.type.Obj eval(final String code) {
        return evalAt(current(), () -> parse(code).apply(noobj()));
    }

    /**
     * Construction is typed by the DECLARATION: a point is two or three reals, a line one or more points, a polygon
     * one or more lines. mtron's int is not a real, and a bad arity is refused.
     */
    @ParameterizedTest
    @CsvSource(value = {
            POINT + "                                              % " + POINT,
            "point::[-123.13,44.47,1240.0]                          % point::[-123.13,44.47,1240.0]",
            POINT + ".?point::T                                       % " + POINT,
            POINT + ".?line::T                                        % noobj",
            LINE + "                                                % " + LINE,
            LINE + ".?line::T                                         % " + LINE,
            LINE + ".?point::T                                        % noobj",
            POLYGON + "                                             % " + POLYGON,
            POLYGON + ".?polygon::T                                    % " + POLYGON,
            // the declaration refuses what it does not describe
            "point::[-123.13]                                        % <ERROR>",
            "point::[1,2]                                            % <ERROR>",
            "line::[-123.13,44.47]                                   % <ERROR>",
            "polygon::[[-123.2,44.4],[-122.9,44.6]]                  % <ERROR>",
    }, delimiter = '%', quoteCharacter = '~')
    public void testConstruction(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /** The kind, the coordinates, the envelope and the measures — the questions the wire answers with a `type` key. */
    @ParameterizedTest
    @CsvSource(value = {
            POINT + ".kind()                                        % Point",
            LINE + ".kind()                                          % LineString",
            POLYGON + ".kind()                                       % Polygon",
            POINT + ".coordinates()                                  % [" + POINT + "]",
            LINE + ".coordinates()                                   % [" + POINT + ",point::[-121.18,45.61]]",
            POINT + ".envelope()                                    % bbox2d::[-123.13,44.47,-123.13,44.47]",
            LINE + ".envelope()                                     % bbox2d::[-123.13,44.47,-121.18,45.61]",
            POLYGON + ".envelope()                                  % bbox2d::[-123.2,44.4,-122.9,44.6]",
            POINT + ".area()                                       % 0.0",
            LINE + ".area()                                        % 0.0",
    }, delimiter = '%', quoteCharacter = '~')
    public void testQuestions(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /** The predicates: containment, inside an envelope, and overlap. */
    @ParameterizedTest
    @CsvSource(value = {
            POLYGON + ".inside(bbox2d::[-123.2,44.4,-122.9,44.6])         % true",
            POLYGON + ".inside(bbox2d::[-124.2,44.4,-123.9,44.6])         % false",
            POLYGON + ".contains(point::[-123.05,44.5])                    % true",
            POLYGON + ".contains(point::[-120.0,44.5])                     % false",
            POLYGON + ".intersects(point::[-123.05,44.5])                  % true",
            POLYGON + ".intersects(point::[-120.0,44.5])                   % false",
            POINT + ".inside(bbox2d::[-123.2,44.4,-122.9,44.6])           % true",
    }, delimiter = '%', quoteCharacter = '~')
    public void testPredicates(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /** The wire: the document carries the wrapper, the value does not — and `as(json::T)` is the door. */
    @ParameterizedTest
    @CsvSource(value = {
            POINT + ".as(json::T)    % json::'{\"type\":\"Point\",\"coordinates\":[-123.13,44.47]}'",
            LINE + ".as(json::T)     % json::'{\"type\":\"LineString\",\"coordinates\":[[-123.13,44.47],[-121.18,45.61]]}'",
            POINT + ".wkt()          % 'POINT (-123.13 44.47)'",
            LINE + ".wkt()           % 'LINESTRING (-123.13 44.47, -121.18 45.61)'",
            "'POINT (-123.13 44.47)'.as(point::T)       % " + POINT,
    }, delimiter = '%', quoteCharacter = '~')
    public void testEncodings(final String code, final String expected) {
        AbstractMetatronTest.checkCodeParseApply(LOG, code, expected);
    }

    /**
     * A FEATURE IS A REC WITH A GEO FIBER — the wild-fiber, {@code rec[geo:+]::T} — so a rec that carries a geometry
     * is asked the geometry's questions and written as a GeoJSON Feature without any feature type existing.
     */
    @Test
    public void testAGeoFiberAnswersThroughItsWrapper() {
        eval("person -> rec[geo:location]::T[?[name=>str::T]]@person");
        eval("person::[name=>'marko',geo:location=>point::[-123.0,44.5]]@marko");
        // the untyped geo question is answered by the geometry at the fiber
        assertTrue(eval("*marko.kind()").isUri());
        assertTrue(eval("*marko.?point::T").isNoObj() || eval("*marko.?point::T").isRec());
        assertTrue(eval("*marko>>geo:location").isLst());
        // an envelope question reaches the point inside the person
        assertTrue(eval("*marko.inside(bbox2d::[-123.2,44.4,-122.9,44.6])").isBool());
    }
}
