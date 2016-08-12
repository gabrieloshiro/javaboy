package javaboy;

import javaboy.lang.BitValue;
import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;
import org.pmw.tinylog.Logger;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static javaboy.lang.BitValue.ONE;
import static javaboy.lang.BitValue.ZERO;

class Dmgcpu {

    private final Byte a = new Byte();
    private final FlagRegister f = new FlagRegister();
    private final Short af = new Short(a, f);

    private final Byte b = new Byte();
    private final Byte c = new Byte();
    private final Short bc = new Short(b, c);

    private final Byte d = new Byte();
    private final Byte e = new Byte();
    private final Short de = new Short(d, e);

    private final Byte h = new Byte();
    private final Byte l = new Byte();
    private final Short hl = new Short(h, l);

    private final Short pc = new Short();
    private final Short sp = new Short();

    private byte[] rom;

    /**
     * The number of instructions that have been executed since the
     * last reset
     */
    int instrCount = 0;

    private boolean interruptsEnabled = false;

    /**
     * Used to implement the IE delay slot
     */
    private int ieDelay = -1;

    boolean timaEnabled = false;
    int instrsPerTima = 6000;

    final short INSTRS_PER_VBLANK = 9000; /* 10000  */

    /**
     * Used to set the speed of the emulator.  This controls how
     * many instructions are executed for each horizontal line scanned
     * on the screen.  Multiply by 154 to find out how many instructions
     * per frame.
     */
    private final short BASE_INSTRS_PER_HBLANK = 60;    /* 60    */
    short INSTRS_PER_HBLANK = BASE_INSTRS_PER_HBLANK;

    /**
     * Used to set the speed of DIV increments
     */
    private final short BASE_INSTRS_PER_DIV = 33;    /* 33    */

    // Constants for interrupts

    /**
     * Vertical blank interrupt
     */
    private final short INT_VBLANK = 0x01;

    /**
     * LCD Coincidence interrupt
     */
    private final short INT_LCDC = 0x02;

    /**
     * TIMA (programmable timer) interrupt
     */
    private final short INT_TIMA = 0x04;

    /**
     * Serial interrupt
     */
    private final short INT_SER = 0x08;

    /**
     * P10 - P13 (Joypad) interrupt
     */
    private final short INT_P10 = 0x10;

    // 8Kb main system RAM appears at 0xC000 in address space
    // 32Kb for GBC
    private byte[] mainRam = new byte[0x8000];

    // 256 bytes at top of RAM are used mainly for registers
    private byte[] oam = new byte[0x100];

    GraphicsChip graphicsChip;
    IoHandler ioHandler;
    private Component applet;

    Dmgcpu(Component a) {

        InputStream is;
        try {
            is = new FileInputStream(new File("/Users/gabrieloshiro/Developer/GitHub Deprecated Projects/javaboy/bgblogo.gb"));

            rom = new byte[0x08000];   // Recreate the ROM array with the correct size

            is.read(rom);
            is.close();

            Logger.debug("Loaded ROM 'bgblogo.gb'.  2 ROM banks, 32Kb.  0 RAM banks. Type: ROM Only");

        } catch (IOException e) {
            Logger.debug("Error opening ROM image");
        }


        graphicsChip = new GraphicsChip(a, this);
        ioHandler = new IoHandler(this);
        applet = a;
    }

    final short addressRead(int addr) {

        addr = addr & 0xFFFF;

        switch ((addr & 0xF000)) {
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                return rom[addr];

            case 0x8000:
            case 0x9000:
                return graphicsChip.addressRead(addr - 0x8000);

            case 0xA000:
            case 0xB000:
                return rom[addr];

            case 0xC000:
                return (mainRam[addr - 0xC000]);

            case 0xD000:
                return (mainRam[addr - 0xD000]);

            case 0xE000:
                return mainRam[addr - 0xE000];

            case 0xF000:
                if (addr < 0xFE00) {
                    return mainRam[addr - 0xE000];
                } else if (addr < 0xFF00) {
                    return (short) (oam[addr - 0xFE00] & 0x00FF);
                } else {
                    return ioHandler.ioRead(addr - 0xFF00);
                }

            default:
                Logger.debug("Tried to read address " + addr + ".  pc = " + String.format("%04X", pc.intValue()));
                return 0xFF;
        }

    }

    private void addressWrite(Short addr, Byte data) {
        addressWrite(addr.intValue(), data.intValue());
    }

    final void addressWrite(int addr, int data) {

        addr = addr & 0xFFFF;

        switch (addr & 0xF000) {
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                break;

            case 0x8000:
            case 0x9000:
                graphicsChip.addressWrite(addr - 0x8000, (byte) data);
                break;

            case 0xA000:
            case 0xB000:
                break;

            case 0xC000:
                mainRam[addr - 0xC000] = (byte) data;
                break;

            case 0xD000:
                mainRam[addr - 0xD000] = (byte) data;
                break;

            case 0xE000:
                mainRam[addr - 0xE000] = (byte) data;
                break;

            case 0xF000:
                if (addr < 0xFE00) {
                    try {
                        mainRam[addr - 0xE000] = (byte) data;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        Logger.debug("Address error: " + addr + " pc = " + String.format("%04X", pc.intValue()));
                    }
                } else if (addr < 0xFF00) {
                    oam[addr - 0xFE00] = (byte) data;
                } else {
                    ioHandler.ioWrite(addr - 0xFF00, (short) data);
                }
                break;
        }


    }

