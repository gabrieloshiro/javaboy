package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Short {

    private int value;

    public Short(int i) {
        setValue(i);
    }

    public Short(Byte high, Byte low) {
        setValue(high, low);
    }

    public Short() {
        setValue(0);
    }

    public void setValue(Byte high, Byte low) {
        setHigherByte(high);
        setLowerByte(low);
    }

    public void setValue(Byte high, FlagRegister low) {
        setHigherByte(high);
        setLowerByte(low.byteValue());
    }

    public int intValue() {
        return value;
    }

    public void setValue(int value) {
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
        setValue(value + 1);
    }

    public void dec() {
        setValue(value - 1);
    }
}

