package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-08-10.
 */
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

    public BitValue toggle() {
        return (this == ZERO) ? ONE : ZERO;
    }
}