    /**
     * Performs a read of a register by internal register number
     */
    private int registerRead(int regNum) {
        switch (regNum) {
            case 0:
                return b.intValue();
            case 1:
                return c.intValue();
            case 2:
                return d.intValue();
            case 3:
                return e.intValue();
            case 4:
                return h.intValue();
            case 5:
                return l.intValue();
            case 6:
                return JavaBoy.unsign(addressRead(hl.intValue()));
            case 7:
                return a.intValue();
            default:
                return -1;
        }
    }

    /**
     * Performs a write of a register by internal register number
     */
    private void registerWrite(int regNum, int data) {
        switch (regNum) {
            case 0:
                b.setValue(data);
                break;
            case 1:
                c.setValue(data);
                break;
            case 2:
                d.setValue(data);
                break;
            case 3:
                e.setValue(data);
                break;
            case 4:
                h.setValue(data);
                break;
            case 5:
                l.setValue(data);
                break;
            case 6:
                addressWrite(hl.intValue(), data);
                break;
            case 7:
                a.setValue(data);
                break;
            default:
                break;
        }
    }

    /**
     * Resets the CPU to it's power on state.  Memory contents are not cleared.
     */
    void reset() {
        graphicsChip.dispose();
        interruptsEnabled = false;
        ieDelay = -1;
        pc.setValue(0x0100);
        sp.setValue(0xFFFE);

        f.zf(ONE);
        f.nf(ZERO);
        f.hf(ONE);
        f.cf(ONE);

        instrCount = 0;

        a.setValue(0x01);

        for (int r = 0; r < 0x8000; r++) {
            mainRam[r] = 0;
        }

        bc.setValue(0x0013);
        de.setValue(0x00D8);
        hl.setValue(0x014D);
        Logger.debug("CPU reset");
        ioHandler.reset();
    }

