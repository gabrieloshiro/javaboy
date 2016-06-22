import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.StringTokenizer;

import static java.lang.Integer.*;

/**
 * This is the main controlling class which contains the main() method
 * to run JavaBoy as an application, and also the necessary applet methods.
 * It also implements a full command based debugger using the console.
 */

// w = 160
// h = 144

public class JavaBoy extends java.applet.Applet implements Runnable, KeyListener, ActionListener {
    private static final String hexChars = "0123456789ABCDEF";
    private boolean fullFrame = true;

    /**
     * These strings contain all the names for the colour schemes.
     * A scheme can be activated using the view menu when JavaBoy is
     * running as an application.
     */
    static String[] schemeNames =
            {"Standard colours", "LCD shades", "Midnight garden", "Psychadelic"};

    /**
     * When emulation running, references the currently loaded cartridge
     */
    Cartridge cartridge;

    /**
     * When emulation running, references the current CPU object
     */
    Dmgcpu dmgcpu;

    /**
     * Stores the byte which was overwritten at the breakpoint address by the breakpoint instruction
     */
    private short breakpointInstr;

    /**
     * When set, stores the RAM address of a breakpoint.
     */
    private short breakpointAddr = -1;

    private short breakpointBank;

    /**
     * Stores commands queued to be executed by the debugger
     */
    private String debuggerQueue = null;

    /**
     * True when the commands in debuggerQueue have yet to be executed
     */
    private boolean debuggerPending = false;

    private Image doubleBuffer;

    static int[] keyCodes = {38, 40, 37, 39, 90, 88, 10, 8};

    private boolean keyListener = false;

    /**
     * True if the image size changed last frame, and we need to repaint the background
     */
    private boolean imageSizeChanged = false;

    private int stripTimer = 0;

    /**
     * Outputs a line of debugging information
     */
    static void debugLog(String s) {
        System.out.println("Debug: " + s);
    }

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

