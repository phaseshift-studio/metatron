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

package studio.phaseshift.metatron.isa.m;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.isa.AbstractObjTest;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer.parse;

/**
 * Contract resolution across a refinement: a contract is declared once, at the most general pair of endpoints
 * it can honour, and a query walks each endpoint's path to the root to find it. {@code nat}'s path is
 * nat -> int -> #, so a nat on either side of {@code plus} resolves to the int contract — a refined query
 * widens to the contract, it never resolves to nothing and never displaces the nominal pair.
 * <p>
 * This boots every inst set ({@code #}): with only the IO isa loaded the nat-qualified reads resolve to
 * nothing at all, because the type those queries name does not exist yet — so the boot is part of the case,
 * not incidental. It lives beside {@link LatticeReadTest}, its admission-facing peer.
 */
public class ContractResolutionTest extends AbstractObjTest {

    @BeforeAll
    public static void importAllInstSets() {
        InstSet.importInstSet(f("#"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "*plus?nat<=nat % both endpoints refined — nat -> int -> # on both sides",
            "*plus?nat<=int % a nat rng with an int dom",
            "*plus?int<=nat % a nat dom with an int rng",
            "*plus?int<=int % the nominal pair — widened queries must not displace it",
    }, delimiter = '%')
    void testPlusResolvesToTheIntContract(final String read, final String why) {
        final List<Obj> resolved = parse(read).apply().stream().toList();
        assertEquals(1, resolved.size(), why);
        final Inst contract = resolved.getFirst().asInst();
        assertEquals(f("/m/int"), contract.tid().dom(), why);
        assertEquals(f("/m/int"), contract.tid().rng(), why);
    }
}
