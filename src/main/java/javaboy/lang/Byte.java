package javaboy.lang;

import static javaboy.lang.BitValue.ONE;

public class Byte {

    int value = 0;

    public Byte() {
    }

    public Byte(int i) {
        setValue(i);
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

    public int getLowerNibble() {
        return value & 0xF;
    }

    public void setLowerNibble(int i) {
        this.value = (this.value & 0xF0) | (i & 0xF);
    }

    public int getUpperNibble() {
        return value >> 4;
    }

    public void setUpperNibble(int i) {
        this.value = (this.value & 0x0F) | ((i & 0xF) << 4);
    }

    public void swap() {
        value = (getUpperNibble() << 4) | getLowerNibble();
    }

    public BitValue getBit(int index) {
        if (index > 7 || index < 0) {
            throw new IllegalArgumentException("Bit index on a byte should be in the range 0..7. Index passed: " + index);
        }
        if (((value >> index) & 1) == 1) {
            return ONE;
        } else {
            return BitValue.ZERO;
        }
    }

    public void setBit(int index, BitValue value) {
        if (index > 7 || index < 0) {
            throw new IllegalArgumentException("Bit index on a byte should be in the range 0..7. Index passed: " + index);
        }

        if (value == ONE) {
            this.value = this.value | (1 << index);
        } else {
            this.value = this.value & ~(1 << index);
        }

    }
}