            if ((stripTimer > stripLength) && (!fullFrame) && (!imageSizeChanged)) {

                dmgcpu.graphicsChip.draw(g, x, y, this);

            } else {
                Graphics bufferGraphics = doubleBuffer.getGraphics();

                if (dmgcpu.graphicsChip.isFrameReady()) {
                    bufferGraphics.setColor(new Color(255, 255, 255));
                    bufferGraphics.fillRect(0, 0, getSize().width, getSize().height);

                    dmgcpu.graphicsChip.draw(bufferGraphics, x, y, this);


                    int stripPos = getSize().height - 40;
                    if (stripTimer < 10) {
                        stripPos = getSize().height - (stripTimer * 4);
                    }
                    if (stripTimer >= stripLength - 10) {
                        stripPos = getSize().height - 40 + ((stripTimer - (stripLength - 10)) * 4);
                    }

                    bufferGraphics.setColor(new Color(0, 0, 255));
                    bufferGraphics.fillRect(0, stripPos, getSize().width, 44);

                    bufferGraphics.setColor(new Color(128, 128, 255));
                    bufferGraphics.fillRect(0, stripPos, getSize().width, 2);

                    if (stripTimer < stripLength) {
                        if (stripTimer < stripLength / 2) {
                            bufferGraphics.setColor(new Color(255, 255, 255));
                            bufferGraphics.drawString("JavaBoy - Neil Millstone", 2, stripPos + 12);
                            bufferGraphics.setColor(new Color(255, 255, 255));
                            bufferGraphics.drawString("www.millstone.demon.co.uk", 2, stripPos + 24);
                            bufferGraphics.drawString("/download/javaboy", 2, stripPos + 36);
                        } else {
                            bufferGraphics.setColor(new Color(255, 255, 255));
                            bufferGraphics.drawString("ROM: " + cartridge.getCartName(), 2, stripPos + 12);
                            bufferGraphics.drawString("Double click for options", 2, stripPos + 24);
                            bufferGraphics.drawString("Emulator version: 0.92", 2, stripPos + 36);
                        }
                    }

                    stripTimer++;
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

    public void actionPerformed(ActionEvent e) {
    }

    public void update(Graphics g) {
        paint(g);
        fullFrame = true;
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == keyCodes[0]) {
            dmgcpu.ioHandler.padUp = true;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[1]) {
            dmgcpu.ioHandler.padDown = true;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[2]) {
            dmgcpu.ioHandler.padLeft = true;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[3]) {
            dmgcpu.ioHandler.padRight = true;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[4]) {
            dmgcpu.ioHandler.padA = true;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[5]) {
            dmgcpu.ioHandler.padB = true;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[6]) {
            dmgcpu.ioHandler.padStart = true;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[7]) {
            dmgcpu.ioHandler.padSelect = true;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        }

        switch (key) {
            case KeyEvent.VK_F1:
                if (dmgcpu.graphicsChip.frameSkip != 1)
                    dmgcpu.graphicsChip.frameSkip--;
                break;
            case KeyEvent.VK_F2:
                if (dmgcpu.graphicsChip.frameSkip != 10)
                    dmgcpu.graphicsChip.frameSkip++;
                break;
            case KeyEvent.VK_F5:
                dmgcpu.terminateProcess();
                System.out.println("- Break into debugger");
                break;
        }
    }

    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == keyCodes[0]) {
            dmgcpu.ioHandler.padUp = false;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[1]) {
            dmgcpu.ioHandler.padDown = false;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[2]) {
            dmgcpu.ioHandler.padLeft = false;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[3]) {
            dmgcpu.ioHandler.padRight = false;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[4]) {
            dmgcpu.ioHandler.padA = false;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[5]) {
            dmgcpu.ioHandler.padB = false;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[6]) {
            dmgcpu.ioHandler.padStart = false;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        } else if (key == keyCodes[7]) {
            dmgcpu.ioHandler.padSelect = false;
            dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_P10);
        }
    }

    /**
     * Output a debugger command list to the console
     */
    private void displayDebuggerHelp() {
        System.out.println("Enter a command followed by it's parameters (all values in hex):");
        System.out.println("?                     Display this help screen");
        System.out.println("c [script]            Execute _c_ommands from script file [default.scp]");
        System.out.println("s                     Re_s_et CPU");
        System.out.println("r                     Show current register values");
        System.out.println("r reg val             Set value of register reg to value val");
        System.out.println("e addr val [val] ...  Write values to RAM / ROM starting at address addr");
        System.out.println("d addr len            Hex _D_ump len bytes starting at addr");
        System.out.println("i addr len            D_i_sassemble len instructions starting at addr");
        System.out.println("p len                 Disassemble len instructions starting at current PC");
        System.out.println("n                     Show interrupt state");
        System.out.println("n 1|0                 Enable/disable interrupts");
        System.out.println("t [len]               Execute len instructions starting at current PC [1]");
        System.out.println("g                     Execute forever");
        System.out.println("o                     Output Gameboy screen to applet window");
        System.out.println("b addr                Set breakpoint at addr");
        System.out.println("k [keyname]           Toggle Gameboy key");
        System.out.println("m bank                _M_ap to ROM bank");
        System.out.println("m                     Display current ROM mapping");
        System.out.println("q                     Quit debugger interface");
        System.out.println("<CTRL> + C            Quit JavaBoy");
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
     * Output the current register values to the console
     */
    private void showRegisterValues() {
        System.out.println("- Register values");
        System.out.print("A = " + JavaBoy.hexWord(dmgcpu.a) + "    BC = " + JavaBoy.hexWord(dmgcpu.b) + JavaBoy.hexWord(dmgcpu.c));
        System.out.print("    DE = " + JavaBoy.hexByte(dmgcpu.d) + JavaBoy.hexByte(dmgcpu.e));
        System.out.print("    HL = " + JavaBoy.hexWord(dmgcpu.hl));
        System.out.print("    PC = " + JavaBoy.hexWord(dmgcpu.pc));
        System.out.println("    SP = " + JavaBoy.hexWord(dmgcpu.sp));
        System.out.println("F = " + JavaBoy.hexByte(unsign((short) dmgcpu.f)));
    }

    /**
     * Execute any pending debugger commands, or get a command from the console and execute it
     */
    private void getDebuggerMenuChoice() {
        if (dmgcpu != null) dmgcpu.terminate = false;

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
            while (((line = in.readLine()) != null) && (!dmgcpu.terminate)) {
                executeDebuggerCommand(line);
            }

            in.close();
        } catch (IOException e) {
            System.out.println("Can't open script file '" + fn + "'!");
        }
    }

    /**
     * Queue a debugger command for later execution
     */
    void queueDebuggerCommand(String command) {
        debuggerQueue = command;
        debuggerPending = true;
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

    private void setupKeyboard() {
        if (!keyListener) {
            addKeyListener(this);
            keyListener = true;
        }
    }

    /**
     * Execute a single debugger command
     */
    private void executeSingleDebuggerCommand(String command) {
        StringTokenizer st = new StringTokenizer(command, " \n");

        try {
            switch (st.nextToken().charAt(0)) {
                case '?':
                    displayDebuggerHelp();
                    break;
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
                case 'k':
                    try {
                        String keyName = st.nextToken();
                        dmgcpu.ioHandler.toggleKey(keyName);
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("Invalid number of parameters to 'k' command.");
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
                        showRegisterValues();
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
                case 'b':
                    try {
                        if (breakpointAddr != -1) {
                            cartridge.saveMapping();
                            cartridge.mapRom(breakpointBank);
                            dmgcpu.addressWrite(breakpointAddr, breakpointInstr);
                            cartridge.restoreMapping();
                            breakpointAddr = -1;
                            System.out.println("- Clearing original breakpoint");
                            dmgcpu.setBreakpoint(false);
                        }
                        int addr = valueOf(st.nextToken(), 16);
                        System.out.println("- Setting breakpoint at " + JavaBoy.hexWord(addr));
                        breakpointAddr = (short) addr;
                        breakpointInstr = dmgcpu.addressRead(addr);
                        breakpointBank = (short) cartridge.currentBank;
                        dmgcpu.addressWrite(addr, 0x52);
                        dmgcpu.setBreakpoint(true);
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("Invalid number of parameters to 'b' command.");
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing hex value.");
                    }
                    break;
                case 'g':
                    setupKeyboard();
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
                case 't':
                    try {
                        cartridge.restoreMapping();
                        int length = valueOf(st.nextToken(), 16);
                        System.out.println("- Executing " + JavaBoy.hexWord(length) + " instructions starting from program counter (" + JavaBoy.hexWord(dmgcpu.pc) + ")");
                        dmgcpu.execute(length);
                        if (dmgcpu.pc == breakpointAddr) {
                            dmgcpu.addressWrite(breakpointAddr, breakpointInstr);
                            breakpointAddr = -1;
                            System.out.println("- Breakpoint instruction restored");
                        }
                    } catch (java.util.NoSuchElementException e) {
                        System.out.println("- Executing instruction at program counter (" + JavaBoy.hexWord(dmgcpu.pc) + ")");
                        dmgcpu.execute(1);
                    } catch (NumberFormatException e) {
                        System.out.println("Error parsing hex value.");
                    }
                    break;
                default:
                    System.out.println("Command not recognized.  Try looking at the help page.");
            }
        } catch (java.util.NoSuchElementException e) {
            // Do nothing
        }

    }

    /**
     * Initialize JavaBoy when run as an application
     */
    private JavaBoy() {
        GameBoyScreen mainWindow = new GameBoyScreen("JavaBoy 0.92", this);
        mainWindow.setVisible(true);
        this.requestFocus();
    }

    public static void main(String[] args) {
        System.out.println("JavaBoy (tm) Version 0.92 (c) 2005 Neil Millstone (application)");
        JavaBoy javaBoy = new JavaBoy();

        Thread p = new Thread(javaBoy);
        p.start();
    }

    public void run() {
        do {
            try {
                getDebuggerMenuChoice();
                java.lang.Thread.sleep(1);
                this.requestFocus();
            } catch (InterruptedException e) {
                System.out.println("Interrupted!");
                break;
            }
        } while (true);
    }

    public void init() {
        requestFocus();
        doubleBuffer = createImage(getSize().width, getSize().height);
    }

}



