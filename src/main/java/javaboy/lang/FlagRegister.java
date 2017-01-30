package javaboy.lang;

import static javaboy.lang.Bit.ONE;
import static javaboy.lang.Bit.ZERO;

public class FlagRegister extends Byte {

    public FlagRegister() {
    }

    public int intValue() {
        return this.value;
    }

    public void setValue(int intValue) {
        this.value = intValue & 0xF0;
    }

    public Bit zf() {
        return getBit(7);
    }

    public Bit nf() {
        return getBit(6);
    }

    public Bit hf() {
        return getBit(5);
    }

    public Bit cf() {
        return getBit(4);
    }

    public void zf(Bit value) {
        setBit(7, value);
    }

    public void nf(Bit value) {
        setBit(6, value);
    }

    public void hf(Bit value) {
        setBit(5, value);
    }

    public void cf(Bit value) {
        setBit(4, value);
    }

    public void setZeroFlagForResult(int value) {
        if (value == 0) {
            zf(ONE);
        } else {
            zf(ZERO);
        }
    }

}
