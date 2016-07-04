package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Short {

    private short value = 0;

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

    public void setValue(Byte high, FlagRegister low) {
        setHigherByte(high);
        setLowerByte(low.byteValue());
    }

    public int intValue() {
        return value;
    }

    public void setValue(short value) {
        this.value = value;
    }

    public void setValue(int value) {
        this.value = (short) value;
    }

    public Byte getLowerByte() {
        return new Byte(value & 0xFF);
    }

    public void setLowerByte(Byte b) {
        this.value = (short) ((this.value & 0xFF00) | b.intValue());
    }

    public Byte getHigherByte() {
        return new Byte((value >> 8) & 0xFF);
    }

    public void setHigherByte(Byte b) {
        this.value = (short) ((this.value & 0x00FF) | (b.intValue() << 8));
    }

    public void inc() {
        setValue((short) (value + 1));
    }

    public void dec() {
        setValue((short) (value - 1));
    }
}

