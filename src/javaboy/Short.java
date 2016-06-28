package javaboy;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Short {

    private int value;

    public Short(int i) {
        intValue(i);
    }

    public Short(Byte b, Byte c) {
        setHigherByte(b);
        setLowerByte(c);
    }

    public int intValue() {
        return value;
    }

    public void intValue(int value) {
        this.value = value & 0xFFFF;
    }

    public Byte getLowerByte() {
        return new Byte(value & 0xFF);
    }

    public void setLowerByte(Byte b) {
        this.value = (this.value & 0xFF00) | b.intValue();
    }

    public Byte getHigherByte() {
        return new Byte((value >> 8) & 0xFF);
    }

    public void setHigherByte(Byte b) {
        this.value = (this.value & 0x00FF) | (b.intValue() << 8);
    }

    public void inc() {
        intValue(value + 1);
    }

    public void dec() {
        intValue(value - 1);
    }
}

