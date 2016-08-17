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

    private Byte read(Short addr) {
        return new Byte(addressRead(addr.intValue()));
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

    private void write(Short addr, Byte data) {
        addressWrite(addr.intValue(), data.intValue());
    }

    private void write(Short addr, Short data) {
        addressWrite(addr.intValue(), data.getLowerByte().intValue());
        addressWrite(addr.intValue() + 1, data.getUpperByte().intValue());
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

        graphicsChip.startTime = System.currentTimeMillis();

        while (true) {
            instrCount++;

            Byte opcode = loadImmediateByte(pc);

            switch (opcode.intValue()) {

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
                    write(bc, a);
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
                case 0x06: {
                    Byte data = loadImmediateByte(pc);
                    load(b, data);
                    break;
                }

                /*
                  RLCA
                 */
                case 0x07:
                    rlca(a);
                    break;

                /*
                  LD (nn), SP
                */
                case 0x08: {
                    Short address = loadImmediateShort(pc);
                    write(address, sp);
                    break;
                }

                /*
                  ADD HL, BC
                 */
                case 0x09: {
                    add(hl, bc);
                    break;
                }

                /*
                  LD A, (BC)
                 */
                case 0x0A: {
                    Byte data = read(bc);
                    a.setValue(data);
                    break;
                }

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
                case 0x0E: {
                    Byte data = loadImmediateByte(pc);
                    load(c, data);
                    break;
                }

                /*
                 RRCA
                 */
                case 0x0F:
                    rrca(a);
                    break;

                /*
                 STOP
                 */
                case 0x10:
                    pc.inc();
                    break;

                /*
                 LD DE, nn
                 */
                case 0x11: {
                    Short data = loadImmediateShort(pc);
                    de.setValue(data.intValue());
                    break;
                }

                /*
                 LD (DE), A
                 */
                case 0x12:
                    addressWrite(de.intValue(), a.intValue());
                    break;

                /*
                 INC DE
                 */
                case 0x13:
                    de.inc();
                    break;

                /*
                 INC D
                 */
                case 0x14:
                    inc(d);
                    break;

                /*
                 DEC D
                 */
                case 0x15:
                    dec(d);
                    break;

                /*
                 LD D, n
                 */
                case 0x16: {
                    Byte data = loadImmediateByte(pc);
                    load(d, data);
                    break;
                }

                /*
                 RL A
                 */
                case 0x17:
                    rl(a, f.cf());
                    break;

                /*
                 JR n
                 */
                case 0x18: {
                    Byte offset = loadImmediateByte(pc);
                    jr(true, offset);
                    break;
                }

                /*
                 ADD HL, DE
                 */
                case 0x19: {
                    add(hl, de);
                    break;
                }

                /*
                 LD A, (DE)
                 */
                case 0x1A: {
                    Byte data = read(de);
                    load(a, data);
                    break;
                }

                /*
                 DEC DE
                 */
                case 0x1B:
                    de.inc();
                    break;

                /*
                 INC E
                 */
                case 0x1C:
                    inc(e);
                    break;

                /*
                 DEC E
                 */
                case 0x1D:
                    dec(e);
                    break;

                /*
                LD E, n
                */
                case 0x1E: {
                    Byte data = loadImmediateByte(pc);
                    load(e, data);
                    break;
                }

                /*
                 RR A
                 */
                case 0x1F: {
                    rra(a, f.cf());
                    break;
                }

                /*
                 JR NZ, n
                  */
                case 0x20: {
                    Byte address = loadImmediateByte(pc);
                    jr(f.zf() == ZERO, address);
                }
                break;

                /*
                 LD HL, nn
                 */
                case 0x21: {
                    Short address = loadImmediateShort(pc);
                    load(hl, address);
                    break;
                }

                /*
                 LDH (HL), A
                 */
                case 0x22:
                    addressWrite(hl.intValue(), a.intValue());
                    hl.inc();
                    break;

                /*
                 INC HL
                 */
                case 0x23:
                    hl.inc();
                    break;

                /*
                 INC H
                 */
                case 0x24:
                    inc(h);
                    break;

                /*
                 DEC H
                 */
                case 0x25:
                    dec(h);
                    break;

                /*
                 LD H, n
                 */
                case 0x26: {
                    Byte data = loadImmediateByte(pc);
                    load(h, data);
                    break;
                }

                /*
                 DAA
                 */
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

                /*
                 JR Z, n
                 */
                case 0x28: {
                    Byte address = loadImmediateByte(pc);
                    jr(f.zf() == ONE, address);
                    break;
                }

                /*
                 ADD HL, HL
                 */
                case 0x29: {
                    add(hl, hl);
                    break;
                }

                /*
                 LDI A, (HL)
                 */
                case 0x2A:
                    a.setValue(JavaBoy.unsign(addressRead(hl.intValue())));
                    hl.inc();
                    break;

                /*
                 DEC HL
                 */
                case 0x2B:
                    hl.dec();
                    break;

                /*
                 INC L
                 */
                case 0x2C:
                    inc(l);
                    break;

                /*
                 DEC L
                 */
                case 0x2D:
                    dec(l);
                    break;

                /*
                 LD L, n
                 */
                case 0x2E: {
                    Byte address = loadImmediateByte(pc);
                    load(l, address);
                    break;
                }

                /*
                 CPL A
                 */
                case 0x2F:
                    a.setValue((~a.intValue()));
                    f.nf(ONE);
                    f.hf(ONE);
                    break;

                /*
                 JR NC, n
                 */
                case 0x30: {
                    Byte address = loadImmediateByte(pc);
                    jr(f.cf() == ZERO, address);
                    break;
                }

                /*
                 LD SP, nn
                 */
                case 0x31: {
                    Short address = loadImmediateShort(pc);
                    load(sp, address);
                    break;
                }

                /*
                 LD (HL-), A
                 */
                case 0x32:
                    addressWrite(hl.intValue(), a.intValue());
                    hl.dec();
                    break;

                /*
                 INC SP
                 */
                case 0x33:
                    sp.inc();
                    break;

                /*
                 INC (HL)
                  */
                case 0x34: {
                    Byte data = read(hl);
                    inc(data);
                    write(hl, data);
                    break;
                }

                /*
                 DEC (HL)
                 */
                case 0x35:
                    hl.dec();
                    break;

                /*
                 LD (HL), n
                 */
                case 0x36: {
                    Byte address = loadImmediateByte(pc);
                    write(hl, address);
                    break;
                }

                /*
                 SCF
                 */
                case 0x37:
                    f.nf(ZERO);
                    f.hf(ZERO);
                    f.cf(ONE);
                    break;

                /*
                 JR C, n
                 */
                case 0x38: {
                    Byte address = loadImmediateByte(pc);
                    jr(f.cf() == ONE, address);
                    break;
                }

                /*
                 ADD HL, SP
                 */
                case 0x39: {
                    add(hl, sp);
                    break;
                }

                /*
                 LD A, (HL-)
                 */
                case 0x3A:
                    a.setValue(JavaBoy.unsign(addressRead(hl.intValue())));
                    hl.dec();
                    break;

                // DEC SP
                case 0x3B:
                    sp.dec();
                    break;

                /*
                 INC A
                 */
                case 0x3C:
                    inc(a);
                    break;

                /*
                 DEC A
                 */
                case 0x3D:
                    dec(a);
                    break;

                /*
                 LD A, n
                 */
                case 0x3E: {
                    Byte data = loadImmediateByte(pc);
                    load(a, data);
                    break;
                }

                /*
                 CCF
                 */
                case 0x3F:
                    f.nf(ZERO);
                    f.hf(ZERO);
                    f.cf(f.cf().toggle());
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

                /*
                 XOR A, A
                 */
                case 0xAF:
                    xor(a, a);
                    break;

                /*
                 RET NZ
                 */
                case 0xC0:
                    ret(f.zf() == ZERO, sp);
                    break;

                // POP BC
                case 0xC1:
                    c.setValue(JavaBoy.unsign(addressRead(sp.intValue())));
                    b.setValue(JavaBoy.unsign(addressRead(sp.intValue() + 1)));
                    sp.inc();
                    sp.inc();
                    break;

                // JP NZ, n
                case 0xC2: {
                    Short address = loadImmediateShort(pc);
                    jp(f.zf() == ZERO, address);
                    break;
                }

                /*
                 JP nn
                 */
                case 0xC3: {
                    Short address =loadImmediateShort(pc);
                    jp(address);
                    break;
                }

                /*
                 CALL NZ, nn
                 */
                case 0xC4:
                    call(f.zf() == ZERO);
                    break;

                /*
                 PUSH BC
                 */
                case 0xC5:
                    pushShort(sp, bc);
                    break;

                /*
                 ADD A, n
                 */
                case 0xC6: {
                    Byte data = loadImmediateByte(pc);
                    add(a, data);
                    break;
                }

                // RST 08
                case 0xCF:
                    rst(0x08);
                    break;

                // RET Z
                case 0xC8: {
                    ret(f.zf() == ONE, sp);
                    break;
                }

                /*
                 RET
                 */
                case 0xC9:
                    ret(sp);
                    break;

                /*
                 JP Z, nn
                 */
                case 0xCA: {
                    Short address = loadImmediateShort(pc);
                    jp(f.zf() == ONE, address);
                    break;
                }

                // Shift/bit test
                case 0xCB: {
                    Byte operand = loadImmediateByte(pc);
                    int regNum = operand.intValue() & 0x07;
                    int data = registerRead(regNum);
                    if ((operand.intValue() & 0xC0) == 0) {
                        switch ((operand.intValue() & 0xF8)) {

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
                        int bitNumber = (operand.intValue() & 0x38) >> 3;

                        // BIT n, r
                        if ((operand.intValue() & 0xC0) == 0x40) {
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
                        if ((operand.intValue() & 0xC0) == 0x80) {
                            mask = (short) (0xFF - (0x01 << bitNumber));
                            data = (short) (data & mask);
                            registerWrite(regNum, data);
                        }

                        // SET n, r
                        if ((operand.intValue() & 0xC0) == 0xC0) {
                            mask = (short) (0x01 << bitNumber);
                            data = (short) (data | mask);
                            registerWrite(regNum, data);
                        }
                    }

                    break;
                }

                // CALL Z, nn
                case 0xCC:
                    call(f.zf() == ONE);
                    break;

                // CALL nn
                case 0xCD: {
                    call();
                    break;
                }

                /*
                *  ADC A, n
                */
                case 0xCE: {
                    Byte data = loadImmediateByte(pc);
                    adc(a, data, f.cf());
                    break;
                }

                /*
                 RST 00
                  */
                case 0xC7:
                    rst(0x00);
                    break;

                /*
                 RET NC
                  */
                case 0xD0:
                    ret(f.cf() == ZERO, sp);
                    break;

                /*
                 POP DE
                  */
                case 0xD1: {
                    Short data = popShort(sp);
                    de.setValue(data.intValue());
                    break;
                }

                /*
                 JP NC, nn
                 */
                case 0xD2: {
                    Short address = loadImmediateShort(pc);
                    jp(f.cf() == ZERO, address);
                    break;
                }

                /*
                 CALL NC, nn
                 */
                case 0xD4:
                    call(f.cf() == ZERO);
                    break;

                /*
                 PUSH DE
                 */
                case 0xD5:
                    pushShort(sp, de);
                    break;

                /*
                 SUB A, n
                 */
                case 0xD6: {
                    Byte data = loadImmediateByte(pc);
                    sub(a, data);
                    break;
                }

                /*
                 RST 10
                  */
                case 0xD7:
                    rst(0x10);
                    break;

                /*
                 RET C
                  */
                case 0xD8:
                    ret(f.cf() == ONE, sp);
                    break;

                /*
                 RETI
                  */
                case 0xD9:
                    interruptsEnabled = true;
                    ret(sp);
                    break;

                /*
                 JP C, nn
                 */
                case 0xDA: {
                    Short address = loadImmediateShort(pc);
                    jp(f.cf() == ONE, address);
                    break;
                }

                /*
                 CALL C, nn
                 */
                case 0xDC:
                    call(f.cf() == ONE);
                    break;

                // SBC A, n
                case 0xDE: {
                    Byte data = loadImmediateByte(pc);
                    sbc(a, data, f.cf());
                    break;
                }

                // RST 18
                case 0xDF:
                    rst(0x18);
                    break;

                // LDH (n), A
                case 0xE0: {
                    Byte data = loadImmediateByte(pc);
                    write(new Short(0xFF00 | data.intValue()), a);
                    break;
                }

                // POP HL
                case 0xE1: {
                    Short data = popShort(sp);
                    load(hl, data);
                    break;
                }

                // LDH (FF00 + C), A
                case 0xE2:
                    addressWrite(0xFF00 + c.intValue(), a.intValue());
                    break;

                // PUSH HL
                case 0xE5:
                    pushShort(sp, hl);
                    break;

                // AND n
                case 0xE6: {
                    Byte data = loadImmediateByte(pc);
                    and(a, data);
                    break;
                }

                // RST 20
                case 0xE7:
                    rst(0x20);
                    break;

                // ADD SP, nn
                case 0xE8: {
                    Short data = loadImmediateShort(pc);
                    add(sp, data);
                    break;
                }

                /*
                 JP HL
                 */
                case 0xE9: {
                    jp(hl);
                    break;
                }

                /*
                 LD (nn), A
                 */
                case 0xEA: {
                    Short address = loadImmediateShort(pc);
                    write(address, a);
                    break;
                }

                // XOR A, n
                case 0xEE: {
                    Byte data = loadImmediateByte(pc);
                    xor(a, data);
                    break;
                }

                /*
                 RST 28
                 */
                case 0xEF:
                    rst(0x28);
                    break;

                /*
                 LDH A, (n)
                 */
                case 0xF0: {
                    Byte addressOffset = loadImmediateByte(pc);
                    Byte ff = new Byte(0xFF);

                    Short address = new Short(ff, addressOffset);
                    load(a, read(address));
                    break;
                }

                /*
                 POP AF
                 */
                case 0xF1:
                    load(af, popShort(sp));
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
                case 0xF6: {
                    Byte data = loadImmediateByte(pc);
                    or(a, data);
                    break;
                }

                // RST 30
                case 0xF7:
                    rst(0x30);
                    break;

                // LD HL, SP + n  ** HALFCARRY FLAG NOT SET ***
                case 0xF8: {
                    Byte offset = loadImmediateByte(pc);
                    int result = sp.intValue() + offset.intValue();

                    add(hl, new Short(result));

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

                /*
                 LD A, (nn)
                 */
                case 0xFA: {
                    Short address = loadImmediateShort(pc);
                    Byte data = read(address);
                    load(a, data);
                    break;
                }

                /*
                 EI
                 */
                case 0xFB:
                    ieDelay = 1;
                    break;

                /*
                 CP n
                 */
                case 0xFE: {
                    Byte data = loadImmediateByte(pc);
                    cp(a, data);
                    break;
                }

                /*
                 RST 38
                 */
                case 0xFF:
                    rst(0x38);
                    break;

                default:

                    // ALU Operations
                    if ((opcode.intValue() & 0xC0) == 0x80) {
                        int operand = registerRead(opcode.intValue() & 0x07);
                        switch ((opcode.intValue() & 0x38) >> 3) {

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
                                cp(a, new Byte(operand));
                                break;
                        }

                    } else if ((opcode.intValue() & 0xC0) == 0x40) {   // Byte 0x01xxxxxxx indicates 8-bit ld
                        registerWrite((opcode.intValue() & 0x38) >> 3, registerRead(opcode.intValue() & 0x07));
                    } else {
                        Logger.debug("Unrecognized opcode (" + String.format("%02X", opcode.intValue()) + ")");
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

    private void call() {
        call(true);
    }

    private void call(boolean condition) {
        Short address = loadImmediateShort(pc);
        if (condition) {
            call(address);
        }
    }

    private void call(Short address) {
        pushShort(sp, pc);
        jp(address);
    }

    private void jp(boolean condition, Short address) {
        if (condition) {
            jp(address);
        }
    }

    private void jp(Short address) {
        pc.setValue(address.intValue());
    }

    private void ret(Short stackAddress) {
        Short address = popShort(stackAddress);
        pc.setValue(address.intValue());
    }

    private void ret(boolean condition, Short stackAddress) {
        if (condition) {
            ret(stackAddress);
        }
    }

    private Short popShort(Short address) {
        Byte lowerByte = pop(address);
        Byte upperByte = pop(address);

        return new Short(upperByte, lowerByte);
    }

    private Byte pop(Short address) {
        Byte top = read(address);
        address.inc();
        return top;
    }

    private void pushShort(Short address, Short data) {
        push(address, data.getUpperByte());
        push(address, data.getLowerByte());
    }

    private void push(Short address, Byte data) {
        address.dec();
        write(address, data);
    }

    private void jr(boolean condition, Byte addr) {
        if (condition) {
            add(pc, addr);
        }
    }

    private void add(Short pc, Byte addr) {
        Short addrShort = Short.signedShortFromByte(addr);
        add(pc, addrShort);
    }

    private void rst(int address) {
        sp.dec();
        addressWrite(sp.intValue(), pc.getUpperByte().intValue());
        sp.dec();
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

    /**
     * RL
     * <p>
     * ╔═════════════════════════════════╗
     * ║  ┌────┐    ┌───┬────────┬───┐   ║
     * ╚══│ CF │<═══│ 7 │ <═════ │ 0 │<══╝
     *    └────┘    └───┴────────┴───┘
     */
    private void rl(Byte operand, BitValue carry) {
        int result = ((operand.intValue() << 1) | carry.intValue()) & 0xFF;

        f.nf(ZERO);
        f.hf(ZERO);
        f.cf(operand.getBit(7));
        f.setZeroFlagForResult(result);

        operand.setValue(result);
    }

    private void rla(Byte operand) {
        rl(operand, operand.getBit(7));
        f.zf(ZERO);
    }


    /**
     * RLC
     * <p>
     *         ╔══════════════════════╗
     * ┌────┐  ║ ┌───┬────────┬───┐   ║
     * │ CF │<═╩═│ 7 │ <═════ │ 0 │<══╝
     * └────┘    └───┴────────┴───┘
     */
    private void rlc(Byte operand) {
        rl(operand, operand.getBit(7));
    }

    private void rlca(Byte operand) {
        rlc(operand);
        f.zf(ZERO);
    }

    /**
     * RR
     * <p>
     * ╔════════════════════════════════╗
     * ║  ┌────┐    ┌───┬────────┬───┐  ║
     * ╚═>│ CF │═══>│ 7 │ ═════> │ 0 │══╝
     *    └────┘    └───┴────────┴───┘
     */
    private void rr(Byte operand, BitValue carry) {
        int result = (carry.intValue() << 7) | (operand.intValue() >> 1);

        f.nf(ZERO);
        f.hf(ZERO);
        f.cf(operand.getBit(0));
        f.setZeroFlagForResult(result);

        operand.setValue(result);
    }

    private void rra(Byte operand, BitValue carry) {
        rr(operand, carry);
        f.zf(ZERO);
    }

    /**
     * RRC
     * <p>
     * ╔═════════╦═══════════════════════╗
     * ║  ┌────┐ ║  ┌───┬────────┬───┐   ║
     * ╚═>│ CF │ ╚═>│ 7 │ ═════> │ 0 │═══╝
     *    └────┘    └───┴────────┴───┘
     */
    private void rrc(Byte operand) {
        rr(operand, operand.getBit(0));
    }
    private void rrca(Byte operand) {
        rrc(operand);
        f.zf(ZERO);
    }

    /**
     * SLA
     * <p>
     * ┌────┐    ┌───┬────────┬───┐
     * │ CF │<═══│ 7 │ <═════ │ 0 │<══ 0
     * └────┘    └───┴────────┴───┘
     */
    private void sla(Byte operand) {
        int result = operand.intValue() << 1;

        f.nf(ZERO);
        f.hf(ZERO);
        f.cf(operand.getBit(7));
        f.setZeroFlagForResult(result);

        operand.setValue(result);
    }

    /**
     * SRA
     * <p>
     * ╔════╗
     * ║  ┌───┬────────┬───┐    ┌────┐
     * ╚═>│ 7 │ ═════> │ 0 │═══>│ CF │
     *    └───┴────────┴───┘    └────┘
     */
    private void sra(Byte operand, BitValue sign) {
        int result = (sign.intValue() << 7) | (operand.intValue() >> 1);

        f.nf(ZERO);
        f.hf(ZERO);
        f.cf(operand.getBit(0));
        f.setZeroFlagForResult(result);

        operand.setValue(result);
    }

    /**
     * SRL
     * <p>
     *      ┌───┬────────┬───┐    ┌────┐
     * 0 ══>│ 7 │ ═════> │ 0 │═══>│ CF │
     *      └───┴────────┴───┘    └────┘
     */
    private void srl(Byte operand) {
        sra(operand, ZERO);
    }

    public void add(Short left, Short right) {

        f.nf(ZERO);

        int lowerResult = (left.intValue() & 0x0FFF) + (right.intValue() & 0x0FFF);

        if ((lowerResult & 0x1000) == 0x1000) {
            f.hf(ONE);
        } else {
            f.hf(ZERO);
        }

        int result = left.intValue() + right.intValue();

        if ((result & 0x10000) == 0x10000) {
            f.cf(ONE);
        } else {
            f.cf(ZERO);
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
