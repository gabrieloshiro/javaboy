package javaboy.lang;

import static javaboy.lang.BitValue.ONE;
import static javaboy.lang.BitValue.ZERO;

public class FlagRegister extends Byte {

    public FlagRegister() {
    }

    public int intValue() {
        return this.value;
    }

    public void setValue(int i) {
        this.value = i & 0xF0;
    }

    public BitValue zf() {
        return getBit(7);
    }

    public BitValue nf() {
        return getBit(6);
    }

    public BitValue hf() {
        return getBit(5);
    }

    public BitValue cf() {
        return getBit(4);
    }

    public void zf(BitValue value) {
        setBit(7, value);
    }

    public void nf(BitValue value) {
        setBit(6, value);
    }

    public void hf(BitValue value) {
        setBit(5, value);
    }

    public void cf(BitValue value) {
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
