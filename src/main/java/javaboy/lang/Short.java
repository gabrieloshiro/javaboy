package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Short {

    private final Byte lowerByte = new Byte();
    private final Byte higherByte = new Byte();

    public Short() {
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

    public Byte getHigherByte() {
        return higherByte;
    }

    public void inc() {
        setValue(intValue() + 1);
    }

    public void dec() {
        setValue(intValue() - 1);
    }
}

