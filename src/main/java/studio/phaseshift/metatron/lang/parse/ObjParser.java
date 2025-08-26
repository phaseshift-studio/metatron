/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.lang.parse;

import org.jparsec.*;
import org.jparsec.Terminals.*;
import org.jparsec.Tokens.*;
import org.jparsec.pattern.*;
import studio.phaseshift.metatron.lang.obj.*;

import java.util.*;

public class ObjParser {

    //protected static Parser<BObj.Bool> BOOL = Identifier.TOKENIZER.or(Patterns.string("true"),Patterns.string("false"));
    protected static Parser<BObj.Int> INT = Terminals.IntegerLiteral.TOKENIZER.map(i -> new SObj.Int(Integer.valueOf(i.text())));
    protected static Parser<BObj.Obj> OBJ = Parsers.or(INT, INT);

    public static BObj.Obj parse(final String input) {
        return (BObj.Obj) Parsers.<BObj.Obj>sequence(OBJ, Parsers.EOF).<BObj.Obj>parseTree(input).getValue();
    }

    static final Parser NUMBER = Terminals.DecimalLiteral.TOKENIZER.map(arg0 -> Double.valueOf(arg0.text()));


    public static void main(final String[] args) {
        System.out.println(OBJ.parse("12"));
    }
}
