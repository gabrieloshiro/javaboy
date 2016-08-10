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

/**
 * This is the main controlling class for the emulation
 * It contains the code to emulate the Z80-like processor
 * found in the Gameboy, and code to provide the locations
 * in CPU address space that points to the correct area of
 * ROM/RAM/IO.
 */
class Dmgcpu {

    private final Byte a = new Byte();
    private final FlagRegister f = new FlagRegister();

    private final Short bc = new Short();
    private final Byte b = bc.getUpperByte();
    private final Byte c = bc.getLowerByte();

    private final Short de = new Short();
    private final Byte d = de.getUpperByte();
    private final Byte e = de.getLowerByte();

    private final Short hl = new Short();
    private final Byte h = hl.getUpperByte();
    private final Byte l = hl.getLowerByte();

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

            switch (b1) {

                /*
                  NOP
                 */
                case 0x00:
                    pc.inc();
                    break;

                /*
                  LD BC, nn
                 */
                case 0x01:
                    pc.inc();
                    pc.inc();
                    pc.inc();
                    b.setValue(b3);
                    c.setValue(b2);
                    break;
                case 0x02:               // LD (BC), A
                    pc.inc();
                    addressWrite(bc.intValue(), a.intValue());
                    break;
                case 0x03:               // INC BC
                    pc.inc();
                    bc.inc();
                    break;
                case 0x04:               // INC B
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);
                    switch (b.intValue()) {
                        case 0xFF:
                            f.zf(ONE);
                            f.cf(ONE);
                            b.setValue(0x00);
                            break;
                        case 0x0F:
                            f.hf(ONE);
                            b.setValue(0x10);
                            break;
                        default:
                            b.setValue(b.intValue() + 1);
                            break;
                    }
                    break;
                case 0x05:               // DEC B
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ONE);
                    f.hf(ZERO);
                    switch (b.intValue()) {
                        case 0x00:
                            f.hf(ONE);
                            b.setValue(0xFF);
                            break;
                        case 0x10:
                            f.hf(ONE);
                            b.setValue(0x0F);
                            break;
                        case 0x01:
                            f.zf(ONE);
                            b.setValue(0x00);
                            break;
                        default:
                            b.setValue(b.intValue() - 1);
                            break;
                    }
                    break;
                case 0x06:               // LD B, nn
                    pc.inc();
                    pc.inc();
                    b.setValue(b2);
                    break;
                case 0x07:               // RLC A
                    pc.inc();
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
                case 0x08:               // LD (nnnn), SP   /* **** May be wrong! **** */
                    pc.inc();
                    pc.inc();
                    pc.inc();
                    addressWrite((b3 << 8) + b2 + 1, sp.getUpperByte().intValue());
                    addressWrite((b3 << 8) + b2, sp.getLowerByte().intValue());
                    break;
                case 0x09: {       // ADD HL, BC
                    pc.inc();
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
                case 0x0A:               // LD A, (BC)
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead(bc.intValue())));
                    break;
                case 0x0B:               // DEC BC
                    pc.inc();
                    bc.dec();
                    break;
                case 0x0C:               // INC C
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);

                    switch (c.intValue()) {
                        case 0xFF:
                            f.zf(ONE);
                            f.hf(ONE);
                            c.setValue(0x00);
                            break;
                        case 0x0F:
                            f.hf(ONE);
                            c.setValue(0x10);
                            break;
                        default:
                            c.setValue(c.intValue() + 1);
                            break;
                    }
                    break;
                case 0x0D:               // DEC C
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ONE);
                    f.hf(ZERO);

                    switch (c.intValue()) {
                        case 0x00:
                            f.hf(ONE);
                            c.setValue(0xFF);
                            break;
                        case 0x10:
                            f.hf(ONE);
                            c.setValue(0x0F);
                            break;
                        case 0x01:
                            f.zf(ONE);
                            c.setValue(0x00);
                            break;
                        default:
                            c.setValue(c.intValue() - 1);
                            break;
                    }
                    break;
                case 0x0E:               // LD C, nn
                    pc.inc();
                    pc.inc();
                    c.setValue(b2);
                    break;
                case 0x0F:               // RRC A
                    pc.inc();
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
                case 0x10:               // STOP
                    pc.inc();
                    pc.inc();
                    break;
                case 0x11:               // LD DE, nnnn
                    pc.inc();
                    pc.inc();
                    pc.inc();
                    d.setValue(b3);
                    e.setValue(b2);
                    break;
                case 0x12:               // LD (DE), A
                    pc.inc();
                    addressWrite(de.intValue(), a.intValue());
                    break;
                case 0x13:               // INC DE
                    pc.inc();
                    de.inc();
                    break;
                case 0x14:               // INC D
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);
                    switch (d.intValue()) {
                        case 0xFF:
                            f.zf(ONE);
                            f.hf(ONE);
                            d.setValue(0x00);
                            break;
                        case 0x0F:
                            f.hf(ONE);
                            d.setValue(0x10);
                            break;
                        default:
                            d.setValue(d.intValue() + 1);
                            break;
                    }
                    break;
                case 0x15:               // DEC D
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);
                    f.nf(ONE);
                    switch (d.intValue()) {
                        case 0x00:
                            f.hf(ONE);
                            d.setValue(0xFF);
                            break;
                        case 0x10:
                            f.hf(ONE);
                            d.setValue(0x0F);
                            break;
                        case 0x01:
                            f.zf(ONE);
                            d.setValue(0x00);
                            break;
                        default:
                            d.setValue(d.intValue() - 1);
                            break;
                    }
                    break;
                case 0x16:               // LD D, nn
                    pc.inc();
                    pc.inc();
                    d.setValue(b2);
                    break;
                case 0x17:               // RL A
                    pc.inc();
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
                case 0x18:               // JR nn
                    pc.setValue(pc.intValue() + 2 + offset);
                    break;
                case 0x19: {               // ADD HL, DE
                    pc.inc();
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
                case 0x1A:               // LD A, (DE)
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead(de.intValue())));
                    break;
                case 0x1B:               // DEC DE
                    pc.inc();
                    de.inc();
                    break;
                case 0x1C:               // INC E
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);
                    switch (e.intValue()) {
                        case 0xFF:
                            f.zf(ONE);
                            f.hf(ONE);
                            e.setValue(0x00);
                            break;
                        case 0x0F:
                            f.hf(ONE);
                            e.setValue(0x10);
                            break;
                        default:
                            e.setValue(e.intValue() + 1);
                            break;
                    }
                    break;
                case 0x1D:               // DEC E
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ONE);
                    f.hf(ZERO);

                    switch (e.intValue()) {
                        case 0x00:
                            f.hf(ONE);
                            e.setValue(0xFF);
                            break;
                        case 0x10:
                            f.hf(ONE);
                            e.setValue(0x0F);
                            break;
                        case 0x01:
                            f.zf(ONE);
                            e.setValue(0x00);
                            break;
                        default:
                            e.setValue(e.intValue() - 1);
                            break;
                    }
                    break;
                case 0x1E:               // LD E, nn
                    pc.inc();
                    pc.inc();
                    e.setValue(b2);
                    break;
                case 0x1F:               // RR A
                    pc.inc();
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
                case 0x20:               // JR NZ, nn
                    if (f.zf().intValue() == 0) {
                        pc.setValue(pc.intValue() + 2 + offset);
                    } else {
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0x21:               // LD HL, nnnn
                    pc.inc();
                    pc.inc();
                    pc.inc();
                    hl.setValue((b3 << 8) + b2);
                    break;
                case 0x22:               // LD (HL+), A
                    pc.inc();
                    addressWrite(hl.intValue(), a.intValue());
                    hl.inc();
                    break;
                case 0x23:               // INC HL
                    pc.inc();
                    hl.inc();
                    break;
                case 0x24:               // INC H         ** May be wrong **
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);
                    switch (h.intValue()) {
                        case 0xFF:
                            f.zf(ONE);
                            f.hf(ONE);
                            h.setValue(0x00);
                            break;
                        case 0x0F:
                            f.hf(ONE);
                            h.setValue(0x10);
                            break;
                        default:
                            h.setValue(h.intValue() + 1);
                            break;
                    }
                    break;
                case 0x25:               // DEC H           ** May be wrong **
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ONE);
                    f.hf(ZERO);

                    switch (h.intValue()) {
                        case 0x00:
                            f.hf(ONE);
                            hl.setValue((hl.intValue() & 0x00FF) | (0xFF00));
                            break;
                        case 0x10:
                            f.hf(ONE);
                            hl.setValue((hl.intValue() & 0x00FF) | (0x0F00));
                            break;
                        case 0x01:
                            f.zf(ONE);
                            hl.setValue(hl.intValue() & 0x00FF);
                            break;
                        default:
                            hl.setValue((hl.intValue() & 0x00FF) | ((hl.intValue() & 0xFF00) - 0x0100));
                            break;
                    }
                    break;
                case 0x26:               // LD H, nn
                    pc.inc();
                    pc.inc();
                    hl.setValue((hl.intValue() & 0x00FF) | (b2 << 8));
                    break;
                case 0x27:               // DAA         ** This could be wrong! **
                    pc.inc();

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

                    //a &= 0x00FF;
                    if (a.intValue() == 0) {
                        newf.zf(ONE);
                    }

                    f.setValue(newf.intValue());

                    break;
                case 0x28:               // JR Z, nn
                    if (f.zf().intValue() == 1) {
                        pc.setValue(pc.intValue() + 2 + offset);
                    } else {
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0x29: {             // ADD HL, HL
                    pc.inc();
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
                case 0x2A:               // LDI A, (HL)
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead(hl.intValue())));
                    hl.inc();
                    break;
                case 0x2B:               // DEC HL
                    pc.inc();
                    hl.dec();
                    break;
                case 0x2C:               // INC L
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);

                    switch (l.intValue()) {
                        case 0xFF:
                            f.hf(ONE);
                            f.zf(ONE);
                            l.setValue(0x00);
                            break;
                        case 0x0F:
                            f.hf(ONE);
                            l.setValue(0x10);
                            break;
                        default:
                            l.setValue(l.intValue() + 1);
                            break;
                    }
                    break;
                case 0x2D:               // DEC L
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ONE);
                    f.hf(ZERO);

                    switch (l.intValue()) {
                        case 0x00:
                            f.hf(ONE);
                            l.setValue(0xFF);
                            break;
                        case 0x10:
                            f.hf(ONE);
                            //hl = (hl & 0xFF00) | 0x000F;
                            l.setValue(0x0F);
                            break;
                        case 0x01:
                            f.zf(ONE);
                            //hl = (hl & 0xFF00);
                            l.setValue(0x00);
                            break;
                        default:
                            l.setValue(l.intValue() - 1);
                            break;
                    }
                    break;
                case 0x2E:               // LD L, nn
                    pc.inc();
                    pc.inc();
                    //hl = (hl & 0xFF00) | b2;
                    l.setValue(b2);
                    break;
                case 0x2F:               // CPL A
                    pc.inc();
                    a.setValue((~a.intValue()));
                    f.nf(ONE);
                    f.hf(ONE);
                    break;
                case 0x30:               // JR NC, nn
                    if (f.cf().intValue() == 0) {
                        pc.setValue(pc.intValue() + 2 + offset);
                    } else {
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0x31:               // LD SP, nnnn
                    pc.inc();
                    pc.inc();
                    pc.inc();
                    sp.setValue((b3 << 8) + b2);
                    break;
                case 0x32:
                    pc.inc();
                    addressWrite(hl.intValue(), a.intValue());  // LD (HL-), A
                    hl.dec();
                    break;
                case 0x33:               // INC SP
                    sp.inc();
                    pc.inc();
                    break;
                case 0x34:               // INC (HL)
                    pc.inc();
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
                case 0x35:               // DEC (HL)
                    pc.inc();
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
                case 0x36:               // LD (HL), nn
                    pc.inc();
                    pc.inc();
                    addressWrite(hl.intValue(), b2);
                    break;
                case 0x37:               // SCF
                    pc.inc();
                    f.nf(ZERO);
                    f.hf(ZERO);
                    f.cf(ONE);
                    break;
                case 0x38:               // JR C, nn
                    if (f.cf().booleanValue()) {
                        pc.setValue(pc.intValue() + 2 + offset);
                    } else {
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0x39: {               // ADD HL, SP      ** Could be wrong **
                    pc.inc();
                    int result = hl.intValue() + sp.intValue();
                    if ((result & 0xFFFF0000) != 0) {
                        //                        f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)) | (F_CARRY));

                        f.cf(ONE);


                        result = result & 0xFFFF;
                    } else {
                        //f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)));
                        f.cf(ZERO);
                    }
                    hl.setValue(result);
                    break;
                }
                case 0x3A:               // LD A, (HL-)
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead(hl.intValue())));
                    hl.dec();
                    break;
                case 0x3B:               // DEC SP
                    sp.dec();
                    pc.inc();
                    break;
                case 0x3C:               // INC A
                    pc.inc();
                    f.zf(ZERO);
                    f.nf(ZERO);
                    f.hf(ZERO);
                    switch (a.intValue()) {
                        case 0xFF:
                            f.hf(ONE);
                            f.zf(ONE);
                            a.setValue(0x00);
                            break;
                        case 0x0F:
                            f.hf(ONE);
                            a.setValue(0x10);
                            break;
                        default:
                            a.setValue(a.intValue() + 1);
                            break;
                    }
                    break;
                case 0x3D:               // DEC A
                    pc.inc();
                    f.zf(ZERO);
                    f.hf(ZERO);

                    f.nf(ONE);
                    switch (a.intValue()) {
                        case 0x00:
                            f.hf(ONE);
                            a.setValue(0xFF);
                            break;
                        case 0x10:
                            f.hf(ONE);
                            a.setValue(0x0F);
                            break;
                        case 0x01:
                            f.zf(ONE);
                            a.setValue(0x00);
                            break;
                        default:
                            a.setValue(a.intValue() - 1);
                            break;
                    }
                    break;
                case 0x3E:               // LD A, nn
                    pc.inc();
                    pc.inc();
                    a.setValue(b2);
                    break;
                case 0x3F:               // CCF
                    pc.inc();
                    f.nf(ZERO);
                    f.hf(ZERO);

                    if (f.cf().intValue() == 0) {
                        f.cf(ONE);
                    } else {
                        f.cf(ZERO);
                    }
                    break;
                case 0x52:
                    pc.inc();
                    break;

                case 0x76:               // HALT
                    interruptsEnabled = true;
                    while (ioHandler.registers[0x0F] == 0) {
                        initiateInterrupts();
                        instrCount++;
                    }
                    pc.inc();
                    break;

                // XOR A, A (== LD A, 0)
                case 0xAF:
                    pc.inc();
                    xor(a, a);
                    break;

                case 0xC0:               // RET NZ
                    if (f.zf().intValue() == 0) {
                        pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                        sp.inc();
                        sp.inc();
                    } else {
                        pc.inc();
                    }
                    break;
                case 0xC1:               // POP BC
                    pc.inc();
                    c.setValue(JavaBoy.unsign(addressRead(sp.intValue())));
                    b.setValue(JavaBoy.unsign(addressRead(sp.intValue() + 1)));
                    sp.inc();
                    sp.inc();
                    break;
                case 0xC2:               // JP NZ, nnnn
                    if (f.zf().intValue() == 0) {
                        pc.setValue((b3 << 8) + b2);
                    } else {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0xC3:
                    pc.setValue((b3 << 8) + b2);  // JP nnnn
                    break;
                case 0xC4:               // CALL NZ, nnnnn
                    if (f.zf().intValue() == 0) {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                        sp.dec();
                        sp.dec();
                        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                        pc.setValue((b3 << 8) + b2);
                    } else {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0xC5:               // PUSH BC
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    //sp &= 0xFFFF;
                    addressWrite(sp.intValue(), c.intValue());
                    addressWrite(sp.intValue() + 1, b.intValue());
                    break;
                case 0xC6: {            // ADD A, nn
                    pc.inc();
                    pc.inc();

                    add(a, new Byte(b2));
                    break;
                }
                case 0xCF:               // RST 08
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue(0x08);
                    break;
                case 0xC8:               // RET Z
                    if (f.zf().intValue() == 1) {
                        pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                        sp.inc();
                        sp.inc();
                    } else {
                        pc.inc();
                    }
                    break;
                case 0xC9:               // RET
                    pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                    sp.inc();
                    sp.inc();
                    break;
                case 0xCA:               // JP Z, nnnn
                    if (f.zf().intValue() == 1) {
                        pc.setValue((b3 << 8) + b2);
                    } else {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0xCB: {              // Shift/bit test
                    pc.inc();
                    pc.inc();
                    int regNum = b2 & 0x07;
                    int data = registerRead(regNum);
                    //        Logger.debug("0xCB instr! - reg " + JavaBoy.hexByte((short) (b2 & 0xF4)));
                    if ((b2 & 0xC0) == 0) {
                        switch ((b2 & 0xF8)) {
                            case 0x00:          // RLC A
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
                            case 0x08:          // RRC A
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
                            case 0x10:          // RL r
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
                            case 0x18:          // RR r
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
                            case 0x20:          // SLA r
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
                            case 0x28:          // SRA r
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
                            case 0x30:          // SWAP r

                                data = (short) (((data & 0x0F) << 4) | ((data & 0xF0) >> 4));
                                f.setValue(0);
                                if (data == 0) {
                                    f.zf(ONE);
                                }
                                registerWrite(regNum, data);
                                break;
                            case 0x38:          // SRL r
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

                        if ((b2 & 0xC0) == 0x40) {  // BIT n, r
                            mask = (short) (0x01 << bitNumber);
                            f.nf(ZERO);
                            f.hf(ONE);
                            if ((data & mask) != 0) {
                                f.zf(ZERO);
                            } else {
                                f.zf(ONE);
                            }
                        }
                        if ((b2 & 0xC0) == 0x80) {  // RES n, r
                            mask = (short) (0xFF - (0x01 << bitNumber));
                            data = (short) (data & mask);
                            registerWrite(regNum, data);
                        }
                        if ((b2 & 0xC0) == 0xC0) {  // SET n, r
                            mask = (short) (0x01 << bitNumber);
                            data = (short) (data | mask);
                            registerWrite(regNum, data);
                        }
                    }

                    break;
                }
                case 0xCC:               // CALL Z, nnnnn
                    if (f.zf().intValue() == 1) {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                        sp.dec();
                        sp.dec();
                        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                        pc.setValue((b3 << 8) + b2);
                    } else {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0xCD:               // CALL nnnn
                    pc.inc();
                    pc.inc();
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue((b3 << 8) + b2);
                    break;
                case 0xCE: {              // ADC A, nn
                    pc.inc();
                    pc.inc();

                    if (f.cf().intValue() == 1) {
                        b2++;
                    }
                    f.setValue(0);

                    if ((((a.intValue() & 0x0F) + (b2 & 0x0F)) & 0xF0) != 0x00) {
                        f.hf(ONE);
                    }

                    int result = a.intValue() + b2;

                    if ((result & 0xFF00) != 0) {     // Perform 8-bit overflow and set zero flag
                        //f.hf(ONE);
                        f.cf(ONE);
                    }
                    if (result == 0) {
                        f.zf(ONE);
                    }
                    a.setValue(result);
                    break;
                }
                case 0xC7:               // RST 00
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    //        terminate = true;
                    pc.setValue(0x00);
                    break;
                case 0xD0:               // RET NC
                    if (f.cf().intValue() == 0) {
                        pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                        sp.inc();
                        sp.inc();
                    } else {
                        pc.inc();
                    }
                    break;
                case 0xD1:               // POP DE
                    pc.inc();
                    e.setValue(JavaBoy.unsign(addressRead(sp.intValue())));
                    d.setValue(JavaBoy.unsign(addressRead(sp.intValue() + 1)));
                    sp.inc();
                    sp.inc();
                    break;
                case 0xD2:               // JP NC, nnnn
                    if (f.cf().intValue() == 0) {
                        pc.setValue((b3 << 8) + b2);
                    } else {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0xD4:               // CALL NC, nnnn
                    if (f.cf().intValue() == 0) {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                        sp.dec();
                        sp.dec();
                        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                        pc.setValue((b3 << 8) + b2);
                    } else {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0xD5:               // PUSH DE
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue(), e.intValue());
                    addressWrite(sp.intValue() + 1, d.intValue());
                    break;
                case 0xD6: {              // SUB A, nn
                    pc.inc();
                    pc.inc();

                    f.zf(ZERO);
                    f.nf(ONE);
                    f.hf(ZERO);
                    f.cf(ZERO);

                    if ((((a.intValue() & 0x0F) - (b2 & 0x0F)) & 0xFFF0) != 0x00) {
                        f.hf(ONE);
                    }

                    int result = a.intValue() - b2;

                    if ((result & 0xFF00) != 0) {
                        f.cf(ONE);
                    }
                    if (result == 0) {
                        f.zf(ONE);
                    }
                    a.setValue(result);
                    break;
                }
                case 0xD7:               // RST 10
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue(0x10);
                    break;
                case 0xD8:               // RET C
                    if (f.cf().intValue() == 1) {
                        pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                        sp.inc();
                        sp.inc();
                    } else {
                        pc.inc();
                    }
                    break;
                case 0xD9:               // RETI
                    interruptsEnabled = true;
                    pc.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                    sp.inc();
                    sp.inc();
                    break;
                case 0xDA:               // JP C, nnnn
                    if (f.cf().intValue() == 1) {
                        pc.setValue((b3 << 8) + b2);
                    } else {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0xDC:               // CALL C, nnnn
                    if (f.cf().intValue() == 1) {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                        sp.dec();
                        sp.dec();
                        addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                        addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                        pc.setValue((b3 << 8) + b2);
                    } else {
                        pc.inc();
                        pc.inc();
                        pc.inc();
                    }
                    break;
                case 0xDE: {               // SBC A, nn
                    pc.inc();
                    pc.inc();
                    if (f.cf().intValue() == 1) {
                        b2++;
                    }

                    f.zf(ZERO);
                    f.nf(ONE);
                    f.hf(ZERO);
                    f.cf(ZERO);
                    if ((((a.intValue() & 0x0F) - (b2 & 0x0F)) & 0xFFF0) != 0x00) {
                        f.hf(ONE);
                    }

                    int result = a.intValue() - b2;

                    if ((result & 0xFF00) != 0) {
                        f.cf(ONE);
                    }

                    if (result == 0) {
                        f.zf(ONE);
                    }
                    a.setValue(result);
                    break;
                }
                case 0xDF:               // RST 18
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue(0x18);
                    break;
                case 0xE0:               // LDH (FFnn), A
                    pc.inc();
                    pc.inc();
                    addressWrite(0xFF00 + b2, a.intValue());
                    break;
                case 0xE1:               // POP HL
                    pc.inc();
                    hl.setValue((JavaBoy.unsign(addressRead(sp.intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(sp.intValue())));
                    sp.inc();
                    sp.inc();
                    break;
                case 0xE2:               // LDH (FF00 + C), A
                    pc.inc();
                    addressWrite(0xFF00 + c.intValue(), a.intValue());
                    break;
                case 0xE5:               // PUSH HL
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, h.intValue());
                    addressWrite(sp.intValue(), l.intValue());
                    break;
                case 0xE6:               // AND nn
                    pc.inc();
                    pc.inc();
                    a.setValue(a.intValue() & b2);
                    f.setValue(0);
                    if (a.intValue() == 0) {
                        f.zf(ONE);
                    }
                    break;
                case 0xE7:               // RST 20
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue(0x20);
                    break;
                case 0xE8: {               // ADD SP, nn
                    pc.inc();
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
                case 0xE9:               // JP (HL)
                    pc.inc();
                    pc.setValue(hl.intValue());
                    break;
                case 0xEA:               // LD (nnnn), A
                    pc.inc();
                    pc.inc();
                    pc.inc();
                    addressWrite((b3 << 8) + b2, a.intValue());
                    break;

                // XOR A, n
                case 0xEE: {
                    pc.inc();
                    Byte data = loadImmediateByte(pc);
                    xor(a, data);
                    break;
                }
                case 0xEF:               // RST 28
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue(0x28);
                    break;
                case 0xF0:               // LDH A, (FFnn)
                    pc.inc();
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead(0xFF00 + b2)));
                    break;
                case 0xF1:               // POP AF
                    pc.inc();
                    f.setValue(addressRead(sp.intValue()));
                    a.setValue(JavaBoy.unsign(addressRead(sp.intValue() + 1)));
                    sp.inc();
                    sp.inc();
                    break;
                case 0xF2:               // LD A, (FF00 + C)
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead(0xFF00 + c.intValue())));
                    break;
                case 0xF3:               // DI
                    pc.inc();
                    interruptsEnabled = false;
                    break;
                case 0xF5:               // PUSH AF
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue(), f.intValue());
                    addressWrite(sp.intValue() + 1, a.intValue());
                    break;
                case 0xF6:               // OR A, nn
                    pc.inc();
                    pc.inc();
                    a.setValue(a.intValue() | b2);
                    f.setValue(0);
                    if (a.intValue() == 0) {
                        f.zf(ONE);
                    }
                    break;
                case 0xF7:               // RST 30
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue(0x30);
                    break;
                case 0xF8: {              // LD HL, SP + nn  ** HALFCARRY FLAG NOT SET ***
                    pc.inc();
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
                case 0xF9:               // LD SP, HL
                    pc.inc();
                    sp.setValue(hl.intValue());
                    break;
                case 0xFA:               // LD A, (nnnn)
                    pc.inc();
                    pc.inc();
                    pc.inc();
                    a.setValue(JavaBoy.unsign(addressRead((b3 << 8) + b2)));
                    break;
                case 0xFB:               // EI
                    pc.inc();
                    ieDelay = 1;
                    break;
                case 0xFE:               // CP nn     ** FLAGS ARE WRONG! **
                    pc.inc();
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
                case 0xFF:               // RST 38
                    pc.inc();
                    sp.dec();
                    sp.dec();
                    addressWrite(sp.intValue() + 1, pc.getUpperByte().intValue());
                    addressWrite(sp.intValue(), pc.getLowerByte().intValue());
                    pc.setValue(0x38);
                    break;

                default:

                    if ((b1 & 0xC0) == 0x80) {       // Byte 0x10?????? indicates ALU op
                        pc.inc();
                        int operand = registerRead(b1 & 0x07);
                        switch ((b1 & 0x38) >> 3) {
                            case 1: // ADC A, r

                                

                                if (f.cf().intValue() == 1) {
                                    operand++;
                                }
                                // Note!  No break!

                                // ADD A, r
                            case 0: {
                                add(a, new Byte(operand));
                                break;
                            }
                            // SBC A, r
                            case 3:
                                if (f.cf().intValue() == 1) {
                                    operand++;
                                }
                                // Note! No break!
                                // todo ARRRRRRGH
                                // SUB A, r
                            case 2: {
                                f.zf(ZERO);
                                f.nf(ONE);
                                f.hf(ZERO);
                                f.cf(ZERO);

                                if ((((a.intValue() & 0x0F) - (operand & 0x0F)) & 0xFFF0) != 0x00) {
                                    f.hf(ONE);
                                }

                                int result = a.intValue() - operand;

                                if ((result & 0xFF00) != 0) {
                                    f.cf(ONE);
                                }
                                if (result == 0) {
                                    f.zf(ONE);
                                }
                                a.setValue(result);
                                break;
                            }
                            case 4: // AND A, r
                                a.setValue(a.intValue() & operand);
                                f.setValue(0);
                                if (a.intValue() == 0) {
                                    f.zf(ONE);
                                }
                                break;
                            case 5: // XOR A, r
                                xor(a, new Byte(operand));
                                break;
                            case 6: // OR A, r
                                a.setValue(a.intValue() | operand);
                                f.setValue(0);
                                if (a.intValue() == 0) {
                                    f.zf(ONE);
                                }
                                break;
                            case 7: // CP A, r (compare)
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

                        pc.inc();
                        registerWrite((b1 & 0x38) >> 3, registerRead(b1 & 0x07));

                    } else {
                        Logger.debug("Unrecognized opcode (" + String.format("%02X", b1) + ")");
                        pc.inc();
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

    private Byte loadImmediateByte(Short address) {
        address.inc();

        Byte immediate = new Byte();
        immediate.setValue(addressRead(address.intValue()));

        return immediate;
    }

    private Short loadImmediateShort(Short address) {
        address.inc();

        Short immediate = new Short();
        Byte lowerByte = loadImmediateByte(address);
        immediate.getLowerByte().setValue(lowerByte.intValue());
        Byte upperByte = loadImmediateByte(address);
        immediate.getUpperByte().setValue(upperByte.intValue());

        return immediate;
    }


    private void xor(Byte left, Byte right) {

        int result = left.intValue() ^ right.intValue();

        f.setValue(0);

        if (result == 0) {
            f.zf(ONE);
        }

        a.setValue(result);
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

}
