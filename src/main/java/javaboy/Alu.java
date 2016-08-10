package javaboy;

import javaboy.lang.BitValue;
import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;

import static javaboy.lang.BitValue.ONE;
import static javaboy.lang.BitValue.ZERO;

/**
 * Created by gabrieloshiro on 2016-07-03.
 */
public class Alu {

    public static class AluResult {
        private final FlagRegister flags = new FlagRegister();
        private final Byte result = new Byte();

        public FlagRegister flags() {
            return flags;
        }

        public Byte result() {
            return result;
        }
    }

    public static AluResult adc(Byte left, Byte right, BitValue carry) {
        AluResult r = new AluResult();

        r.flags().nf(ZERO);

        int lowerResult = left.getLowerNibble() + right.getLowerNibble() + carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            r.flags().hf(ONE);
        } else {
            r.flags().hf(ZERO);
        }

        int result = left.intValue() + right.intValue() + carry.intValue();

        if ((result & 0x100) == 0x100) {
            r.flags().cf(ONE);
        } else {
            r.flags().cf(ZERO);
        }

        if ((result & 0xFF) == 0) {
            r.flags().zf(ONE);
        } else {
            r.flags().zf(ZERO);
        }

        r.result().setValue(result);
        return r;
    }

    public static AluResult add(Byte left, Byte right) {
        return adc(left, right, ZERO);
    }

    public static AluResult inc(Byte left) {
        return add(left, new Byte(1));
    }

    public static AluResult add(Short left, Short right) {
        AluResult r = new AluResult();

        r.flags.nf(ZERO);

        int lowerResult = (left.intValue() & 0x0FFF) + (right.intValue() & 0x0FFF);

        if ((lowerResult & 0x1000) == 0x1000) {
            r.flags().hf(ONE);
        } else {
            r.flags().hf(ZERO);
        }

        int result = left.intValue() + right.intValue();

        if ((result & 0x10000) == 0x10000) {
            r.flags().cf(ONE);
        } else {
            r.flags().cf(ZERO);
        }

        r.result().setValue(result);
        return r;
    }

    public static AluResult sbc(Byte left, Byte right, BitValue carry) {
        AluResult r = new AluResult();

        r.flags().nf(ONE);

        int lowerResult = left.getLowerNibble() - right.getLowerNibble() - carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            r.flags().hf(ONE);
        } else {
            r.flags().hf(ZERO);
        }

        int result = left.intValue() - right.intValue() - carry.intValue();

        if ((result & 0x100) == 0x100) {
            r.flags().cf(ONE);
        } else {
            r.flags().cf(ZERO);
        }

        if ((result & 0xFF) == 0) {
            r.flags().zf(ONE);
        } else {
            r.flags().zf(ZERO);
        }

        r.result().setValue(result);
        return r;
    }

    public static AluResult sub(Byte left, Byte right) {
        return sbc(left, right, ZERO);
    }

    public static AluResult dec(Byte left) {
        return sub(left, new Byte(1));
    }


    public static AluResult xor(Byte left, Byte right, FlagRegister flags) {
        AluResult r = new AluResult();

        int result = left.intValue() ^ right.intValue();

        flags.nf(ZERO);
        flags.hf(ZERO);
        flags.cf(ZERO);
        flags.zf(ZERO);

        if (result == 0) {
            flags.zf(ONE);
        }

        left.setValue(result);
        return r;
    }

    public static AluResult or(Byte left, Byte right, FlagRegister flags) {
        AluResult r = new AluResult();

        int result = left.intValue() | right.intValue();

        flags.nf(ZERO);
        flags.hf(ZERO);
        flags.cf(ZERO);
        flags.zf(ZERO);

        if (result == 0) {
            flags.zf(ONE);
        }

        left.setValue(result);
        return r;
    }
}
