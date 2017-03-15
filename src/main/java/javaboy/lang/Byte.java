package javaboy.lang;

import static javaboy.lang.Bit.ONE;

public class Byte {

    int value = 0;

    public Byte() {
    }

    public Byte(int value) {
        setValue(value);
    }

    public Byte(byte value) {
        setValue(value);
    }

    public int intValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value & 0xFF;
    }

    public void setValue(Byte b) {
        this.setValue(b.intValue());
    }

    public int lowerNibble() {
        return value & 0xF;
    }

    public void lowerNibble(int value) {
        this.value = (this.value & 0xF0) | (value & 0xF);
    }

    public int upperNibble() {
        return value >> 4;
    }

    public void upperNibble(int i) {
        this.value = (this.value & 0x0F) | ((i & 0xF) << 4);
    }

    public void swap() {
        value = (lowerNibble() << 4) | upperNibble();
    }

    public void inc() {
        setValue(value + 1);
    }

    public Bit getBit(int index) {
        if ((index & 0xFFFFFFF8) != 0) {
            throw new IllegalArgumentException("Bit index on a byte should be in the range 0..7. Index passed: " + index);
        }
        if (((value >> index) & 1) == 1) {
            return ONE;
        } else {
            return Bit.ZERO;
        }
    }

    public void setBit(int index, Bit value) {
        if ((index & 0xFFFFFFF8) != 0) {
            throw new IllegalArgumentException("Bit index on a byte should be in the range 0..7. Index passed: " + index);
        }

        if (value == ONE) {
            this.value = this.value | (1 << index);
        } else {
            this.value = this.value & ~(1 << index);
        }

    }
}
