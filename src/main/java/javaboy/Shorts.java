package javaboy;

public class Shorts {

    /**
     * Returns the unsigned value (0 - 255) of a signed byte
     */
    public static short unsigned(byte b) {
        if (b < 0) {
            return (short) (256 + b);
        } else {
            return b;
        }
    }

    /**
     * Returns the unsigned value (0 - 255) of a signed 8-bit value stored in a short
     */
    static short unsigned(short b) {
        if (b < 0) {
            return (short) (256 + b);
        } else {
            return b;
        }
    }

}
