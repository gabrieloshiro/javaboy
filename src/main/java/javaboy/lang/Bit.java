package javaboy.lang;

public enum Bit {
    ZERO(0, false),
    ONE(1, true);

    private int intValue;
    private boolean booleanValue;

    public int intValue() {
        return intValue;
    }

    public boolean booleanValue() {
        return booleanValue;
    }

    Bit(int intValue, boolean booleanValue) {
        this.intValue = intValue;
        this.booleanValue = booleanValue;
    }

    public Bit toggle() {
        return (this == ZERO) ? ONE : ZERO;
    }

    public static Bit from(int intValue) {
        switch (intValue) {
            case 0: return ZERO;
            case 1: return ONE;
            default: throw new IllegalArgumentException();
        }
    }

    public static Bit from(boolean booleanValue) {
        if (booleanValue) {
            return ONE;
        }
        return ZERO;
    }
}
