package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Short {

    private int value = 0;

    public Short() {
    }

    public Short(short s) {
        setValue(s);
    }

    public Short(Byte high, Byte low) {
        setValue(high, low);
    }

    public void setValue(Byte high, Byte low) {
        setHigherByte(high);
        setLowerByte(low);
    }

    public int intValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value & 0xFFFF;
    }

    public int getLowerByte() {
        return value & 0xFF;
    }

    public void setLowerByte(Byte b) {
        this.value = (short) ((this.value & 0xFF00) | b.intValue());
    }

    public int getHigherByte() {
        return (value >> 8) & 0xFF;
    }

    public void setHigherByte(Byte b) {
        this.value = (short) ((this.value & 0x00FF) | (b.intValue() << 8));
    }

    public void inc() {
        setValue(value + 1);
    }

    public void dec() {
        setValue(value - 1);
    }
}

