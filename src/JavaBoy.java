import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.StringTokenizer;

import static java.lang.Integer.valueOf;

/**
 * This is the main controlling class which contains the main() method
 * to run JavaBoy as an application, and also the necessary applet methods.
 * It also implements a full command based debugger using the console.
 */

// w = 160
// h = 144

public class JavaBoy extends Frame implements ActionListener {
    private static final String hexChars = "0123456789ABCDEF";
    private boolean fullFrame = true;

    /**
     * When emulation running, references the currently loaded cartridge
     */
    Cartridge cartridge;

    /**
     * When emulation running, references the current CPU object
     */
    Dmgcpu dmgcpu;

    /**
     * Stores commands queued to be executed by the debugger
     */
    private String debuggerQueue = null;

    /**
     * True when the commands in debuggerQueue have yet to be executed
     */
    private boolean debuggerPending = false;

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
        if (dmgcpu != null) {
            int stripLength = 300;

            // Centre the GB image
            int x = getSize().width / 2 - dmgcpu.graphicsChip.getWidth() / 2;
            int y = getSize().height / 2 - dmgcpu.graphicsChip.getHeight() / 2;

            /*
      True if the image size changed last frame, and we need to repaint the background
     */
            if (!fullFrame) {

                dmgcpu.graphicsChip.draw(g, x, y, this);

            } else {
                Graphics bufferGraphics = doubleBuffer.getGraphics();

                if (dmgcpu.graphicsChip.isFrameReady()) {
                    bufferGraphics.setColor(new Color(255, 255, 255));
                    bufferGraphics.fillRect(0, 0, getSize().width, getSize().height);

                    dmgcpu.graphicsChip.draw(bufferGraphics, x, y, this);

                    g.drawImage(doubleBuffer, 0, 0, this);
                } else {
                    dmgcpu.graphicsChip.draw(bufferGraphics, x, y, this);
                }

            }

        } else {
            g.setColor(new Color(0, 0, 0));
            g.fillRect(0, 0, 160, 144);
            g.setColor(new Color(255, 255, 255));
            g.drawRect(0, 0, 160, 144);
            g.drawString("JavaBoy (tm)", 10, 10);
            g.drawString("Version 0.92", 10, 20);

            g.drawString("Charging flux capacitor...", 10, 40);
            g.drawString("Loading game ROM...", 10, 50);
        }


    }

    @Override
    public void update(Graphics g) {
        paint(g);
        fullFrame = true;
    }

    /**
     * Output a standard hex dump of memory to the console
     */
    private void hexDump(int address, int length) {
        int start = address & 0xFFF0;
        int lines = length / 16;
        if (lines == 0) lines = 1;

        for (int l = 0; l < lines; l++) {
            System.out.print(JavaBoy.hexWord(start + (l * 16)) + "   ");
            for (int r = start + (l * 16); r < start + (l * 16) + 16; r++) {
                System.out.print(JavaBoy.hexByte(unsign(dmgcpu.addressRead(r))) + " ");
            }
            System.out.print("   ");
            for (int r = start + (l * 16); r < start + (l * 16) + 16; r++) {
                char c = (char) dmgcpu.addressRead(r);
                if ((c >= 32) && (c <= 128)) {
                    System.out.print(c);
                } else {
                    System.out.print(".");
                }
            }
            System.out.println("");
        }
    }

    /**
     * Execute any pending debugger commands, or get a command from the console and execute it
     */
    private void getDebuggerMenuChoice() {
        if (debuggerPending) {
            debuggerPending = false;
            executeDebuggerCommand(debuggerQueue);
        }
    }

    /**
     * Execute debugger commands contained in a text file
     */
    private void executeDebuggerScript(String fn) {
        InputStream is;
        BufferedReader in;
        try {

            is = new FileInputStream(new File(fn));
            in = new BufferedReader(new InputStreamReader(is));

            String line;
            while ((line = in.readLine()) != null) {
                executeDebuggerCommand(line);
            }

            in.close();
        } catch (IOException e) {
            System.out.println("Can't open script file '" + fn + "'!");
        }
    }


    /**
     * Execute a debugger command which can consist of many commands separated by semicolons
     */
    private void executeDebuggerCommand(String commands) {
        StringTokenizer commandTokens = new StringTokenizer(commands, ";");

        while (commandTokens.hasMoreTokens()) {
            executeSingleDebuggerCommand(commandTokens.nextToken());
        }
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
                        hexDump(address, length);
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("Invalid number of parameters to 'd' command.");
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing hex value.");
                    }
                    break;
                case 'i':
                    try {
                        int address = valueOf(st.nextToken(), 16);
                        int length = valueOf(st.nextToken(), 16);
                        System.out.println("- Dissasembling " + JavaBoy.hexWord(length) + " instructions starting from " + JavaBoy.hexWord(address));
                        dmgcpu.disassemble(address, length);
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("Invalid number of parameters to 'i' command.");
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing hex value.");
                    }
                    break;
                case 'p':
                    try {
                        int length = valueOf(st.nextToken(), 16);
                        System.out.println("- Dissasembling " + JavaBoy.hexWord(length) + " instructions starting from program counter (" + JavaBoy.hexWord(dmgcpu.pc) + ")");
                        dmgcpu.disassemble(dmgcpu.pc, length);
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("Invalid number of parameters to 'p' command.");
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
                case 'c':
                    try {
                        String fn = st.nextToken();
                        System.out.println("* Starting execution of script '" + fn + "'");
                        executeDebuggerScript(fn);
                        System.out.println("* Script execution finished");
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("* Starting execution of default script");
                        executeDebuggerScript("default.scp");
                        System.out.println("* Script execution finished");
                    }
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
                    dmgcpu.execute(-1);
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

        MenuBar menuBar = new MenuBar();

        MenuItem fileOpen = new MenuItem("Open ROM");
        fileOpen.setActionCommand("Open ROM");
        fileOpen.addActionListener(this);
        Menu fileMenu = new Menu("File");

        fileMenu.add(fileOpen);
        menuBar.add(fileMenu);
        setMenuBar(menuBar);

        setVisible(true);
        requestFocus();
        doubleBuffer = createImage(getSize().width, getSize().height);
    }

    public static void main(String[] args) {
        System.out.println("JavaBoy (tm) Version 0.92 (c) 2005 Neil Millstone (application)");
        JavaBoy javaBoy = new JavaBoy();

        javaBoy.go();
    }

    private void go() {
        do {
            getDebuggerMenuChoice();
        } while (true);
    }

    public void actionPerformed(ActionEvent e) {
        cartridge = new Cartridge("/Users/gabrieloshiro/Developer/GitHub Deprecated Projects/javaboy/Bomberman.gb");
        dmgcpu = new Dmgcpu(cartridge, this);
        dmgcpu.reset();
        debuggerQueue = "s;g";
        debuggerPending = true;
    }

}



