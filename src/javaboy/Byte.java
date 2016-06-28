package javaboy;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Byte {

    int value;

    public Byte(int i) {
        setValue(i);
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

}
