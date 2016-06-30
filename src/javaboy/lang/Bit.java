package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-28.
 */
public class Bit {

    private int value;

    public Bit(int i) {
        setValue(i);
    }

    public Bit() {
        this(0);
    }

    public Bit(boolean b) {
        setValue(b);
    }

    public int intValue() {
        return value;
    }

    public boolean booleanValue() {
        return value == 1;
    }

    public void set() {
        value = 1;
    }

    public void reset() {
        value = 0;
    }

    public void setValue(int i) {
        value = i & 1;
    }

    public void setValue(boolean b) {
        value = b ? 1 : 0;
    }

    public void toggle() {
        value = value ^ 1;
    }
}
