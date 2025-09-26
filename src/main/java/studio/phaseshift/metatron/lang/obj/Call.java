package studio.phaseshift.metatron.lang.obj;

import studio.phaseshift.metatron.lang.obj.mtron.MCode;

import java.util.List;

public interface Call extends Obj {

    static Call from(final List<Inst> insts) {
        if (insts.isEmpty())
            return NoObj.single();
        else if (insts.size() == 1)
            return insts.get(0);
        else
            return MCode.of(insts);
    }

    default Call singleOrSequence() {
        if (this.isCode()) {
            if (this.codeValue().isEmpty())
                return NoObj.single();
            else if (this.codeValue().size() == 1)
                return this.codeValue().get(0);
        }
        return this;
    }

    default List<Inst> insts() {
        return this.isCode() ? this.codeValue() : List.of(this.as());
    }

    <C extends Call> C resolve(final Obj start);

    default <C extends Call> C dom(final Type domain) {
        return (C) this.tid(this.tid().dom(domain.tid()));
    }

    default <C extends Call> C rng(final Type range) {
        return (C) this.tid(this.tid().rng(range.tid()));
    }
}
