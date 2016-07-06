package javaboy.lang;

import static javaboy.lang.Bit.BitValue.ONE;
import static javaboy.lang.Bit.BitValue.ZERO;

/**
 * Created by gabrieloshiro on 2016-06-28.
 */
public class FlagRegister extends Byte {

    public FlagRegister() {
    }

    public int intValue() {
        return this.value;
    }

    public void setValue(int i) {
        this.value = i & 0xF0;
    }

    public Bit.BitValue zf() {
        return getBit(7);
    }

    public Bit.BitValue nf() {
        return getBit(6);
    }

    public Bit.BitValue hf() {
        return getBit(5);
    }

    public Bit.BitValue cf() {
        return getBit(4);
    }

    public void zf(Bit.BitValue value) {
        setBit(7, value);
    }

    public void nf(Bit.BitValue value) {
        setBit(6, value);
    }

    public void hf(Bit.BitValue value) {
        setBit(5, value);
    }

    public void cf(Bit.BitValue value) {
        setBit(4, value);
    }

    public void setZf() {
        setBit(7, ONE);
    }

    public void setNf() {
        setBit(6, ONE);
    }

    public void setHf() {
        setBit(5, ONE);
    }

    public void setCf() {
        setBit(4, ONE);
    }

    public void resetZf() {
        setBit(7, ZERO);
    }

    public void resetNf() {
        setBit(6, ZERO);
    }

    public void resetHf() {
        setBit(5, ZERO);
    }

    public void resetCf() {
        setBit(4, ZERO);
    }

}