    /**
     * If an interrupt is enabled an the interrupt register shows that it has occured, jump to
     * the relevant interrupt vector address
     */
    private void checkInterrupts() {
        int intFlags = ioHandler.registers[0x0F];
        int ieReg = ioHandler.registers[0xFF];
        if ((intFlags & ieReg) != 0) {
            sp.dec();
            sp.dec();
            addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());  // Push current program counter onto stack
            addressWrite(sp.intValue(), pc.getLowerByte().intValue());
            interruptsEnabled = false;

            if ((intFlags & ieReg & INT_VBLANK) != 0) {
                pc.setValue(0x40);                      // Jump to Vblank interrupt address
                intFlags -= INT_VBLANK;
            } else if ((intFlags & ieReg & INT_LCDC) != 0) {
                pc.setValue(0x48);
                intFlags -= INT_LCDC;
            } else if ((intFlags & ieReg & INT_TIMA) != 0) {
                pc.setValue(0x50);
                intFlags -= INT_TIMA;
            } else if ((intFlags & ieReg & INT_SER) != 0) {
                pc.setValue(0x58);
                intFlags -= INT_SER;
            } else if ((intFlags & ieReg & INT_P10) != 0) {    // Joypad interrupt
                pc.setValue(0x60);
                intFlags -= INT_P10;
            }

            ioHandler.registers[0x0F] = (byte) intFlags;
        }
    }

    /**
     * Initiate an interrupt of the specified type
     */
    private void triggerInterrupt(int intr) {
        ioHandler.registers[0x0F] |= intr;
    }

    /**
     * Check for interrupts that need to be initiated
     */
    private void initiateInterrupts() {
        if (timaEnabled && ((instrCount % instrsPerTima) == 0)) {
            if (JavaBoy.unsign(ioHandler.registers[0x05]) == 0) {
                ioHandler.registers[0x05] = ioHandler.registers[0x06]; // Set TIMA modulo
                if ((ioHandler.registers[0xFF] & INT_TIMA) != 0)
                    triggerInterrupt(INT_TIMA);
            }
            ioHandler.registers[0x05]++;
        }

        short INSTRS_PER_DIV = BASE_INSTRS_PER_DIV;
        if ((instrCount % INSTRS_PER_DIV) == 0) {
            ioHandler.registers[0x04]++;
        }

        if ((instrCount % INSTRS_PER_HBLANK) == 0) {


            // LCY Coincidence
            // The +1 is due to the LCY register being just about to be incremented
            int cline = JavaBoy.unsign(ioHandler.registers[0x44]) + 1;
            if (cline == 152) cline = 0;

            if (((ioHandler.registers[0xFF] & INT_LCDC) != 0) &&
                    ((ioHandler.registers[0x41] & 64) != 0) &&
                    (JavaBoy.unsign(ioHandler.registers[0x45]) == cline) && ((ioHandler.registers[0x40] & 0x80) != 0) && (cline < 0x90)) {
                triggerInterrupt(INT_LCDC);
            }

            // Trigger on every line
            if (((ioHandler.registers[0xFF] & INT_LCDC) != 0) &&
                    ((ioHandler.registers[0x41] & 0x8) != 0) && ((ioHandler.registers[0x40] & 0x80) != 0) && (cline < 0x90)) {
                triggerInterrupt(INT_LCDC);
            }

            if (JavaBoy.unsign(ioHandler.registers[0x44]) == 143) {
                for (int r = 144; r < 170; r++) {
                    graphicsChip.notifyScanline(r);
                }
                if (((ioHandler.registers[0x40] & 0x80) != 0) && ((ioHandler.registers[0xFF] & INT_VBLANK) != 0)) {
                    triggerInterrupt(INT_VBLANK);
                    if (((ioHandler.registers[0x41] & 16) != 0) && ((ioHandler.registers[0xFF] & INT_LCDC) != 0)) {
                        triggerInterrupt(INT_LCDC);
                    }
                }

                if (graphicsChip.frameWaitTime >= 0) {
                    //      Logger.debug("Waiting for " + graphicsChip.frameWaitTime + "ms.");
                    try {
                        java.lang.Thread.sleep(graphicsChip.frameWaitTime);
                    } catch (InterruptedException e) {
                        // Nothing.
                    }
                }


            }


            graphicsChip.notifyScanline(JavaBoy.unsign(ioHandler.registers[0x44]));
            ioHandler.registers[0x44] = (byte) (JavaBoy.unsign(ioHandler.registers[0x44]) + 1);
            //	Logger.debug("Reg 44 = " + JavaBoy.unsign(ioHandler.registers[0x44]));

            if (JavaBoy.unsign(ioHandler.registers[0x44]) >= 153) {
                //     Logger.debug("VBlank");

                ioHandler.registers[0x44] = 0;
                graphicsChip.frameDone = false;
                applet.repaint();
                try {
                    while (!graphicsChip.frameDone) {
                        java.lang.Thread.sleep(1);
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    final void execute() {

        final FlagRegister newf = new FlagRegister();

        int dat;
        graphicsChip.startTime = System.currentTimeMillis();
        int b1, b2, b3, offset;

        while (true) {
            instrCount++;

            b1 = JavaBoy.unsign(addressRead(pc.intValue())); // opcode
            offset = addressRead(pc.intValue() + 1); // n
            b3 = JavaBoy.unsign(addressRead(pc.intValue() + 2)); // nn
            b2 = JavaBoy.unsign((short) offset); // unsigned

            pc.inc();

            switch (b1) {

                /*
                  NOP
                 */
                case 0x00:
                    break;

                /*
                  LD BC, nn
                 */
                case 0x01: {
                    Short data = loadImmediateShort(pc);
                    load(bc, data);
                    break;
                }

                /*
                  LD (BC), A
                 */
                case 0x02:
                    addressWrite(bc, a);
                    break;

                /*
                  INC BC
                 */
                case 0x03:
                    bc.inc();
                    break;

                /*
                  INC B
                */
                case 0x04:
                    inc(b);
                    break;

                /*
                  DEC B
                 */
                case 0x05:
                    dec(b);
                    break;

                /*
                  LD B, n
                 */
                case 0x06:
                    pc.inc();
                    b.setValue(b2);
                    break;

                /*
                  RLC A
                 */
                case 0x07:
                    f.setValue(0);

                    a.setValue(a.intValue() << 1);

                    if ((a.intValue() & 0x0100) != 0) {
                        f.cf(ONE);
                        a.setBit(0, ONE);
                    }
                    if (a.intValue() == 0) {
                        f.zf(ONE);
                    }
                    break;

                /*
                  LD (nn), SP   May be wrong!
                */
                case 0x08:
                    pc.inc();
                    pc.inc();
                    addressWrite((b3 << 8) + b2 + 1, sp.getUpperByte().intValue());
                    addressWrite((b3 << 8) + b2, sp.getLowerByte().intValue());
                    break;

                /*
                  ADD HL, BC
                 */
                case 0x09: {
                    int result = hl.intValue() + bc.intValue();
                    if ((result & 0xFFFF0000) != 0) {
                        f.cf(ONE);
                        result = result & 0xFFFF;
                    } else {
                        f.cf(ZERO);
                    }
                    hl.setValue(result);
                    break;
                }

                /*
                  LD A, (BC)
                 */
                case 0x0A:
                    a.setValue(JavaBoy.unsign(addressRead(bc.intValue())));
                    break;

                /*
                  DEC BC
                 */
                case 0x0B:
                    bc.dec();
                    break;

                /*
                  INC C
                 */
                case 0x0C:
                    inc(c);
                    break;

                /*
                  DEC C
                 */
                case 0x0D:
                    dec(c);
                    break;

                /*
                  LD C, n
                 */
                case 0x0E:
                    pc.inc();
                    c.setValue(b2);
                    break;

                // RRC A
                case 0x0F:
                    f.setValue(0);
                    if (a.getBit(0) == ONE) {
                        f.cf(ONE);
                    }
                    a.setValue(a.intValue() >> 1);
                    if (f.cf().intValue() == 1) {
                        a.setBit(7, ONE);
                    }
                    if (a.intValue() == 0) {
                        f.zf(ONE);
                    }
                    break;

                // STOP
                case 0x10:
                    pc.inc();
                    break;

                // LD DE, nn
                case 0x11:
                    pc.inc();
                    pc.inc();
                    d.setValue(b3);
                    e.setValue(b2);
                    break;

                // LD (DE), A
                case 0x12:
                    addressWrite(de.intValue(), a.intValue());
                    break;

                // INC DE
                case 0x13:
                    de.inc();
                    break;

                // INC D
                case 0x14:
                    inc(d);
                    break;

                // DEC D
                case 0x15:
                    dec(d);
                    break;

                // LD D, n
                case 0x16:
                    pc.inc();
                    d.setValue(b2);
                    break;

                // RL A
                case 0x17:
                    newf.setValue(0);
                    if (a.getBit(7) == ONE) {
                        newf.cf(ONE);
                    }
                    a.setValue(a.intValue() << 1);

                    if (f.cf().intValue() == 1) {
                        a.setBit(0, ONE);
                    }

                    if (a.intValue() == 0) {
                        newf.zf(ONE);
                    }
                    f.setValue(newf.intValue());
                    break;

                // JR n
                case 0x18:
                    pc.setValue(pc.intValue() + 1 + offset);
                    break;

                // ADD HL, DE
                case 0x19: {
                    int result = hl.intValue() + de.intValue();

                    if ((result & 0xFFFF0000) != 0) {
                        f.cf(ONE);
                        result = result & 0xFFFF;
                    } else {
                        f.cf(ONE);
                    }
                    hl.setValue(result);
                    break;
                }

                // LD A, (DE)
                case 0x1A:
                    a.setValue(JavaBoy.unsign(addressRead(de.intValue())));
                    break;

                // DEC DE
                case 0x1B:
                    de.inc();
                    break;

                // INC E
                case 0x1C:
                    inc(e);
                    break;

                // DEC E
                case 0x1D:
                    dec(e);
                    break;

                // LD E, n
                case 0x1E:
                    pc.inc();
                    e.setValue(b2);
                    break;

                // RR A
                case 0x1F:
                    newf.setValue(0);
                    if (a.getBit(0) == ONE) {
                        newf.cf(ONE);
                    }
                    a.setValue(a.intValue() >> 1);

                    if (f.cf().intValue() == 1) {
                        a.setBit(7, ONE);
                    }

                    if (a.intValue() == 0) {
                        newf.zf(ONE);
                    }
                    f.setValue(newf.intValue());
                    break;

                // JR NZ, n
                case 0x20:
                    pc.inc();
                    if (f.zf().intValue() == 0) {
                        pc.setValue(pc.intValue() + offset);
                    }
                    break;

                // LD HL, nn
                case 0x21:
                    pc.inc();
                    pc.inc();
                    hl.setValue((b3 << 8) + b2);
                    break;

                // LD (HL+), A
                case 0x22:
                    addressWrite(hl.intValue(), a.intValue());
                    hl.inc();
                    break;

                // INC HL
                case 0x23:
                    hl.inc();
                    break;

                // INC H         ** May be wrong **
                case 0x24:
                    inc(h);
                    break;

                // DEC H           ** May be wrong **
                case 0x25:
                    dec(h);
                    break;

                // LD H, n
                case 0x26:
                    pc.inc();
                    hl.setValue((hl.intValue() & 0x00FF) | (b2 << 8));
                    break;

                // DAA         ** This could be wrong! **
                case 0x27:
                    int upperNibble = a.getUpperNibble();
                    int lowerNibble = a.getLowerNibble();

                    newf.setValue(0);
                    newf.nf(f.nf());

                    if (f.nf().intValue() == 0) {

                        if (f.cf().intValue() == 0) {
                            if ((upperNibble <= 8) && (lowerNibble >= 0xA) &&
                                    (f.hf().intValue() == 0)) {
                                a.setValue(a.intValue() + 0x06);
                            }

                            if ((upperNibble <= 9) && (lowerNibble <= 0x3) &&
                                    (f.hf().intValue() == 1)) {
                                a.setValue(a.intValue() + 0x06);
                            }

                            if ((upperNibble >= 0xA) && (lowerNibble <= 0x9) &&
                                    (f.hf().intValue() == 0)) {
                                a.setValue(a.intValue() + 0x60);
                                newf.cf(ONE);
                            }

                            if ((upperNibble >= 0x9) && (lowerNibble >= 0xA) &&
                                    (f.hf().intValue() == 0)) {
                                a.setValue(a.intValue() + 0x66);
                                newf.cf(ONE);
                            }

                            if ((upperNibble >= 0xA) && (lowerNibble <= 0x3) &&
                                    (f.hf().intValue() == 1)) {
                                a.setValue(a.intValue() + 0x66);
                                newf.cf(ONE);
                            }

                        } else {  // If carry set

                            if ((upperNibble <= 0x2) && (lowerNibble <= 0x9) &&
                                    (f.hf().intValue() == 0)) {
                                a.setValue(a.intValue() + 0x60);
                                newf.cf(ONE);
                            }

                            if ((upperNibble <= 0x2) && (lowerNibble >= 0xA) &&
                                    (f.hf().intValue() == 0)) {
                                a.setValue(a.intValue() + 0x66);
                                newf.cf(ONE);
                            }

                            if ((upperNibble <= 0x3) && (lowerNibble <= 0x3) &&
                                    (f.hf().intValue() == 1)) {
                                a.setValue(a.intValue() + 0x66);
                                newf.cf(ONE);
                            }

                        }

                    } else { // Subtract is set

                        if (f.cf().intValue() == 0) {

                            if ((upperNibble <= 0x8) && (lowerNibble >= 0x6) &&
                                    (f.hf().intValue() == 1)) {
                                a.setValue(a.intValue() + 0xFA);
                            }

                        } else { // Carry is set

                            if ((upperNibble >= 0x7) && (lowerNibble <= 0x9) &&
                                    (f.hf().intValue() == 0)) {
                                a.setValue(a.intValue() + 0xA0);
                                newf.cf(ONE);
                            }

                            if ((upperNibble >= 0x6) && (lowerNibble >= 0x6) &&
                                    (f.hf().intValue() == 1)) {
                                a.setValue(a.intValue() + 0x9A);
                                newf.cf(ONE);
                            }

                        }

                    }

                    if (a.intValue() == 0) {
                        newf.zf(ONE);
                    }

                    f.setValue(newf.intValue());

                    break;

                // JR Z, n
                case 0x28:
                    pc.inc();

                    if (f.zf().intValue() == 1) {
                        pc.setValue(pc.intValue() + offset);
                    }
                    break;

                // ADD HL, HL
                case 0x29: {
                    int result = hl.intValue() + hl.intValue();
                    if ((result & 0xFFFF0000) != 0) {
                        f.cf(ONE);
                        result = result & 0xFFFF;
                    } else {
                        f.cf(ZERO);
                    }
                    hl.setValue(result);
                    break;
                }

                // LDI A, (HL)
                case 0x2A:
                    a.setValue(JavaBoy.unsign(addressRead(hl.intValue())));
                    hl.inc();
                    break;

                // DEC HL
                case 0x2B:
                    hl.dec();
                    break;

                // INC L
                case 0x2C:
                    inc(l);
                    break;

                // DEC L
                case 0x2D:
                    dec(l);
                    break;

                // LD L, n
                case 0x2E:
                    pc.inc();
                    l.setValue(b2);
                    break;

                // CPL A
                case 0x2F:
                    a.setValue((~a.intValue()));
                    f.nf(ONE);
                    f.hf(ONE);
                    break;

                // JR NC, n
                case 0x30:
                    pc.inc();
                    if (f.cf().intValue() == 0) {
                        pc.setValue(pc.intValue() + offset);
                    }
                    break;

                // LD SP, nn
                case 0x31:
                    pc.inc();
                    pc.inc();
                    sp.setValue((b3 << 8) + b2);
                    break;

                // LD (HL-), A
                case 0x32:
                    addressWrite(hl.intValue(), a.intValue());
                    hl.dec();
                    break;

                // INC SP
                case 0x33:
                    sp.inc();
                    break;

                // INC (HL)
                case 0x34:
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);

                    dat = JavaBoy.unsign(addressRead(hl.intValue()));
                    switch (dat) {
                        case 0xFF:
                            f.zf(ONE);
                            f.hf(ONE);
                            addressWrite(hl.intValue(), 0x00);
                            break;
                        case 0x0F:
                            f.hf(ONE);
                            addressWrite(hl.intValue(), 0x10);
                            break;
                        default:
                            addressWrite(hl.intValue(), dat + 1);
                            break;
                    }
                    break;

                // DEC (HL)
                case 0x35:
                    f.zf(ZERO);
                    f.nf(ONE);
                    f.hf(ZERO);

                    dat = JavaBoy.unsign(addressRead(hl.intValue()));
                    switch (dat) {
                        case 0x00:
                            f.hf(ONE);
                            addressWrite(hl.intValue(), 0xFF);
                            break;
                        case 0x10:
                            f.hf(ONE);
                            addressWrite(hl.intValue(), 0x0F);
                            break;
                        case 0x01:
                            f.zf(ONE);
                            addressWrite(hl.intValue(), 0x00);
                            break;
                        default:
                            addressWrite(hl.intValue(), dat - 1);
                            break;
                    }
                    break;

                // LD (HL), n
                case 0x36:
                    pc.inc();
                    addressWrite(hl.intValue(), b2);
                    break;

                // SCF
                case 0x37:
                    f.nf(ZERO);
                    f.hf(ZERO);
                    f.cf(ONE);
                    break;

                // JR C, n
                case 0x38:
                    pc.inc();
                    if (f.cf().booleanValue()) {
                        pc.setValue(pc.intValue() + offset);
                    }
                    break;

                // ADD HL, SP      ** Could be wrong **
                case 0x39: {
                    int result = hl.intValue() + sp.intValue();
                    if ((result & 0xFFFF0000) != 0) {
                        f.cf(ONE);
                        result = result & 0xFFFF;
                    } else {
                        f.cf(ZERO);
                    }
                    hl.setValue(result);
                    break;
                }

                // LD A, (HL-)
                case 0x3A:
                    a.setValue(JavaBoy.unsign(addressRead(hl.intValue())));
                    hl.dec();
                    break;

                // DEC SP
                case 0x3B:
                    sp.dec();
                    break;

                // INC A
                case 0x3C:
                    inc(a);
                    break;

                // DEC A
                case 0x3D:
                    dec(a);
                    break;

                // LD A, n
                case 0x3E:
                    pc.inc();
                    a.setValue(b2);
                    break;

                // CCF
                case 0x3F:
                    f.nf(ZERO);
                    f.hf(ZERO);

                    if (f.cf().intValue() == 0) {
                        f.cf(ONE);
                    } else {
                        f.cf(ZERO);
                    }
                    break;

                case 0x52:
                    break;

                // HALT
                case 0x76:
                    interruptsEnabled = true;
                    while (ioHandler.registers[0x0F] == 0) {
                        initiateInterrupts();
                        instrCount++;
                    }
                    break;

                // XOR A, A (== LD A, 0)
                case 0xAF:
                    xor(a, a);
                    break;

                // RET NZ
                case 0xC0:
                    if (f.zf().intValue() == 0) {
                        pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                        sp.inc();
                        sp.inc();
                    }
                    break;

                // POP BC
                case 0xC1:
                    c.setValue(JavaBoy.unsign(addressRead(sp.intValue())));
                    b.setValue(JavaBoy.unsign(addressRead(sp.intValue() + 1)));
                    sp.inc();
                    sp.inc();
                    break;

                // JP NZ, n
                case 0xC2:
                    pc.inc();
                    pc.inc();

                    if (f.zf().intValue() == 0) {
                        pc.setValue((b3 << 8) + b2);
                    }
                    break;

                // JP nn
                case 0xC3:
                    pc.setValue((b3 << 8) + b2);
                    break;

                // CALL NZ, nn
                case 0xC4:
                    pc.inc();
                    pc.inc();
                    if (f.zf().intValue() == 0) {
                        sp.dec();
                        sp.dec();
                        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                        pc.setValue((b3 << 8) + b2);
                    }
                    break;

                // PUSH BC
                case 0xC5:
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue(), c.intValue());
                    addressWrite(sp.intValue() + 1, b.intValue());
                    break;

                // ADD A, n
                case 0xC6: {
                    pc.inc();
                    add(a, new Byte(b2));
                    break;
                }

                // RST 08
                case 0xCF:
                    rst(0x08);
                    break;

                // RET Z
                case 0xC8:
                    if (f.zf().intValue() == 1) {
                        pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                        sp.inc();
                        sp.inc();
                    }
                    break;

                // RET
                case 0xC9:
                    pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                    sp.inc();
                    sp.inc();
                    break;

                // JP Z, nn
                case 0xCA:
                    pc.inc();
                    pc.inc();
                    if (f.zf().intValue() == 1) {
                        pc.setValue((b3 << 8) + b2);
                    }
                    break;

                // Shift/bit test
                case 0xCB: {
                    pc.inc();
                    int regNum = b2 & 0x07;
                    int data = registerRead(regNum);
                    if ((b2 & 0xC0) == 0) {
                        switch ((b2 & 0xF8)) {

                            // RLC A
                            case 0x00:
                                f.setValue(0);
                                if ((data & 0x80) == 0x80) {
                                    f.cf(ONE);
                                }
                                data <<= 1;
                                if (f.cf().intValue() == 1) {
                                    data |= 1;
                                }

                                data &= 0xFF;
                                if (data == 0) {
                                    f.zf(ONE);
                                }
                                registerWrite(regNum, data);
                                break;

                            // RRC A
                            case 0x08:
                                f.setValue(0);
                                if ((data & 0x01) == 0x01) {
                                    f.cf(ONE);
                                }
                                data >>= 1;
                                if (f.cf().intValue() == 1) {
                                    data |= 0x80;
                                }
                                if (data == 0) {
                                    f.zf(ONE);
                                }
                                registerWrite(regNum, data);
                                break;

                            // RL r
                            case 0x10:
                                newf.setValue(0);
                                if ((data & 0x80) == 0x80) {
                                    newf.cf(ONE);
                                }
                                data <<= 1;

                                if (f.cf().intValue() == 1) {
                                    data |= 1;
                                }

                                data &= 0xFF;
                                if (data == 0) {
                                    newf.zf(ONE);
                                }
                                f.setValue(newf.intValue());
                                registerWrite(regNum, data);
                                break;

                            // RR r
                            case 0x18:
                                newf.setValue(0);
                                if ((data & 0x01) == 0x01) {
                                    newf.cf(ONE);

                                }
                                data >>= 1;

                                if (f.cf().intValue() == 1) {
                                    data |= 0x80;
                                }

                                if (data == 0) {
                                    newf.zf(ONE);
                                }
                                f.setValue(newf.intValue());
                                registerWrite(regNum, data);
                                break;

                            // SLA r
                            case 0x20:
                                f.setValue(0);
                                if ((data & 0x80) == 0x80) {
                                    f.cf(ONE);
                                }

                                data <<= 1;

                                data &= 0xFF;
                                if (data == 0) {
                                    f.zf(ONE);
                                }
                                registerWrite(regNum, data);
                                break;

                            // SRA r
                            case 0x28:
                                short topBit;

                                topBit = (short) (data & 0x80);
                                f.setValue(0);
                                if ((data & 0x01) == 0x01) {
                                    f.cf(ONE);
                                }

                                data >>= 1;
                                data |= topBit;

                                if (data == 0) {
                                    f.zf(ONE);
                                }
                                registerWrite(regNum, data);
                                break;

                            // SWAP r
                            case 0x30:

                                data = (short) (((data & 0x0F) << 4) | ((data & 0xF0) >> 4));
                                f.setValue(0);
                                if (data == 0) {
                                    f.zf(ONE);
                                }
                                registerWrite(regNum, data);
                                break;

                            // SRL r
                            case 0x38:
                                f.setValue(0);
                                if ((data & 0x01) == 0x01) {
                                    f.cf(ONE);
                                }

                                data >>= 1;

                                if (data == 0) {
                                    f.zf(ONE);
                                }
                                registerWrite(regNum, data);
                                break;
                        }
                    } else {

                        int mask;
                        int bitNumber = (b2 & 0x38) >> 3;

                        // BIT n, r
                        if ((b2 & 0xC0) == 0x40) {
                            mask = (short) (0x01 << bitNumber);
                            f.nf(ZERO);
                            f.hf(ONE);
                            if ((data & mask) != 0) {
                                f.zf(ZERO);
                            } else {
                                f.zf(ONE);
                            }
                        }

                        // RES n, r
                        if ((b2 & 0xC0) == 0x80) {
                            mask = (short) (0xFF - (0x01 << bitNumber));
                            data = (short) (data & mask);
                            registerWrite(regNum, data);
                        }

                        // SET n, r
                        if ((b2 & 0xC0) == 0xC0) {
                            mask = (short) (0x01 << bitNumber);
                            data = (short) (data | mask);
                            registerWrite(regNum, data);
                        }
                    }

                    break;
                }

                // CALL Z, nn
                case 0xCC:
                    pc.inc();
                    pc.inc();
                    if (f.zf().intValue() == 1) {
                        sp.dec();
                        sp.dec();
                        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                        pc.setValue((b3 << 8) + b2);
                    }
                    break;

                // CALL nn
                case 0xCD:
                    pc.inc();
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue((b3 << 8) + b2);
                    break;

                /*
                *  ADC A, n
                */
                case 0xCE: {
                    pc.inc();
                    adc(a, new Byte(b2), f.cf());
                    break;
                }

                // RST 00
                case 0xC7:
                    rst(0x00);
                    break;

                // RET NC
                case 0xD0:
                    if (f.cf().intValue() == 0) {
                        pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                        sp.inc();
                        sp.inc();
                    }
                    break;

                // POP DE
                case 0xD1:
                    e.setValue(JavaBoy.unsign(addressRead(sp.intValue())));
                    d.setValue(JavaBoy.unsign(addressRead(sp.intValue() + 1)));
                    sp.inc();
                    sp.inc();
                    break;

                // JP NC, nn
                case 0xD2:
                    pc.inc();
                    pc.inc();
                    if (f.cf().intValue() == 0) {
                        pc.setValue((b3 << 8) + b2);
                    }
                    break;

                // CALL NC, nn
                case 0xD4:
                    pc.inc();
                    pc.inc();
                    if (f.cf().intValue() == 0) {
                        sp.dec();
                        sp.dec();
                        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                        pc.setValue((b3 << 8) + b2);
                    }
                    break;

                // PUSH DE
                case 0xD5:
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue(), e.intValue());
                    addressWrite(sp.intValue() + 1, d.intValue());
                    break;

                // SUB A, n
                case 0xD6: {
                    pc.inc();
                    sub(a, new Byte(b2));
                    break;
                }

                // RST 10
                case 0xD7:
                    rst(0x10);
                    break;

                // RET C
                case 0xD8:
                    if (f.cf().intValue() == 1) {
                        pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                        sp.inc();
                        sp.inc();
                    }
                    break;

                // RETI
                case 0xD9:
                    interruptsEnabled = true;
                    pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                    sp.inc();
                    sp.inc();
                    break;

                // JP C, nn
                case 0xDA:
                    pc.inc();
                    pc.inc();
                    if (f.cf().intValue() == 1) {
                        pc.setValue((b3 << 8) + b2);
                    }
                    break;

                // CALL C, nn
                case 0xDC:
                    pc.inc();
                    pc.inc();
                    if (f.cf().intValue() == 1) {
                        sp.dec();
                        sp.dec();
                        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                        pc.setValue((b3 << 8) + b2);
                    }
                    break;

                // SBC A, n
                case 0xDE: {
                    pc.inc();
                    sbc(a, new Byte(b2), f.cf());
                    break;
                }

                // RST 18
                case 0xDF:
                    rst(0x18);
                    break;

                // LDH (n), A
                case 0xE0:
                    pc.inc();
                    addressWrite(0xFF00 + b2, a.intValue());
                    break;

                // POP HL
                case 0xE1:
                    hl.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                    sp.inc();
                    sp.inc();
                    break;

                // LDH (FF00 + C), A
                case 0xE2:
                    addressWrite(0xFF00 + c.intValue(), a.intValue());
                    break;

                // PUSH HL
                case 0xE5:
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, h.intValue());
                    addressWrite(sp.intValue(), l.intValue());
                    break;

                // AND n
                case 0xE6:
                    pc.inc();
                    and(a, new Byte(b2));
                    break;

                // RST 20
                case 0xE7:
                    rst(0x20);
                    break;

                // ADD SP, nn
                case 0xE8: {
                    pc.inc();
                    int result = sp.intValue() + offset;
                    if ((result & 0xFFFF0000) != 0) {
                        f.cf(ONE);
                    } else {
                        f.cf(ZERO);
                    }
                    sp.setValue(result);
                    break;
                }

                // JP (HL)
                case 0xE9:
                    pc.setValue(hl.intValue());
                    break;

                // LD (nn), A
                case 0xEA:
                    pc.inc();
                    pc.inc();
                    addressWrite((b3 << 8) + b2, a.intValue());
                    break;

                // XOR A, n
                case 0xEE: {
                    Byte data = loadImmediateByte(pc);
                    xor(a, data);
                    break;
                }

                // RST 28
                case 0xEF:
                    rst(0x28);
                    break;

                // LDH A, (n)
                case 0xF0:
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead(0xFF00 + b2)));
                    break;

                // POP AF
                case 0xF1:
                    f.setValue(addressRead(sp.intValue()));
                    a.setValue(JavaBoy.unsign(addressRead(sp.intValue() + 1)));
                    sp.inc();
                    sp.inc();
                    break;

                // LD A, (FF00 + C)
                case 0xF2:
                    a.setValue(JavaBoy.unsign(addressRead(0xFF00 + c.intValue())));
                    break;

                // DI
                case 0xF3:
                    interruptsEnabled = false;
                    break;

                // PUSH AF
                case 0xF5:
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue(), f.intValue());
                    addressWrite(sp.intValue() + 1, a.intValue());
                    break;

                // OR A, n
                case 0xF6:
                    pc.inc();
                    or(a, new Byte(b2));
                    break;

                // RST 30
                case 0xF7:
                    rst(0x30);
                    break;

                // LD HL, SP + n  ** HALFCARRY FLAG NOT SET ***
                case 0xF8: {
                    pc.inc();
                    int result = sp.intValue() + offset;
                    f.setValue(0);
                    if ((result & 0x10000) != 0) {
                        f.cf(ONE);
                        result = result & 0xFFFF;
                    }
                    hl.setValue(result);
                    break;
                }

                // LD SP, HL
                case 0xF9:
                    sp.setValue(hl.intValue());
                    break;

                // LD A, (nn)
                case 0xFA:
                    pc.inc();
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead((b3 << 8) + b2)));
                    break;

                // EI
                case 0xFB:
                    ieDelay = 1;
                    break;

                // CP n     ** FLAGS ARE WRONG! **
                case 0xFE:
                    pc.inc();
                    f.setValue(0);
                    if (b2 == a.intValue()) {
                        f.zf(ONE);
                    } else {
                        if (a.intValue() < b2) {
                            f.cf(ONE);
                        }
                    }
                    break;

                // RST 38
                case 0xFF:
                    rst(0x38);
                    break;

                default:

                    if ((b1 & 0xC0) == 0x80) {       // Byte 0x10?????? indicates ALU op
                        int operand = registerRead(b1 & 0x07);
                        switch ((b1 & 0x38) >> 3) {

                            // ADC A, r
                            case 1:
                                adc(a, new Byte(operand), f.cf());
                                break;

                            // ADD A, r
                            case 0: {
                                add(a, new Byte(operand));
                                break;
                            }

                            // SBC A, r
                            case 3:
                                sbc(a, new Byte(operand), f.cf());
                                break;

                            // SUB A, r
                            case 2: {
                                sub(a, new Byte(operand));
                                break;
                            }

                            // AND A, r
                            case 4:
                                a.setValue(a.intValue() & operand);
                                and(a, new Byte(operand));
                                break;

                            // XOR A, r
                            case 5:
                                xor(a, new Byte(operand));
                                break;

                            // OR A, r
                            case 6:
                                or(a, new Byte(operand));
                                break;

                            // CP A, r (compare)
                            case 7:
                                f.setValue(0);
                                f.nf(ONE);
                                if (a.intValue() == operand) {
                                    f.zf(ONE);
                                }
                                if (a.intValue() < operand) {
                                    f.cf(ONE);
                                }
                                if ((a.intValue() & 0x0F) < (operand & 0x0F)) {
                                    f.hf(ONE);
                                }
                                break;
                        }

                    } else if ((b1 & 0xC0) == 0x40) {   // Byte 0x01xxxxxxx indicates 8-bit ld
                        registerWrite((b1 & 0x38) >> 3, registerRead(b1 & 0x07));
                    } else {
                        Logger.debug("Unrecognized opcode (" + String.format("%02X", b1) + ")");
                        break;
                    }
            }


            if (ieDelay != -1) {

                if (ieDelay > 0) {
                    ieDelay--;
                } else {
                    interruptsEnabled = true;
                    ieDelay = -1;
                }

            }


            if (interruptsEnabled) {
                checkInterrupts();
            }

            initiateInterrupts();
        }
    }

    private void rst(int address) {
        sp.dec();
        sp.dec();
        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
        pc.setValue(address);

    }

    private Byte loadImmediateByte(Short address) {
        Byte immediate = new Byte();
        immediate.setValue(addressRead(address.intValue()));
        address.inc();
        return immediate;
    }

    private Short loadImmediateShort(Short address) {
        Short immediate = new Short();
        Byte lowerByte = loadImmediateByte(address);
        immediate.getLowerByte().setValue(lowerByte.intValue());
        Byte upperByte = loadImmediateByte(address);
        immediate.getUpperByte().setValue(upperByte.intValue());

        return immediate;
    }

    public void adc(Byte left, Byte right, BitValue carry) {

        f.nf(ZERO);

        int lowerResult = left.getLowerNibble() + right.getLowerNibble() + carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            f.hf(ONE);
        } else {
            f.hf(ZERO);
        }

        int result = left.intValue() + right.intValue() + carry.intValue();

        if ((result & 0x100) == 0x100) {
            f.cf(ONE);
        } else {
            f.cf(ZERO);
        }

        if ((result & 0xFF) == 0) {
            f.zf(ONE);
        } else {
            f.zf(ZERO);
        }

        left.setValue(result);
    }

    public void add(Byte left, Byte right) {
        adc(left, right, ZERO);
    }

    public void inc(Byte left) {
        add(left, new Byte(1));
    }

    public void sbc(Byte left, Byte right, BitValue carry) {
        f.nf(ONE);

        int lowerResult = left.getLowerNibble() - right.getLowerNibble() - carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            f.hf(ONE);
        } else {
            f.hf(ZERO);
        }

        int result = left.intValue() - right.intValue() - carry.intValue();

        if ((result & 0x100) == 0x100) {
            f.cf(ONE);
        } else {
            f.cf(ZERO);
        }

        if ((result & 0xFF) == 0) {
            f.zf(ONE);
        } else {
            f.zf(ZERO);
        }

        left.setValue(result);
    }

    public void sub(Byte left, Byte right) {
        sbc(left, right, ZERO);
    }

    public void cp(Byte left, Byte right) {
        int originalValue = left.intValue();
        sub(left, right);
        left.setValue(originalValue);
    }

    public void dec(Byte left) {
        sub(left, new Byte(1));
    }

    public void or(Byte left, Byte right) {
        int result = left.intValue() | right.intValue();

        f.setValue(0);

        if (result == 0) {
            f.zf(ONE);
        }

        left.setValue(result);
    }

    private void xor(Byte left, Byte right) {
        int result = left.intValue() ^ right.intValue();

        f.setValue(0);

        if (result == 0) {
            f.zf(ONE);
        }

        left.setValue(result);
    }

    private void and(Byte left, Byte right) {
        int result = left.intValue() & right.intValue();

        f.nf(ZERO);
        f.hf(ONE);
        f.cf(ZERO);

        if (result == 0) {
            f.zf(ONE);
        } else {
            f.zf(ZERO);
        }

        left.setValue(result);
    }

    private void load(Byte destination, Byte source) {
        destination.setValue(source.intValue());
    }

    private void load(Short destination, Short source) {
        destination.setValue(source.intValue());
    }
}
