package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-28.
 */
public class Bit {

    public enum BitValue {
        ZERO,
        ONE;

        public boolean booleanValue() {
            if (this == ONE) {
                return true;
            } else {
                return false;
            }
        }

        public int intValue() {
            if (this == ONE) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    private BitValue value = BitValue.ZERO;

    public Bit() {
    }

    public Bit(BitValue value) {
        this.value = value;
    }

    public void setValue(BitValue value) {
        this.value = value;
    }

    public int intValue() {
        return value.intValue();
    }

    public boolean booleanValue() {
        return value.booleanValue();
    }

    public void set() {
        value = BitValue.ONE;
    }

    public void reset() {
        value = BitValue.ZERO;
    }

    public void toggle() {
        if (value == BitValue.ONE) {
            value = BitValue.ZERO;
        } else {
            value = BitValue.ONE;
        }
    }
}
