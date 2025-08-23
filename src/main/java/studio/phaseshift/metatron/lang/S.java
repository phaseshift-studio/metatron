package studio.phaseshift.metatron.lang;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class S implements Base {

    public static class Obj implements Base.Obj {
        final Object value;

        public Obj(final Object value) {
            this.value = value;
        }

        public Object value() {
            return this.value;
        }

        public static Obj of(final Object value) {
            if (value instanceof Boolean)
                return new Bool((Boolean) value);
            else if (value instanceof Integer)
                return new Int((Integer) value);
            else if (value instanceof Double)
                return new Real((Double) value);
            else if (value instanceof String)
                return new Str((String) value);
            else if (value instanceof URI)
                return new Uri((URI) value);
            else if (value instanceof List)
                return new Lst((List) value);
            else if (value instanceof Map)
                return new Rec((Map) value);
            else
                throw new RuntimeException("unknown object type: " + value.toString());
        }
    }

    public static class Bool extends Obj implements Base.Bool {

        public Bool(final Boolean value) {
            super(value);
        }

        public Boolean value() {
            return (Boolean) this.value;
        }

    }

    public static class Int extends Obj implements Base.Int {
        public Int(final Integer value) {
            super(value);
        }

        public Integer value() {
            return (Integer) this.value;
        }
    }

    public static class Real extends Obj implements Base.Real {

        public Real(final Double value) {
            super(value);
        }

        public Double value() {
            return (Double) this.value;
        }

    }

    public static class Str extends Obj implements Base.Str {

        public Str(final String value) {
            super(value);
        }

        public String value() {
            return (String) this.value;
        }

    }

    public static class Uri extends Obj implements Base.Uri {

        public Uri(final URI value) {
            super(value);
        }

        public URI value() {
            return (URI) this.value;
        }

    }

    public static class Lst extends Obj implements Base.Lst {

        public Lst(final List<Base.Obj> value) {
            super(value);
        }

        @Override
        public List<Base.Obj> value() {
            return (List<Base.Obj>) this.value;
        }

        @Override
        public Lst apply(final Base.Obj other) {
            List<Base.Obj> list = new ArrayList<>();
            for (int i = 0; i < this.value().size(); i++) {
                list.set(i, this.value().get(i).apply(other));
            }
            return new S.Lst(list);
        }

    }

    public static class Rec extends Obj implements Base.Rec {

        public Rec(final Map<Base.Obj, Base.Obj> value) {
            super(value);
        }

        @Override
        public Map<Base.Obj, Base.Obj> value() {
            return (Map<Base.Obj, Base.Obj>) this.value;
        }

        @Override
        public Rec apply(final Base.Obj other) {
            Map<Base.Obj, Base.Obj> map = new HashMap<>();
            return new S.Rec(map);
        }

    }
}
