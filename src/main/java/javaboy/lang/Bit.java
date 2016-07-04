package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-28.
 */
public class Bit {

    private boolean value = false;

    public Bit() {
    }

    public Bit(int i) {
        setValue(i);
    }

    public Bit(boolean b) {
        setValue(b);
    }

    public int intValue() {
        return value ? 1 : 0;
    }

    public boolean booleanValue() {
        return value;
    }

    public void set() {
        value = true;
    }

    public void reset() {
        value = false;
    }

    public void setValue(int i) {
        value = (i != 0);
    }

    public void setValue(boolean b) {
        value = b;
    }

    public void toggle() {
        value = !value;
    }
}
