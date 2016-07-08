package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Short {

    private final Byte lowerByte = new Byte();
    private final Byte higherByte = new Byte();

    public Short() {
    }

    public Short(int i) {
        setValue(i);
    }

    public Short(Byte higher, Byte lower) {
        lowerByte.setValue(lower.intValue());
        higherByte.setValue(higher.intValue());
    }

    public int intValue() {
        return higherByte.intValue() << 8 | lowerByte.intValue();
    }

    public void setValue(int value) {
        lowerByte.setValue(value & 0xFF);
        higherByte.setValue(value >> 8);
    }

    public Byte getLowerByte() {
        return lowerByte;
    }

    public Byte getUpperByte() {
        return higherByte;
    }

    public void inc() {
        setValue(intValue() + 1);
    }

    public void dec() {
        setValue(intValue() - 1);
    }
}
