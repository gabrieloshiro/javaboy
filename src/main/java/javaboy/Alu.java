package javaboy;

import javaboy.lang.Bit;
import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;

import static javaboy.lang.Bit.BitValue.ONE;
import static javaboy.lang.Bit.BitValue.ZERO;

/**
 * Created by gabrieloshiro on 2016-07-03.
 */
public class Alu {

    private void inc(Byte left, FlagRegister f) {
        f.nf(ZERO);

        int lowerResult = left.getLowerNibble() + 1;

        if ((lowerResult & 0x10) == 0x10) {
            f.hf(ONE);
        } else {
            f.hf(ZERO);
        }

        int result = left.intValue() + 1;

        if ((result & 0xFF) == 0) {
            f.zf(ONE);
        } else {
            f.zf(ZERO);
        }

        left.setValue(result);
    }

    private void adc(Byte left, Byte right, Bit.BitValue carry, FlagRegister f) {
        f.nf(ZERO);

        int lowerResult = left.getLowerNibble() + right.getLowerNibble() + carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            f.hf(ONE);
        } else {
            f.hf(ZERO);
        }

        int result = left.intValue() + right.intValue() + carry.intValue();

        if ((result & 0x100) == 0x100) {
            f.cf(ONE);
        } else {
            f.cf(ZERO);
        }

        if ((result & 0xFF) == 0) {
            f.zf(ONE);
        } else {
            f.zf(ZERO);
        }

        left.setValue(result);
    }

    private void add(Byte left, Byte right, FlagRegister f) {
        adc(left, right, ZERO, f);
    }

    private void add(Short left, Short right, FlagRegister f) {

        f.nf(ZERO);

        int lowerResult = (left.intValue() & 0x0FFF) + (right.intValue() & 0x0FFF);

        if ((lowerResult & 0x1000) == 0x1000) {
            f.hf(ONE);
        } else {
            f.hf(ZERO);
        }

        int result = left.intValue() + right.intValue();

        if ((result & 0x10000) == 0x10000) {
            f.cf(ONE);
        } else {
            f.cf(ZERO);
        }

        left.setValue(result);

    }

    private void dec(Byte left, FlagRegister f) {
        f.nf(ONE);

        int lowerResult = left.getLowerNibble() - 1;

        if ((lowerResult & 0x10) == 0x10) {
            f.hf(ONE);
        } else {
            f.hf(ZERO);
        }

        int result = left.intValue() - 1;

        if ((result & 0xFF) == 0) {
            f.zf(ONE);
        } else {
            f.zf(ZERO);
        }

        left.setValue(result);
    }

    private void sbc(Byte left, Byte right, Bit carry, FlagRegister f) {
        f.nf(ONE);

        int lowerResult = left.getLowerNibble() - right.getLowerNibble() - carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            f.hf(ONE);
        } else {
            f.hf(ZERO);
        }

        int result = left.intValue() - right.intValue() - carry.intValue();

        if ((result & 0x100) == 0x100) {
            f.cf(ONE);
        } else {
            f.cf(ZERO);
        }

        if ((result & 0xFF) == 0) {
            f.zf(ONE);
        } else {
            f.zf(ZERO);
        }

        left.setValue(result);
    }

    private void sub(Byte left, Byte right, FlagRegister f) {
                adc(left, right, ZERO, f);
    }

}
