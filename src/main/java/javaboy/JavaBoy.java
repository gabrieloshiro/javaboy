package javaboy;

import org.pmw.tinylog.Logger;

import java.awt.*;

public class JavaBoy extends Frame {

    private Cpu cpu;

    /**
     * Returns the unsigned value (0 - 255) of a signed byte
     */
    public static short unsign(byte b) {
        if (b < 0) {
            return (short) (256 + b);
        } else {
            return b;
        }
    }

    /**
     * Returns the unsigned value (0 - 255) of a signed 8-bit value stored in a short
     */
    static short unsign(short b) {
        if (b < 0) {
            return (short) (256 + b);
        } else {
            return b;
        }
    }

    /**
     * When running as an applet, updates the screen when necessary
     */
    public void paint(Graphics graphics) {
        if (cpu == null) return;

        cpu.graphicsChip.draw(graphics, 0, 0);
    }

    @Override
    public void update(Graphics graphics) {
        paint(graphics);
    }

    /**
     * Initialize JavaBoy when run as an application
     */
    private JavaBoy() {
        Logger.debug("JavaBoy (tm) Version 0.92 (c) 2005 Neil Millstone (application)");

        setUndecorated(true);
        setSize(160, 144);
        setVisible(true);
        requestFocus();

        cpu = new Cpu(this);
        Logger.debug("CPU Reset");
        cpu.reset();
        cpu.execute();
    }

    public static void main(String[] args) {
        new JavaBoy();
    }

}



