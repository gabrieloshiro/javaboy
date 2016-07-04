package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Byte {

    int value = 0;

    public Byte() {
    }

    public Byte(int i) {
        setValue(i);
    }

    public int intValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value & 0xFF;
    }

    public int getLowerNibble() {
        return value & 0xF;
    }

    public void setLowerNibble(int i) {
        this.value = (this.value & 0xF0) | (i & 0xF);
    }

    public int getHigherNibble() {
        return value >> 4;
    }

    public void setHigherNibble(int i) {
        this.value = (this.value & 0x0F) | ((i & 0xF) << 4);
    }

    public void swap() {
        value = (getHigherNibble() << 4) | getLowerNibble();
    }

    public Bit.BitValue getBit(int index) {
        if (index > 7 || index < 0) {
            throw new IllegalArgumentException("Bit index on a byte should be in the range 0..7. Index passed: " + index);
        }
        if (((value >> index) & 1) == 1) {
            return Bit.BitValue.ONE;
        } else {
            return Bit.BitValue.ZERO;
        }
    }

    public void setBit(int index, Bit.BitValue value) {
        if (index > 7 || index < 0) {
            throw new IllegalArgumentException("Bit index on a byte should be in the range 0..7. Index passed: " + index);
        }

        if (value == Bit.BitValue.ONE) {
            this.value = this.value | (1 << index);
        } else {
            this.value = this.value & ~(1 << index);
        }

    }

}
