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

package studio.phaseshift.metatron.furi.q;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.q.QCollection.LINEQ_TID;

public interface LineQTest extends QProcTest {


    @ParameterizedTest
    @TestData({
            "$$/xyz -> \"\"\"this is\na long\nmulti-line\nstring\"\"\""
    })
    @CsvSource(value = {
            "*$$/xyz               % '''this is\na long\nmulti-line\nstring'''",
            "*$$/xyz?lineq=0       % '''this is'''",
            "*$$/xyz?lineq=0-1     % '''this is\na long'''",
            "*$$/xyz?lineq=0-2     % '''this is\na long\nmulti-line'''",
            "*$$/xyz?lineq=0-3     % '''this is\na long\nmulti-line\nstring'''",
            "*$$/xyz?lineq=0-4     % '''this is\na long\nmulti-line\nstring'''",
            "*$$/xyz?lineq=0-10    % '''this is\na long\nmulti-line\nstring'''",
    }, delimiter = '%')
    default void testLineQRead(String code, String expected) {
        this.attachQ(LINEQ_TID);
        final Obj resultObj = ObjmtronSerializer.parse(make(code)).apply();
        final Obj expectedObj = ObjmtronSerializer.parse(make(expected)).apply();
        assertEquals(expectedObj, resultObj);
    }

    @Test
    default void testLineQWrite() {
        this.attachQ(LINEQ_TID);
        for (final String source : new String[]{
                "$$/xyz ->  \"\"\"this is\na long\nmulti-line\nstring\"\"\"           % \"\"\"this is\na long\nmulti-line\nstring\"\"\"",
                "*$$/xyz?lineq=0                                                      % \"this is\"",
                "$$/xyz?lineq=0 -> \"xxx\"                                            % \"xxx\"",
                "*$$/xyz                                                              % \"\"\"xxx\na long\nmulti-line\nstring\"\"\"",
                "$$/xyz?lineq=1-2 -> \"abc\"                                          % \"abc\"",
                "*$$/xyz                                                              % \"\"\"xxx\nabc\nstring\"\"\"",
                "$$/xyz?lineq=0-10 -> \"z\"                                            % \"z\"",
                "*$$/xyz                                                              % \"\"\"z\"\"\""}) {
            final Obj resultObj = ObjmtronSerializer.parse(make(source.split("%")[0])).apply();
            final Obj expectedObj = ObjmtronSerializer.parse(make(source.split("%")[1])).apply();
            assertEquals(expectedObj, resultObj);
        }

    }

    @Test
    default void testLineQPreservesStructure() {
        this.attachQ(LINEQ_TID);
        for (final String source : new String[]{
                // blank lines survive a lineq edit
                "$$/xyz ->  \"\"\"line1\n\nline2\nline3\"\"\"    % \"\"\"line1\n\nline2\nline3\"\"\"",
                "$$/xyz?lineq=2 -> \"replaced\"                  % \"replaced\"",
                "*$$/xyz                                        % \"\"\"line1\n\nreplaced\nline3\"\"\"",
                // deletion: writing "" removes the range cleanly
                "$$/xyz ->  \"\"\"line1\nline2\nline3\"\"\"       % \"\"\"line1\nline2\nline3\"\"\"",
                "$$/xyz?lineq=1 -> \"\"                          % \"\"",
                "*$$/xyz                                        % \"\"\"line1\nline3\"\"\"",
                // multi-line replacement inserts a block
                "$$/xyz ->  \"\"\"line1\nline2\"\"\"              % \"\"\"line1\nline2\"\"\"",
                "$$/xyz?lineq=1 -> \"\"\"a\nb\nc\"\"\"            % \"\"\"a\nb\nc\"\"\"",
                "*$$/xyz                                        % \"\"\"line1\na\nb\nc\"\"\""}) {
            final Obj resultObj = ObjmtronSerializer.parse(make(source.split("%")[0])).apply();
            final Obj expectedObj = ObjmtronSerializer.parse(make(source.split("%")[1])).apply();
            assertEquals(expectedObj, resultObj);
        }

    }

    @Test
    default void testLineQAppend() {
        this.attachQ(LINEQ_TID);
        for (final String source : new String[]{
                "$$/xyz ->  \"\"\"line1\nline2\"\"\"     % \"\"\"line1\nline2\"\"\"",
                // ?lineq=2 (the line count) appends a single line
                "$$/xyz?lineq=2 -> \"line3\"            % \"line3\"",
                "*$$/xyz                                 % \"\"\"line1\nline2\nline3\"\"\"",
                // appending beyond the line count also lands at the end
                "$$/xyz?lineq=99 -> \"line4\"           % \"line4\"",
                "*$$/xyz                                 % \"\"\"line1\nline2\nline3\nline4\"\"\""}) {
            final Obj resultObj = ObjmtronSerializer.parse(make(source.split("%")[0])).apply();
            final Obj expectedObj = ObjmtronSerializer.parse(make(source.split("%")[1])).apply();
            assertEquals(expectedObj, resultObj);
        }

    }

    @Test
    default void testLineQInsert() {
        this.attachQ(LINEQ_TID);
        for (final String source : new String[]{
                "$$/xyz ->  \"\"\"line1\nline2\nline3\"\"\"       % \"\"\"line1\nline2\nline3\"\"\"",
                // lineq=+ inserts at the end
                "$$/xyz?lineq=+ -> \"line4\"                      % \"line4\"",
                "*$$/xyz                                         % \"\"\"line1\nline2\nline3\nline4\"\"\"",
                // lineq=N+ inserts before line N — pushes it down, no overwrite
                "$$/xyz?lineq=1+ -> \"inserted\"                  % \"inserted\"",
                "*$$/xyz                                         % \"\"\"line1\ninserted\nline2\nline3\nline4\"\"\"",
                // lineq=0+ inserts at the very beginning
                "$$/xyz?lineq=0+ -> \"top\"                       % \"top\"",
                "*$$/xyz                                         % \"\"\"top\nline1\ninserted\nline2\nline3\nline4\"\"\"",
                // multi-line block insert
                "$$/xyz?lineq=2+ -> \"\"\"a\nb\nc\"\"\"             % \"\"\"a\nb\nc\"\"\"",
                "*$$/xyz                                         % \"\"\"top\nline1\na\nb\nc\ninserted\nline2\nline3\nline4\"\"\""}) {
            final Obj resultObj = ObjmtronSerializer.parse(make(source.split("%")[0])).apply();
            final Obj expectedObj = ObjmtronSerializer.parse(make(source.split("%")[1])).apply();
            assertEquals(expectedObj, resultObj);
        }

    }

}
