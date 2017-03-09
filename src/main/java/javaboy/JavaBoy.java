package javaboy;

import org.pmw.tinylog.Logger;

import java.awt.*;

public class JavaBoy extends Frame {

    private Cpu cpu;

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



