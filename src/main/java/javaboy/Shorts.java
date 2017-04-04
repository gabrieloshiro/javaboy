package javaboy;

public class Shorts {

    /**
     * Returns the unsigned value (0 - 255) of a signed byte
     */
    public static short unsigned(byte value) {
        if (value < 0) {
            return (short) (256 + value);
        } else {
            return value;
        }
    }

    /**
     * Returns the unsigned value (0 - 255) of a signed 8-bit value stored in a short
     */
    static short unsigned(short value) {
        if (value < 0) {
            return (short) (256 + value);
        } else {
            return value;
        }
    }

}
