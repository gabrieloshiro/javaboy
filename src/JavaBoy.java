import java.awt.*;

/**
 * This is the main controlling class which contains the main() method
 * to run JavaBoy as an application, and also the necessary applet methods.
 * It also implements a full command based debugger using the console.
 */

// w = 160
// h = 144

public class JavaBoy extends Frame {

    private static final String hexChars = "0123456789ABCDEF";
    private boolean fullFrame = true;
    private Dmgcpu dmgcpu;
    private Image doubleBuffer;

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

        // Centre the GB image
        int x = getSize().width / 2 - dmgcpu.graphicsChipOld.getWidth() / 2;
        int y = getSize().height / 2 - dmgcpu.graphicsChipOld.getHeight() / 2;

        /*
              True if the image size changed last frame, and we need to repaint the background
             */
        if (!fullFrame) {

            dmgcpu.graphicsChipOld.draw(g, x, y);

        } else {
            Graphics bufferGraphics = doubleBuffer.getGraphics();

            if (dmgcpu.graphicsChipOld.isFrameReady()) {
                bufferGraphics.setColor(new Color(255, 255, 255));
                bufferGraphics.fillRect(0, 0, getSize().width, getSize().height);

                dmgcpu.graphicsChipOld.draw(bufferGraphics, x, y);

                g.drawImage(doubleBuffer, 0, 0, this);
            } else {
                dmgcpu.graphicsChipOld.draw(bufferGraphics, x, y);
            }

        }

    }

    @Override
    public void update(Graphics g) {
        paint(g);
        fullFrame = true;
    }

    /**
     * Initialize JavaBoy when run as an application
     */
    private JavaBoy() {
        System.out.println("JavaBoy (tm) Version 0.92 (c) 2005 Neil Millstone (application)");

        setSize(400, 400);
        setVisible(true);
        requestFocus();

        doubleBuffer = createImage(getSize().width, getSize().height);
        Cartridge cartridge = new Cartridge("/Users/gabrieloshiro/Developer/GitHub Deprecated Projects/javaboy/Bomberman.gb");
        dmgcpu = new Dmgcpu(cartridge, this);
        System.out.println("- CPU Reset");
        dmgcpu.reset();

        cartridge.restoreMapping();
        dmgcpu.execute();
    }

    public static void main(String[] args) {
        new JavaBoy();
    }

}



