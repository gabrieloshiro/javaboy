package javaboy.lang;

import static javaboy.lang.BitValue.ONE;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Short {

    private final Byte lowerByte;
    private final Byte higherByte;

    public Short() {
        lowerByte = new Byte();
        higherByte = new Byte();
    }

    public Short(int i) {
        this();
        setValue(i);
    }

    public Short(Byte higher, Byte lower) {
        lowerByte = lower;
        higherByte = higher;
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

    public static Short signedShortFromByte(Byte value) {
        if (value.getBit(7) == ONE) {
            return new Short(value.intValue() | 0xFF00);
        }

        return new Short(value.intValue());
    }

}
