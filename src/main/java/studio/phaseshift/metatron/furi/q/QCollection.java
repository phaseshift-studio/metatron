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

package studio.phaseshift.metatron.furi.q;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.furi.Q;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.machine.SwarmMachine;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.Q.Q_TID;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MBool.bool;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class QCollection {

    public static final fURI CONSTQ_TID = Q_TID.extend("constq");
    public static final Type CONSTQ_TYPE = Type.Builder.build().tid(Q_TID).vid(CONSTQ_TID).constructor(QCollection::constQ).create();
    //
    public static final fURI INCRQ_TID = Q_TID.extend("incrq");
    public static final Type INCRQ_TYPE = Type.Builder.build().tid(Q_TID).vid(INCRQ_TID).constructor(QCollection::incrQ).create();
    //
    public static final fURI SUBQ_TID = Q_TID.extend("subq");
    public static final fURI SUBSCRIPTION_TID = SUBQ_TID.extend("sub");
    public static final Type SUBQ_TYPE = Type.Builder.build()
            .vid(SUBQ_TID)
            .tid(REC_TID)
            .constructor(
                    instC(mInstSet.INST_TID.dom(ALL.maybe()).rng(SUBQ_TID),
                            lst(isa_(rec()).tryToInst()),
                            (lhs, inst) -> QCollection.subq())).create();

    public static final Type SUB_TYPE =
            Type.Builder.build()
                    .vid(SUBSCRIPTION_TID)
                    .tid(REC_TID)
                    .isaPredicate(rec(TARGET, T(URI_TID), ON_RECV, T(ALL)))
                    .create();

    private QCollection() {
        // do nothing 
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q constQ() {
        final Set<fURI> CONSTQ_FURIS = new HashSet<>();
        return studio.phaseshift.metatron.furi.Q.Helper.build(CONSTQ_TID, f(CONST))
                .preRead(furi -> bool(CONSTQ_FURIS.contains(furi.noQ())))
                .preWrite((furi, obj) -> {
                    if (obj.isNoObj()) CONSTQ_FURIS.remove(furi.noQ());
                    else CONSTQ_FURIS.add(furi.noQ());
                    return noobj();
                }).qlessWrite((furi, obj) -> {
                    if (!furi.hasQ(CONST) && CONSTQ_FURIS.contains(furi.noQ()))
                        return fail(MTronException.of("%s is a constant", furi.noQ()));
                    return noobj();
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q incrQ() {
        final AtomicLong counter = new AtomicLong(0);
        return studio.phaseshift.metatron.furi.Q.Helper.build(INCRQ_TID, f(INCR)).
                preWrite((vid, obj) -> {
                    final fURI incrPattern = vid.extend(vid.qValue(INCR, fURI.class)).resolve();
                    final List<String> newPath = new ArrayList<>();
                    for (final String p : incrPattern.path()) {
                        if (fURI.isPattern(p))
                            newPath.add(counter.incrementAndGet() + "");
                        else
                            newPath.add(p);
                    }
                    return obj.vid(vid.removeQ(INCR).path(newPath));
                }).create();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Q subq() {
        final Rec subscriptions = rec();
        final Queue<Machine> mail = new LinkedList<>();
        return studio.phaseshift.metatron.furi.Q.Helper.build(Q_TID.extend("subq"), f("subq"))
                .preRead(vid -> subscriptions
                        .elements()
                        .map(Rel::second).map(Obj::asRec)
                        .filter(s -> vid.basePath().bimatches(s.at(TARGET).uriValue())).map(Obj::<Obj>as)
                        .reduce(Obj::append)
                        .orElse(noobj()))
                .postWrite((vid, obj, obj2) -> {
                    final Obj subscription;
                    if (obj.isNoObj()) {
                        subscription = noobj();
                        subscriptions.jvm().remove(vid.basePath().toUri());
                        subscription.logger().info("unsubscribing from %s");
                    } else if (obj.tid().basePath().equals(SUBSCRIPTION_TID)) {
                        subscription = obj;
                        subscriptions.jvm().put(vid.basePath().toUri(), obj);
                    } else {
                        subscription = rec(Map.of(uri(TARGET), uri(vid.basePath()), uri(ON_RECV), obj), SUBSCRIPTION_TID, null);
                        subscriptions.jvm().put(vid.basePath().toUri(), subscription);
                    }
                    //LOG.debug("current subscriptions: %s", subscriptions);
                    return subscription;
                })
                .qlessWrite((vid, obj) -> {
                    //   LOG.debug("evaluating {{y}}qless write{{/y}}: %s => %s", obj, vid);
                    subscriptions.elements().map(Rel::second).map(Obj::asRec).filter(s -> vid.test(s.at(TARGET).uriValue())).forEach(s -> {
                        //  LOG.debug("sending mail: (%s, %s)", obj, s);
                        mail.add(SwarmMachine.of(lst(List.of(vid.toUri(), obj)), s.at(ON_RECV).as()));
                    });
                    BootLoader.getExecutor().submit(new Thread(() -> {
                        while (!mail.isEmpty()) {
                            final Machine machine = mail.poll();
                            if (null == machine)
                                break;
                            //   LOG.trace("processing mail: %s", machine);
                            machine.apply();
                        }
                    }));
                    return noobj();
                }).create();
    }
}
