package studio.phaseshift.metatron.lang.obj;

public interface Call extends Obj {

    default Call singleOrSequence() {
        if (this.isCode()) {
            if (this.codeValue().isEmpty())
                return NoObj.single();
            else if (this.codeValue().size() == 1)
                return this.codeValue().get(0);
        }
        return this;
    }

    default boolean checkDom(final Obj obj) {
        return obj.matches(this.dom());
    }

    default boolean checkRng(final Obj obj) {
        return obj.matches(this.rng());
    }
}
