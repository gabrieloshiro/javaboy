package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-28.
 */
public class ZeroFlag extends Bit {

    public void setIfZero(int i) {
        if (i == 0) {
            setValue(BitValue.ONE);
        } else {
            setValue(BitValue.ZERO);
        }

    }

    public void setIfZero(Bit b) {
        if (b.intValue() == 0) {
            setValue(BitValue.ONE);
        } else {
            setValue(BitValue.ZERO);
        }

    }
}
