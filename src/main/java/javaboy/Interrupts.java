package javaboy;

public class Interrupts {

    /**
     * Vertical blank interrupt
     */
    public static final short INT_VBLANK = 0x01;

    /**
     * LCD Coincidence interrupt
     */
    public static final short INT_LCDC = 0x02;

    /**
     * TIMA (programmable timer) interrupt
     */
    public static final short INT_TIMA = 0x04;

    /**
     * Serial interrupt
     */
    public static final short INT_SER = 0x08;

    /**
     * P10 - P13 (Joypad) interrupt
     */
    public static final short INT_P10 = 0x10;
}
