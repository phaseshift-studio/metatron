package studio.phaseshift.metatron.lang.obj.mtron;

import studio.phaseshift.metatron.lang.obj.Coeff;

import java.util.Objects;

public interface MCoeff {

    class Int implements Coeff<Long,Int> {
        private final Long min;
        private final Long max;

        private Int(final Long min, final Long  max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public Long min() {
            return this.min;
        }

        @Override
        public Long max() {
            return  this.max;
        }

        @Override
        public Int plus(final Int rhs) {
            return new Int(this.min() + rhs.min(), this.max() + rhs.max());
        }

        @Override
        public Int mult(final Int rhs) {
            return new Int(this.min() * rhs.min(), this.max() * rhs.max());
        }

        public boolean isZero() {
                 return Objects.equals(this.min, 0L) && Objects.equals(this.max, 0L);
        }

        @Override
        public boolean isOne() {
            return Objects.equals(this.min, 1L) && Objects.equals(this.max,1L);
        }

        @Override
        public boolean isStar() {
            return Objects.equals(this.min, 0L) && null == this.max;
        }

        @Override
        public boolean isPlus() {
            return Objects.equals(this.min, 1L) && null == this.max;
        }

        @Override
        public boolean isQuestion() {
            return  Objects.equals(this.min, 0L) &&  Objects.equals(this.max, 1L);
        }

        @Override
        public boolean within(final Int rhs) {
            Long minA = this.min() ==  null ? 0 : this.min();
            Long maxA = this.max() == null ? Long.MAX_VALUE : this.max();
            Long minB = rhs.min() ==  null ? 0 : rhs.min();
            Long maxB = rhs.max() == null ? Long.MAX_VALUE : rhs.max();
            return minA.compareTo(minB) >= 0 && maxA.compareTo(maxB) <= 0;
        }

        // @Override
        public static Int star() {
            return Int.of(0L,null);
        }

      //  @Override
        public static Int plus() {
            return Int.of(1L,null);
        }

        public static Int zero() {
            return Int.of(0L,0L);
        }

       // @Override
        public static Int question() {
            return Int.of(0L,1L);
        }

       // @Override
        public static Int one() {
            return Int.of(1L,1L);
        }

        @Override
        public String toString() {
            if(null != this.min && null != this.max && this.min.equals(this.max))
                return this.min.toString();
            return (null == this.min ? "" : this.min) + "," + (null == this.max ? "" : this.max);
        }

        @Override
        public int hashCode() {
          return  Objects.hash(this.min,this.max);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Int && Objects.equals(this.min,((Int) other).min) && Objects.equals(this.max,((Int) other).max);
        }

        public static Int of(final Long min, final Long max) {
            return new Int(min,max);
        }

        public static Int of(final String parse) {
            if(parse.isEmpty())
                return Int.one();
            else if(parse.equals("0"))
                return Int.zero();
            else if(parse.equals("*"))
                return Int.star();
            else if(parse.equals("?"))
                return Int.question();
            else if(parse.equals("+"))
                return Int.plus();
            else if(parse.equals("1"))
                return Int.one();
            else if(!parse.contains(","))
                return Int.of(Long.valueOf(parse),Long.valueOf(parse));
            else {
                final String[] split = parse.split(",");
                return split.length == 1 ?
                        (parse.charAt(0) == ',' ? Int.of(null,Long.valueOf(split[0])) : Int.of(Long.valueOf(split[0]),null)):
                        Int.of(Long.valueOf(split[0]),Long.valueOf(split[1]));
            }

        }
    }
}
