package javaboy;

import javaboy.lang.Bit;
import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;

/**
 * Created by gabrieloshiro on 2016-07-03.
 */
public class Alu {

    private void inc(Byte left, FlagRegister f) {
        f.nf().reset();

        int lowerResult = left.getLowerNibble() + 1;

        if ((lowerResult & 0x10) == 0x10) {
            f.hf().set();
        } else {
            f.hf().reset();
        }

        int result = left.intValue() + 1;

        if ((result & 0xFF) == 0) {
            f.zf().set();
        } else {
            f.zf().reset();
        }

        left.setValue(result);
    }

    private void adc(Byte left, Byte right, Bit carry, FlagRegister f) {
        f.nf().reset();

        int lowerResult = left.getLowerNibble() + right.getLowerNibble() + carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            f.hf().set();
        } else {
            f.hf().reset();
        }

        int result = left.intValue() + right.intValue() + carry.intValue();

        if ((result & 0x100) == 0x100) {
            f.cf().set();
        } else {
            f.cf().reset();
        }

        if ((result & 0xFF) == 0) {
            f.zf().set();
        } else {
            f.zf().reset();
        }

        left.setValue(result);
    }

    private void add(Byte left, Byte right, FlagRegister f) {
        adc(left, right, new Bit(0), f);
    }

    private void add(Short left, Short right, FlagRegister f) {

        //        f.nf().reset();

        int lowerResult = (left.intValue() & 0x0FFF) + (right.intValue() & 0x0FFF);

        //        if ((lowerResult & 0x1000) == 0x1000) {
        //            f.hf().set();
        //        } else {
        //            f.hf().reset();
        //        }

        int result = left.intValue() + right.intValue();

        if ((result & 0x10000) == 0x10000) {
            f.cf().set();
        } else {
            f.cf().reset();
        }

        left.setValue(result);

    }

    private void dec(Byte left, FlagRegister f) {
        f.nf().set();

        int lowerResult = left.getLowerNibble() - 1;

        if ((lowerResult & 0x10) == 0x10) {
            f.hf().set();
        } else {
            f.hf().reset();
        }

        int result = left.intValue() - 1;

        if ((result & 0xFF) == 0) {
            f.zf().set();
        } else {
            f.zf().reset();
        }

        left.setValue(result);
    }

    private void sbc(Byte left, Byte right, Bit carry, FlagRegister f) {
        f.nf().set();

        int lowerResult = left.getLowerNibble() - right.getLowerNibble() - carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            f.hf().set();
        } else {
            f.hf().reset();
        }

        int result = left.intValue() - right.intValue() - carry.intValue();

        if ((result & 0x100) == 0x100) {
            f.cf().set();
        } else {
            f.cf().reset();
        }

        if ((result & 0xFF) == 0) {
            f.zf().set();
        } else {
            f.zf().reset();
        }

        left.setValue(result);
    }

    private void sub(Byte left, Byte right, FlagRegister f) {
        adc(left, right, new Bit(0), f);
    }

}
