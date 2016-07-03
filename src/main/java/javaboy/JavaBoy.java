package javaboy;

import java.awt.*;

public class JavaBoy extends Frame {

    private Dmgcpu dmgcpu;

    /**
     * Returns the unsigned value (0 - 255) of a signed byte
     */
    static short unsign(byte b) {
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
    public void paint(Graphics g) {
        if (dmgcpu == null) return;

           dmgcpu.graphicsChip.draw(g, 0, 0);
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Initialize JavaBoy when run as an application
     */
    private JavaBoy() {
        System.out.println("JavaBoy (tm) Version 0.92 (c) 2005 Neil Millstone (application)");

        setUndecorated(true);
        setSize(160, 144);
        setVisible(true);
        requestFocus();
        
        dmgcpu = new Dmgcpu(this, new Registers());
        System.out.println("- CPU Reset");
        dmgcpu.reset();
        dmgcpu.execute();
    }

    public static void main(String[] args) {
        new JavaBoy();
    }

}



