import java.awt.*;
import java.util.StringTokenizer;

import static java.lang.Integer.valueOf;

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

    /**
     * When emulation running, references the currently loaded cartridge
     */
    private Cartridge cartridge;

    /**
     * When emulation running, references the current CPU object
     */
    private Dmgcpu dmgcpu;

    /**
     * Stores commands queued to be executed by the debugger
     */
    private String debuggerQueue = null;

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
     * Returns a string representation of an 8-bit number in hexadecimal
     */
    static String hexByte(int b) {
        String s = Character.toString(hexChars.charAt(b >> 4));
        s = s + Character.toString(hexChars.charAt(b & 0x0F));

        return s;
    }

    /**
     * Returns a string representation of an 16-bit number in hexadecimal
     */
    static String hexWord(int w) {
        return hexByte((w & 0x0000FF00) >> 8) + hexByte(w & 0x000000FF);
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
     * Execute a single debugger command
     */
    private void executeSingleDebuggerCommand(String command) {
        StringTokenizer st = new StringTokenizer(command, " \n");

        try {
            switch (st.nextToken().charAt(0)) {
                case 'd':
                    try {
                        int address = valueOf(st.nextToken(), 16);
                        int length = valueOf(st.nextToken(), 16);
                        System.out.println("- Dumping " + JavaBoy.hexWord(length) + " instructions starting from " + JavaBoy.hexWord(address));
                        //hexDump(address, length);
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("Invalid number of parameters to 'd' command.");
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing hex value.");
                    }
                    break;
                case 'r':
                    try {
                        String reg = st.nextToken();
                        try {
                            int val = valueOf(st.nextToken(), 16);
                            if (dmgcpu.setRegister(reg, val)) {
                                System.out.println("- Set register " + reg + " to " + JavaBoy.hexWord(val) + ".");
                            } else {
                                System.out.println("Invalid register name '" + reg + "'.");
                            }
                        } catch (java.util.NoSuchElementException e) {
                            System.out.println("Missing value");
                        } catch (NumberFormatException e) {
                            System.out.println("Error parsing hex value.");
                        }
                    } catch (java.util.NoSuchElementException e) {
                        //showRegisterValues();
                    }
                    break;
                case 's':
                    System.out.println("- CPU Reset");
                    dmgcpu.reset();
                    break;
                case 'o':
                    repaint();
                    break;
                case 'e':
                    int address;
                    try {
                        address = valueOf(st.nextToken(), 16);
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing hex value.");
                        break;
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("Missing address.");
                        break;
                    }
                    System.out.print("- Written data starting at " + JavaBoy.hexWord(address) + " (");
                    if (!st.hasMoreTokens()) {
                        System.out.println("");
                        System.out.println("Missing data value(s)");
                        break;
                    }
                    try {
                        while (st.hasMoreTokens()) {
                            short data = (byte) valueOf(st.nextToken(), 16).intValue();
                            dmgcpu.addressWrite(address++, data);
                            //           System.out.print(JavaBoy.hexByte(unsign(data)));
                            //           if (st.hasMoreTokens()) System.out.print(", ");
                        }
                        System.out.println(")");
                    } catch (NumberFormatException e) {
                        System.out.println("");
                        System.out.println("Error parsing hex value.");
                    }
                    break;
                case 'g':
                    cartridge.restoreMapping();
                    dmgcpu.execute();
                    break;
                case 'n':
                    try {
                        int state = valueOf(st.nextToken(), 16);
                        dmgcpu.interruptsEnabled = state == 1;
                    } catch (java.util.NoSuchElementException e) {
                        // Nothing!
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing hex value.");
                    }
                    System.out.print("- Interrupts are ");
                    if (dmgcpu.interruptsEnabled) System.out.println("enabled.");
                    else System.out.println("disabled.");

                    break;
                case 'm':
                    try {
                        int bank = valueOf(st.nextToken(), 16);
                        System.out.println("- Mapping ROM bank " + JavaBoy.hexByte(bank) + " to 4000 - 7FFFF");
                        cartridge.saveMapping();
                        cartridge.mapRom(bank);
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("- ROM Mapper state:");
                        System.out.println(cartridge.getMapInfo());
                    }
                    break;
            }
        } catch (java.util.NoSuchElementException e) {
            // Do nothing
        }

    }

    /**
     * Initialize JavaBoy when run as an application
     */
    private JavaBoy() {
        setSize(400, 400);

        setVisible(true);
        requestFocus();
        doubleBuffer = createImage(getSize().width, getSize().height);

        cartridge = new Cartridge("/Users/gabrieloshiro/Developer/GitHub Deprecated Projects/javaboy/Bomberman.gb");
        dmgcpu = new Dmgcpu(cartridge, this);
        dmgcpu.reset();
        debuggerQueue = "s;g";

        System.out.println("debuggerPending = true");

        StringTokenizer commandTokens = new StringTokenizer(debuggerQueue, ";");

        while (commandTokens.hasMoreTokens()) {
            executeSingleDebuggerCommand(commandTokens.nextToken());
        }



    }

    public static void main(String[] args) {
        System.out.println("JavaBoy (tm) Version 0.92 (c) 2005 Neil Millstone (application)");
        new JavaBoy();
    }

}



