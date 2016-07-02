package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Byte {

    int value;

    public Byte(int i) {
        setValue(i);
    }

    public Byte() {
        this(0);
    }

    public int intValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value & 0xFF;
    }

    public void inc() {
        setValue(value + 1);
    }

    public void dec() {
        setValue(value - 1);
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
        int higherNibble = getHigherNibble();
        setHigherNibble(getLowerNibble());
        setLowerNibble(higherNibble);
    }

    public Bit getBit(int index) {
        if (index > 7 || index < 0) {
            throw new IllegalArgumentException("Bit index on a byte should be in the range 0..7. Index passed: " + index);
        }
        return new Bit((value >> index) & 1);
    }

    public void setBit(int index) {
        if (index > 7 || index < 0) {
            throw new IllegalArgumentException("Bit index on a byte should be in the range 0..7. Index passed: " + index);
        }
        value = (1 << index) | value;
    }

}
