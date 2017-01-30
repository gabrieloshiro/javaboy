package javaboy;

public class GraphicsConstants {

    public static final int INSTRS_PER_VBLANK = 9000;

    /**
     * Used to set the speed of the emulator.  This controls how
     * many instructions are executed for each horizontal line scanned
     * on the screen.  Multiply by 154 to find out how many instructions
     * per frame.
     */
    public static final int BASE_INSTRS_PER_HBLANK = 60;
    public static final int INSTRS_PER_HBLANK = BASE_INSTRS_PER_HBLANK;

    /**
     * Used to set the speed of DIV increments
     */
    public static final short BASE_INSTRS_PER_DIV = 33;


}
