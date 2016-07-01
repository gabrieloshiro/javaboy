package javaboy;

import javaboy.lang.FlagRegister;

import java.awt.*;

/**
 * This is the main controlling class for the emulation
 * It contains the code to emulate the Z80-like processor
 * found in the Gameboy, and code to provide the locations
 * in CPU address space that points to the correct area of
 * ROM/RAM/IO.
 */
class Dmgcpu {
    /**
     * Registers: 8-bit
     */
    private int a, b, c, d, e;
    /**
     * Registers: 16-bit
     */
    private int hl;

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
    private short INSTRS_PER_DIV = BASE_INSTRS_PER_DIV;

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
    final short INT_SER = 0x08;

    /**
     * P10 - P13 (Joypad) interrupt
     */
    final short INT_P10 = 0x10;

    // 8Kb main system RAM appears at 0xC000 in address space
    // 32Kb for GBC
    private byte[] mainRam = new byte[0x8000];

    // 256 bytes at top of RAM are used mainly for registers
    private byte[] oam = new byte[0x100];

    private Cartridge cartridge;
    GraphicsChip graphicsChip;
    IoHandler ioHandler;
    private Component applet;

    boolean gbcFeatures = true;
    int gbcRamBank = 1;

    Registers r;

    /**
     * Create a CPU emulator with the supplied cartridge and game link objects.  Both can be set up
     * or changed later if needed
     */
    Dmgcpu(Cartridge c, Component a, Registers r) {
        cartridge = c;
        graphicsChip = new GraphicsChip(a, this);
        checkEnableGbc();

        ioHandler = new IoHandler(this);
        applet = a;

        this.r = r;
    }

    /**
     * Perform a CPU address space read.  This maps all the relevant objects into the correct parts of
     * the memory
     */
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
                return cartridge.addressRead(addr);

            case 0x8000:
            case 0x9000:
                return graphicsChip.addressRead(addr - 0x8000);

            case 0xA000:
            case 0xB000:
                return cartridge.addressRead(addr);

            case 0xC000:
                return (mainRam[addr - 0xC000]);

            case 0xD000:
                return (mainRam[addr - 0xD000 + (gbcRamBank * 0x1000)]);

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
                System.out.println("Tried to read address " + addr + ".  pc = " + String.format("%04X", r.pc().intValue()));
                return 0xFF;
        }

    }

    /**
     * Performs a CPU address space write.  Maps all of the relevant object into the right parts of
     * memory.
     */
    final void addressWrite(int addr, int data) {

        switch (addr & 0xF000) {
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                cartridge.addressWrite(addr, data);
                break;

            case 0x8000:
            case 0x9000:
                graphicsChip.addressWrite(addr - 0x8000, (byte) data);
                break;

            case 0xA000:
            case 0xB000:
                cartridge.addressWrite(addr, data);
                break;

            case 0xC000:
                mainRam[addr - 0xC000] = (byte) data;
                break;

            case 0xD000:
                mainRam[addr - 0xD000 + (gbcRamBank * 0x1000)] = (byte) data;
                break;

            case 0xE000:
                mainRam[addr - 0xE000] = (byte) data;
                break;

            case 0xF000:
                if (addr < 0xFE00) {
                    try {
                        mainRam[addr - 0xE000] = (byte) data;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println("Address error: " + addr + " pc = " + String.format("%04X", r.pc().intValue()));
                    }
                } else if (addr < 0xFF00) {
                    oam[addr - 0xFE00] = (byte) data;
                } else {
                    ioHandler.ioWrite(addr - 0xFF00, (short) data);
                }
                break;
        }


    }

    private void setBC(int value) {
        b = (short) ((value & 0xFF00) >> 8);
        c = (short) (value & 0x00FF);
    }

    private void setDE(int value) {
        d = (short) ((value & 0xFF00) >> 8);
        e = (short) (value & 0x00FF);
    }

    private void setHL(int value) {
        hl = value;
    }

    /**
     * Performs a read of a register by internal register number
     */
    private int registerRead(int regNum) {
        switch (regNum) {
            case 0:
                return b;
            case 1:
                return c;
            case 2:
                return d;
            case 3:
                return e;
            case 4:
                return (short) ((hl & 0xFF00) >> 8);
            case 5:
                return (short) (hl & 0x00FF);
            case 6:
                return JavaBoy.unsign(addressRead(hl));
            case 7:
                return a;
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
                b = (short) data;
                break;
            case 1:
                c = (short) data;
                break;
            case 2:
                d = (short) data;
                break;
            case 3:
                e = (short) data;
                break;
            case 4:
                hl = (hl & 0x00FF) | (data << 8);
                break;
            case 5:
                hl = (hl & 0xFF00) | data;
                break;
            case 6:
                addressWrite(hl, data);
                break;
            case 7:
                a = (short) data;
                break;
            default:
                break;
        }
    }

    private void checkEnableGbc() {
        // GBC Cartridge ID
        gbcFeatures = ((cartridge.rom[0x143] & 0x80) == 0x80);
    }


    /**
     * Resets the CPU to it's power on state.  Memory contents are not cleared.
     */
    void reset() {

        checkEnableGbc();
        setDoubleSpeedCpu(false);
        graphicsChip.dispose();
        cartridge.reset();
        interruptsEnabled = false;
        ieDelay = -1;
        r.pc(0x0100);
        r.sp(0xFFFE);

        r.f().zf().set();
        r.f().nf().reset();
        r.f().hf().set();
        r.f().cf().set();

        gbcRamBank = 1;
        instrCount = 0;

        if (gbcFeatures) {
            a = 0x11;
        } else {
            a = 0x01;
        }

        for (int r = 0; r < 0x8000; r++) {
            mainRam[r] = 0;
        }

        setBC(0x0013);
        setDE(0x00D8);
        setHL(0x014D);
        System.out.println("CPU reset");

        ioHandler.reset();
    }

    private void setDoubleSpeedCpu(boolean enabled) {

        if (enabled) {
            INSTRS_PER_HBLANK = BASE_INSTRS_PER_HBLANK * 2;
            INSTRS_PER_DIV = BASE_INSTRS_PER_DIV * 2;
        } else {
            INSTRS_PER_HBLANK = BASE_INSTRS_PER_HBLANK;
            INSTRS_PER_DIV = BASE_INSTRS_PER_DIV;
        }

    }

    /**
     * If an interrupt is enabled an the interrupt register shows that it has occured, jump to
     * the relevant interrupt vector address
     */
    private void checkInterrupts() {
        int intFlags = ioHandler.registers[0x0F];
        int ieReg = ioHandler.registers[0xFF];
        if ((intFlags & ieReg) != 0) {
            r.sp().dec();
            r.sp().dec();
            addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());  // Push current program counter onto stack
            addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
            interruptsEnabled = false;

            if ((intFlags & ieReg & INT_VBLANK) != 0) {
                r.pc(0x40);                      // Jump to Vblank interrupt address
                intFlags -= INT_VBLANK;
            } else if ((intFlags & ieReg & INT_LCDC) != 0) {
                r.pc(0x48);
                intFlags -= INT_LCDC;
            } else if ((intFlags & ieReg & INT_TIMA) != 0) {
                r.pc(0x50);
                intFlags -= INT_TIMA;
            } else if ((intFlags & ieReg & INT_SER) != 0) {
                r.pc(0x58);
                intFlags -= INT_SER;
            } else if ((intFlags & ieReg & INT_P10) != 0) {    // Joypad interrupt
                r.pc(0x60);
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

    final void triggerInterruptIfEnabled(int intr) {
        if ((ioHandler.registers[0xFF] & (short) (intr)) != 0) ioHandler.registers[0x0F] |= intr;
    }

    /**
     * Check for interrupts that need to be initiated
     */
    private void initiateInterrupts() {
        if (timaEnabled && ((instrCount % instrsPerTima) == 0)) {
            if (JavaBoy.unsign(ioHandler.registers[05]) == 0) {
                ioHandler.registers[05] = ioHandler.registers[06]; // Set TIMA modulo
                if ((ioHandler.registers[0xFF] & INT_TIMA) != 0)
                    triggerInterrupt(INT_TIMA);
            }
            ioHandler.registers[05]++;
        }

        if ((instrCount % INSTRS_PER_DIV) == 0) {
            ioHandler.registers[04]++;
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


            if ((gbcFeatures) && (ioHandler.hdmaRunning)) {
                ioHandler.performHdma();
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
                    //      System.out.println("Waiting for " + graphicsChip.frameWaitTime + "ms.");
                    try {
                        java.lang.Thread.sleep(graphicsChip.frameWaitTime);
                    } catch (InterruptedException e) {
                        // Nothing.
                    }
                }


            }


            graphicsChip.notifyScanline(JavaBoy.unsign(ioHandler.registers[0x44]));
            ioHandler.registers[0x44] = (byte) (JavaBoy.unsign(ioHandler.registers[0x44]) + 1);
            //	System.out.println("Reg 44 = " + JavaBoy.unsign(ioHandler.registers[0x44]));

            if (JavaBoy.unsign(ioHandler.registers[0x44]) >= 153) {
                //     System.out.println("VBlank");

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

        FlagRegister newf = new FlagRegister();

        int dat;
        graphicsChip.startTime = System.currentTimeMillis();
        int b1, b2, b3, offset;

        while (true) {
            instrCount++;

            b1 = JavaBoy.unsign(addressRead(r.pc().intValue()));
            offset = addressRead(r.pc().intValue() + 1);
            b3 = JavaBoy.unsign(addressRead(r.pc().intValue() + 2));
            b2 = JavaBoy.unsign((short) offset);

            switch (b1) {

                /**
                 * NOP
                 */
                case 0x00:
                    r.pc().inc();
                    break;

                /**
                 * LD BC, nn
                 */
                case 0x01:
                    r.pc().inc();
                    r.pc().inc();
                    r.pc().inc();
                    b = b3;
                    c = b2;
                    break;
                case 0x02:               // LD (BC), A
                    r.pc().inc();
                    addressWrite((b << 8) | c, a);
                    break;
                case 0x03:               // INC BC
                    r.pc().inc();
                    c++;
                    if (c == 0x0100) {
                        b++;
                        c = 0;
                        if (b == 0x0100) {
                            b = 0;
                        }
                    }
                    break;
                case 0x04:               // INC B
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();
                    switch (b) {
                        case 0xFF:
                            r.f().zf().set();
                            r.f().cf().set();
                            b = 0x00;
                            break;
                        case 0x0F:
                            r.f().hf().set();
                            b = 0x10;
                            break;
                        default:
                            b++;
                            break;
                    }
                    break;
                case 0x05:               // DEC B
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().set();
                    r.f().hf().reset();
                    switch (b) {
                        case 0x00:
                            r.f().hf().set();
                            b = 0xFF;
                            break;
                        case 0x10:
                            r.f().hf().set();
                            b = 0x0F;
                            break;
                        case 0x01:
                            r.f().zf().set();
                            b = 0x00;
                            break;
                        default:
                            b--;
                            break;
                    }
                    break;
                case 0x06:               // LD B, nn
                    r.pc().inc();
                    r.pc().inc();
                    b = b2;
                    break;
                case 0x07:               // RLC A
                    r.pc().inc();
                    r.f().reset();

                    a <<= 1;

                    if ((a & 0x0100) != 0) {
                        r.f().cf().set();

                        a |= 1;
                        a &= 0xFF;
                    }
                    if (a == 0) {
                        r.f().zf().set();
                    }
                    break;
                case 0x08:               // LD (nnnn), SP   /* **** May be wrong! **** */
                    r.pc().inc();
                    r.pc().inc();
                    r.pc().inc();
                    addressWrite((b3 << 8) + b2 + 1, r.sp().getHigherByte().intValue());
                    addressWrite((b3 << 8) + b2, r.sp().getLowerByte().intValue());
                    break;
                case 0x09:               // ADD HL, BC
                    r.pc().inc();
                    hl = (hl + ((b << 8) + c));
                    if ((hl & 0xFFFF0000) != 0) {
                        r.f().cf().set();
                        hl &= 0xFFFF;
                    } else {
                        r.f().cf().reset();
                    }
                    break;
                case 0x0A:               // LD A, (BC)
                    r.pc().inc();
                    a = JavaBoy.unsign(addressRead((b << 8) + c));
                    break;
                case 0x0B:               // DEC BC
                    r.pc().inc();
                    c--;
                    if ((c & 0xFF00) != 0) {
                        c = 0xFF;
                        b--;
                        if ((b & 0xFF00) != 0) {
                            b = 0xFF;
                        }
                    }
                    break;
                case 0x0C:               // INC C
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();

                    switch (c) {
                        case 0xFF:
                            r.f().zf().set();
                            r.f().hf().set();
                            c = 0x00;
                            break;
                        case 0x0F:
                            r.f().hf().set();
                            c = 0x10;
                            break;
                        default:
                            c++;
                            break;
                    }
                    break;
                case 0x0D:               // DEC C
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().set();
                    r.f().hf().reset();

                    switch (c) {
                        case 0x00:
                            r.f().hf().set();
                            c = 0xFF;
                            break;
                        case 0x10:
                            r.f().hf().set();
                            c = 0x0F;
                            break;
                        case 0x01:
                            r.f().zf().set();
                            c = 0x00;
                            break;
                        default:
                            c--;
                            break;
                    }
                    break;
                case 0x0E:               // LD C, nn
                    r.pc().inc();
                    r.pc().inc();
                    c = b2;
                    break;
                case 0x0F:               // RRC A
                    r.pc().inc();
                    if ((a & 0x01) == 0x01) {
                        r.f().zf().reset();
                        r.f().nf().reset();
                        r.f().hf().reset();
                        r.f().cf().set();
                    } else {
                        r.f().reset();
                    }
                    a >>= 1;
                    if (r.f().cf().intValue() == 1) {
                        a |= 0x80;
                    }
                    if (a == 0) {
                        r.f().zf().set();
                    }
                    break;
                case 0x10:               // STOP
                    r.pc().inc();
                    r.pc().inc();

                    if (gbcFeatures) {
                        if ((ioHandler.registers[0x4D] & 0x01) == 1) {
                            int newKey1Reg = ioHandler.registers[0x4D] & 0xFE;
                            if ((newKey1Reg & 0x80) == 0x80) {
                                setDoubleSpeedCpu(false);
                                newKey1Reg &= 0x7F;
                            } else {
                                setDoubleSpeedCpu(true);
                                newKey1Reg |= 0x80;
                                //           System.out.println("CAUTION: Game uses double speed CPU, humoungus PC required!");
                            }
                            ioHandler.registers[0x4D] = (byte) newKey1Reg;
                        }
                    }

                    //        terminate = true;
                    //        System.out.println("- Breakpoint reached");
                    break;
                case 0x11:               // LD DE, nnnn
                    r.pc().inc();
                    r.pc().inc();
                    r.pc().inc();
                    d = b3;
                    e = b2;
                    break;
                case 0x12:               // LD (DE), A
                    r.pc().inc();
                    addressWrite((d << 8) + e, a);
                    break;
                case 0x13:               // INC DE
                    r.pc().inc();
                    e++;
                    if (e == 0x0100) {
                        d++;
                        e = 0;
                        if (d == 0x0100) {
                            d = 0;
                        }
                    }
                    break;
                case 0x14:               // INC D
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();
                    switch (d) {
                        case 0xFF:
                            r.f().zf().set();
                            r.f().hf().set();
                            d = 0x00;
                            break;
                        case 0x0F:
                            r.f().hf().set();
                            d = 0x10;
                            break;
                        default:
                            d++;
                            break;
                    }
                    break;
                case 0x15:               // DEC D
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();
                    r.f().nf().set();
                    switch (d) {
                        case 0x00:
                            r.f().hf().set();
                            d = 0xFF;
                            break;
                        case 0x10:
                            r.f().hf().set();
                            d = 0x0F;
                            break;
                        case 0x01:
                            r.f().zf().set();
                            d = 0x00;
                            break;
                        default:
                            d--;
                            break;
                    }
                    break;
                case 0x16:               // LD D, nn
                    r.pc().inc();
                    r.pc().inc();
                    d = b2;
                    break;
                case 0x17:               // RL A
                    r.pc().inc();
                    newf.reset();
                    if ((a & 0x80) == 0x80) {
                        newf.cf().set();
                    }
                    a <<= 1;

                    if (r.f().cf().intValue() == 1) {
                        a |= 1;
                    }

                    a &= 0xFF;
                    if (a == 0) {
                        newf.zf().set();
                    }
                    r.f().setValue(newf.intValue());
                    break;
                case 0x18:               // JR nn
                    r.pc(r.pc().intValue() + 2 + offset);
                    break;
                case 0x19:               // ADD HL, DE
                    r.pc().inc();
                    hl = (hl + ((d << 8) + e));
                    if ((hl & 0xFFFF0000) != 0) {
                        r.f().cf().set();
                        hl &= 0xFFFF;
                    } else {
                        r.f().cf().set();
                    }
                    break;
                case 0x1A:               // LD A, (DE)
                    r.pc().inc();
                    a = JavaBoy.unsign(addressRead((d << 8) + e));
                    break;
                case 0x1B:               // DEC DE
                    r.pc().inc();
                    e--;
                    if ((e & 0xFF00) != 0) {
                        e = 0xFF;
                        d--;
                        if ((d & 0xFF00) != 0) {
                            d = 0xFF;
                        }
                    }
                    break;
                case 0x1C:               // INC E
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();
                    switch (e) {
                        case 0xFF:
                            r.f().zf().set();
                            r.f().hf().set();
                            e = 0x00;
                            break;
                        case 0x0F:
                            r.f().hf().set();
                            e = 0x10;
                            break;
                        default:
                            e++;
                            break;
                    }
                    break;
                case 0x1D:               // DEC E
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().set();
                    r.f().hf().reset();

                    switch (e) {
                        case 0x00:
                            r.f().hf().set();
                            e = 0xFF;
                            break;
                        case 0x10:
                            r.f().hf().set();
                            e = 0x0F;
                            break;
                        case 0x01:
                            r.f().zf().set();
                            e = 0x00;
                            break;
                        default:
                            e--;
                            break;
                    }
                    break;
                case 0x1E:               // LD E, nn
                    r.pc().inc();
                    r.pc().inc();
                    e = b2;
                    break;
                case 0x1F:               // RR A
                    r.pc().inc();
                    newf.reset();
                    if ((a & 0x01) == 0x01) {
                        newf.cf().set();
                    }
                    a >>= 1;

                    if (r.f().cf().intValue() == 1) {
                        a |= 0x80;
                    }

                    if (a == 0) {
                        newf.zf().set();
                    }
                    r.f().setValue(newf.intValue());
                    break;
                case 0x20:               // JR NZ, nn
                    if (r.f().zf().intValue() == 0) {
                        r.pc(r.pc().intValue() + 2 + offset);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0x21:               // LD HL, nnnn
                    r.pc().inc();
                    r.pc().inc();
                    r.pc().inc();
                    hl = (b3 << 8) + b2;
                    break;
                case 0x22:               // LD (HL+), A
                    r.pc().inc();
                    addressWrite(hl, a);
                    hl = (hl + 1) & 0xFFFF;
                    break;
                case 0x23:               // INC HL
                    r.pc().inc();
                    hl = (hl + 1) & 0xFFFF;
                    break;
                case 0x24:               // INC H         ** May be wrong **
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();
                    switch ((hl & 0xFF00) >> 8) {
                        case 0xFF:
                            r.f().zf().set();
                            r.f().hf().set();
                            hl = (hl & 0x00FF);
                            break;
                        case 0x0F:
                            r.f().hf().set();
                            hl = (hl & 0x00FF) | 0x10;
                            break;
                        default:
                            hl = (hl + 0x0100);
                            break;
                    }
                    break;
                case 0x25:               // DEC H           ** May be wrong **
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().set();
                    r.f().hf().reset();

                    switch ((hl & 0xFF00) >> 8) {
                        case 0x00:
                            r.f().hf().set();
                            hl = (hl & 0x00FF) | (0xFF00);
                            break;
                        case 0x10:
                            r.f().hf().set();
                            hl = (hl & 0x00FF) | (0x0F00);
                            break;
                        case 0x01:
                            r.f().zf().set();
                            hl = (hl & 0x00FF);
                            break;
                        default:
                            hl = (hl & 0x00FF) | ((hl & 0xFF00) - 0x0100);
                            break;
                    }
                    break;
                case 0x26:               // LD H, nn
                    r.pc().inc();
                    r.pc().inc();
                    hl = (hl & 0x00FF) | (b2 << 8);
                    break;
                case 0x27:               // DAA         ** This could be wrong! **
                    r.pc().inc();

                    int upperNibble = (a & 0xF0) >> 4;
                    int lowerNibble = a & 0x0F;

                    newf.reset();
                    newf.nf(r.f().nf().intValue());

                    if (r.f().nf().intValue() == 0) {

                        if (r.f().cf().intValue() == 0) {
                            if ((upperNibble <= 8) && (lowerNibble >= 0xA) &&
                                    (r.f().hf().intValue() == 0)) {
                                a += 0x06;
                            }

                            if ((upperNibble <= 9) && (lowerNibble <= 0x3) &&
                                    (r.f().hf().intValue() == 1)) {
                                a += 0x06;
                            }

                            if ((upperNibble >= 0xA) && (lowerNibble <= 0x9) &&
                                    (r.f().hf().intValue() == 0)) {
                                a += 0x60;
                                newf.cf().set();
                            }

                            if ((upperNibble >= 0x9) && (lowerNibble >= 0xA) &&
                                    (r.f().hf().intValue() == 0)) {
                                a += 0x66;
                                newf.cf().set();
                            }

                            if ((upperNibble >= 0xA) && (lowerNibble <= 0x3) &&
                                    (r.f().hf().intValue() == 1)) {
                                a += 0x66;
                                newf.cf().set();
                            }

                        } else {  // If carry set

                            if ((upperNibble <= 0x2) && (lowerNibble <= 0x9) &&
                                    (r.f().hf().intValue() == 0)) {
                                a += 0x60;
                                newf.cf().set();
                            }

                            if ((upperNibble <= 0x2) && (lowerNibble >= 0xA) &&
                                    (r.f().hf().intValue() == 0)) {
                                a += 0x66;
                                newf.cf().set();
                            }

                            if ((upperNibble <= 0x3) && (lowerNibble <= 0x3) &&
                                    (r.f().hf().intValue() == 1)) {
                                a += 0x66;
                                newf.cf().set();
                            }

                        }

                    } else { // Subtract is set

                        if (r.f().cf().intValue() == 0) {

                            if ((upperNibble <= 0x8) && (lowerNibble >= 0x6) &&
                                    (r.f().hf().intValue() == 1)) {
                                a += 0xFA;
                            }

                        } else { // Carry is set

                            if ((upperNibble >= 0x7) && (lowerNibble <= 0x9) &&
                                    (r.f().hf().intValue() == 0)) {
                                a += 0xA0;
                                newf.cf().set();
                            }

                            if ((upperNibble >= 0x6) && (lowerNibble >= 0x6) &&
                                    (r.f().hf().intValue() == 1)) {
                                a += 0x9A;
                                newf.cf().set();
                            }

                        }

                    }

                    a &= 0x00FF;
                    if (a == 0) {
                        newf.zf().set();
                    }

                    r.f().setValue(newf.intValue());

                    break;
                case 0x28:               // JR Z, nn
                    if (r.f().zf().intValue() == 1) {
                        r.pc(r.pc().intValue() + 2 + offset);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0x29:               // ADD HL, HL
                    r.pc().inc();
                    hl = (hl + hl);
                    if ((hl & 0xFFFF0000) != 0) {
                        r.f().cf().set();
                        hl &= 0xFFFF;
                    } else {
                        r.f().cf().reset();
                    }
                    break;
                case 0x2A:               // LDI A, (HL)
                    r.pc().inc();
                    a = JavaBoy.unsign(addressRead(hl));
                    hl++;
                    break;
                case 0x2B:               // DEC HL
                    r.pc().inc();
                    if (hl == 0) {
                        hl = 0xFFFF;
                    } else {
                        hl--;
                    }
                    break;
                case 0x2C:               // INC L
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();

                    switch (hl & 0x00FF) {
                        case 0xFF:
                            r.f().hf().set();
                            r.f().zf().set();
                            hl = hl & 0xFF00;
                            break;
                        case 0x0F:
                            r.f().hf().set();
                            hl++;
                            break;
                        default:
                            hl++;
                            break;
                    }
                    break;
                case 0x2D:               // DEC L
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().set();
                    r.f().hf().reset();

                    switch (hl & 0x00FF) {
                        case 0x00:
                            r.f().hf().set();
                            hl = (hl & 0xFF00) | 0x00FF;
                            break;
                        case 0x10:
                            r.f().hf().set();
                            hl = (hl & 0xFF00) | 0x000F;
                            break;
                        case 0x01:
                            r.f().zf().set();
                            hl = (hl & 0xFF00);
                            break;
                        default:
                            hl = (hl & 0xFF00) | ((hl & 0x00FF) - 1);
                            break;
                    }
                    break;
                case 0x2E:               // LD L, nn
                    r.pc().inc();
                    r.pc().inc();
                    hl = (hl & 0xFF00) | b2;
                    break;
                case 0x2F:               // CPL A
                    r.pc().inc();
                    short mask;
                    a = (short) ((~a) & 0x00FF);
                    r.f().nf().set();
                    r.f().hf().set();

                    break;
                case 0x30:               // JR NC, nn
                    if (r.f().cf().intValue() == 0) {
                        r.pc(r.pc().intValue() + 2 + offset);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0x31:               // LD SP, nnnn
                    r.pc().inc();
                    r.pc().inc();
                    r.pc().inc();
                    r.sp((b3 << 8) + b2);
                    break;
                case 0x32:
                    r.pc().inc();
                    addressWrite(hl, a);  // LD (HL-), A
                    hl--;
                    break;
                case 0x33:               // INC SP
                    r.sp().inc();
                    r.pc().inc();
                    break;
                case 0x34:               // INC (HL)
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();

                    dat = JavaBoy.unsign(addressRead(hl));
                    switch (dat) {
                        case 0xFF:
                            r.f().zf().set();
                            r.f().hf().set();
                            addressWrite(hl, 0x00);
                            break;
                        case 0x0F:
                            r.f().hf().set();
                            addressWrite(hl, 0x10);
                            break;
                        default:
                            addressWrite(hl, dat + 1);
                            break;
                    }
                    break;
                case 0x35:               // DEC (HL)
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().set();
                    r.f().hf().reset();

                    dat = JavaBoy.unsign(addressRead(hl));
                    switch (dat) {
                        case 0x00:
                            r.f().hf().set();
                            addressWrite(hl, 0xFF);
                            break;
                        case 0x10:
                            r.f().hf().set();
                            addressWrite(hl, 0x0F);
                            break;
                        case 0x01:
                            r.f().zf().set();
                            addressWrite(hl, 0x00);
                            break;
                        default:
                            addressWrite(hl, dat - 1);
                            break;
                    }
                    break;
                case 0x36:               // LD (HL), nn
                    r.pc().inc();
                    r.pc().inc();
                    addressWrite(hl, b2);
                    break;
                case 0x37:               // SCF
                    r.pc().inc();
                    r.f().nf().reset();
                    r.f().hf().reset();
                    r.f().cf().set();
                    break;
                case 0x38:               // JR C, nn
                    if (r.f().cf().booleanValue()) {
                        r.pc(r.pc().intValue() + 2 + offset);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0x39:               // ADD HL, SP      ** Could be wrong **
                    r.pc().inc();
                    hl = (hl + r.sp().intValue());
                    if ((hl & 0xFFFF0000) != 0) {
                        //                        f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)) | (F_CARRY));

                        r.f().cf().set();


                        hl &= 0xFFFF;
                    } else {
                        //f = (short) ((f & (F_SUBTRACT + F_ZERO + F_HALFCARRY)));
                        r.f().cf().reset();
                    }
                    break;
                case 0x3A:               // LD A, (HL-)
                    r.pc().inc();
                    a = JavaBoy.unsign(addressRead(hl));
                    hl = (hl - 1) & 0xFFFF;
                    break;
                case 0x3B:               // DEC SP
                    r.sp().dec();
                    r.pc().inc();
                    break;
                case 0x3C:               // INC A
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();
                    switch (a) {
                        case 0xFF:
                            r.f().hf().set();
                            r.f().zf().set();
                            a = 0x00;
                            break;
                        case 0x0F:
                            r.f().hf().set();
                            a = 0x10;
                            break;
                        default:
                            a++;
                            break;
                    }
                    break;
                case 0x3D:               // DEC A
                    r.pc().inc();
                    r.f().zf().reset();
                    r.f().nf().reset();
                    r.f().hf().reset();

                    r.f().nf().set();
                    switch (a) {
                        case 0x00:
                            r.f().hf().set();
                            a = 0xFF;
                            break;
                        case 0x10:
                            r.f().hf().set();
                            a = 0x0F;
                            break;
                        case 0x01:
                            r.f().zf().set();
                            a = 0x00;
                            break;
                        default:
                            a--;
                            break;
                    }
                    break;
                case 0x3E:               // LD A, nn
                    r.pc().inc();
                    r.pc().inc();
                    a = b2;
                    break;
                case 0x3F:               // CCF
                    r.pc().inc();

                    if (r.f().cf().intValue() == 0) {
                        //f = (short) ((f & F_ZERO) | F_CARRY);

                        r.f().nf().reset();
                        r.f().hf().reset();
                        r.f().cf().set();


                    } else {
                        //f = (short) (f & F_ZERO);

                        r.f().nf().reset();
                        r.f().hf().reset();
                        r.f().cf().reset();
                    }
                    break;
                case 0x52:
                    r.pc().inc();
                    break;

                case 0x76:               // HALT
                    interruptsEnabled = true;
                    while (ioHandler.registers[0x0F] == 0) {
                        initiateInterrupts();
                        instrCount++;
                    }
                    r.pc().inc();
                    break;
                case 0xAF:               // XOR A, A (== LD A, 0)
                    r.pc().inc();
                    a = 0;
                    r.f().zf().set();
                    r.f().nf().reset();
                    r.f().hf().reset();
                    r.f().cf().reset();
                    break;
                case 0xC0:               // RET NZ
                    if (r.f().zf().intValue() == 0) {
                        r.pc((JavaBoy.unsign(addressRead(r.sp().intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(r.sp().intValue())));
                        r.sp().inc();
                        r.sp().inc();
                    } else {
                        r.pc().inc();
                    }
                    break;
                case 0xC1:               // POP BC
                    r.pc().inc();
                    c = JavaBoy.unsign(addressRead(r.sp().intValue()));
                    b = JavaBoy.unsign(addressRead(r.sp().intValue() + 1));
                    r.sp().inc();
                    r.sp().inc();
                    break;
                case 0xC2:               // JP NZ, nnnn
                    if (r.f().zf().intValue() == 0) {
                        r.pc((b3 << 8) + b2);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0xC3:
                    r.pc((b3 << 8) + b2);  // JP nnnn
                    break;
                case 0xC4:               // CALL NZ, nnnnn
                    if (r.f().zf().intValue() == 0) {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                        r.sp().dec();
                        r.sp().dec();
                        addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                        addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                        r.pc((b3 << 8) + b2);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0xC5:               // PUSH BC
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    //sp &= 0xFFFF;
                    addressWrite(r.sp().intValue(), c);
                    addressWrite(r.sp().intValue() + 1, b);
                    break;
                case 0xC6:               // ADD A, nn
                    r.pc().inc();
                    r.pc().inc();
                    r.f().reset();

                    if ((((a & 0x0F) + (b2 & 0x0F)) & 0xF0) != 0x00) {
                        r.f().hf().set();
                    }

                    a += b2;

                    if ((a & 0xFF00) != 0) {     // Perform 8-bit overflow and set zero flag
                        r.f().hf().set();
                        r.f().cf().set();
                        if (a == 0x0100) {
                            r.f().zf().set();
                            a = 0;
                        } else {
                            a &= 0x00FF;
                        }
                    }
                    break;
                case 0xCF:               // RST 08
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    r.pc(0x08);
                    break;
                case 0xC8:               // RET Z
                    if (r.f().zf().intValue() == 1) {
                        r.pc((JavaBoy.unsign(addressRead(r.sp().intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(r.sp().intValue())));
                        r.sp().inc();
                        r.sp().inc();
                    } else {
                        r.pc().inc();
                    }
                    break;
                case 0xC9:               // RET
                    r.pc((JavaBoy.unsign(addressRead(r.sp().intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(r.sp().intValue())));
                    r.sp().inc();
                    r.sp().inc();
                    break;
                case 0xCA:               // JP Z, nnnn
                    if (r.f().zf().intValue() == 1) {
                        r.pc((b3 << 8) + b2);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0xCB:               // Shift/bit test
                    r.pc().inc();
                    r.pc().inc();
                    int regNum = b2 & 0x07;
                    int data = registerRead(regNum);
                    //        System.out.println("0xCB instr! - reg " + JavaBoy.hexByte((short) (b2 & 0xF4)));
                    if ((b2 & 0xC0) == 0) {
                        switch ((b2 & 0xF8)) {
                            case 0x00:          // RLC A
                                if ((data & 0x80) == 0x80) {
                                    r.f().zf().reset();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().set();
                                } else {
                                    r.f().reset();
                                }
                                data <<= 1;
                                if (r.f().cf().intValue() == 1) {
                                    data |= 1;
                                }

                                data &= 0xFF;
                                if (data == 0) {
                                    r.f().zf().set();
                                }
                                registerWrite(regNum, data);
                                break;
                            case 0x08:          // RRC A
                                if ((data & 0x01) == 0x01) {
                                    r.f().zf().reset();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().set();
                                } else {
                                    r.f().reset();
                                }
                                data >>= 1;
                                if (r.f().cf().intValue() == 1) {
                                    data |= 0x80;
                                }
                                if (data == 0) {
                                    r.f().zf().set();
                                }
                                registerWrite(regNum, data);
                                break;
                            case 0x10:          // RL r
                                newf.reset();
                                if ((data & 0x80) == 0x80) {
                                    //newf = F_CARRY;
                                    newf.cf().set();
                                }
                                //                                else {
                                //                                    newf = 0;
                                //                                }
                                data <<= 1;

                                if (r.f().cf().intValue() == 1) {
                                    data |= 1;
                                }

                                data &= 0xFF;
                                if (data == 0) {
                                    //newf |= F_ZERO;
                                    newf.zf().set();
                                }
                                r.f().setValue(newf.intValue());
                                registerWrite(regNum, data);
                                break;
                            case 0x18:          // RR r
                                newf.reset();
                                if ((data & 0x01) == 0x01) {
                                    //newf = F_CARRY;
                                    newf.cf().set();

                                }
                                //                                else {
                                //                                    newf = 0;
                                //                                }
                                data >>= 1;

                                if (r.f().cf().intValue() == 1) {
                                    data |= 0x80;
                                }

                                if (data == 0) {
                                    //newf |= F_ZERO;
                                    newf.zf().set();
                                }
                                r.f().setValue(newf.intValue());
                                registerWrite(regNum, data);
                                break;
                            case 0x20:          // SLA r
                                if ((data & 0x80) == 0x80) {
                                    r.f().zf().reset();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().set();
                                } else {
                                    r.f().reset();
                                }

                                data <<= 1;

                                data &= 0xFF;
                                if (data == 0) {
                                    r.f().zf().set();
                                }
                                registerWrite(regNum, data);
                                break;
                            case 0x28:          // SRA r
                                short topBit;

                                topBit = (short) (data & 0x80);
                                if ((data & 0x01) == 0x01) {
                                    r.f().zf().reset();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().set();
                                } else {
                                    r.f().reset();
                                }

                                data >>= 1;
                                data |= topBit;

                                if (data == 0) {
                                    r.f().zf().set();
                                }
                                registerWrite(regNum, data);
                                break;
                            case 0x30:          // SWAP r

                                data = (short) (((data & 0x0F) << 4) | ((data & 0xF0) >> 4));
                                if (data == 0) {
                                    r.f().zf().set();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().reset();
                                } else {
                                    r.f().reset();
                                }
                                //           System.out.println("SWAP - answer is " + JavaBoy.hexByte(data));
                                registerWrite(regNum, data);
                                break;
                            case 0x38:          // SRL r
                                if ((data & 0x01) == 0x01) {
                                    r.f().zf().reset();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().set();
                                } else {
                                    r.f().reset();
                                }

                                data >>= 1;

                                if (data == 0) {
                                    r.f().zf().set();
                                }
                                registerWrite(regNum, data);
                                break;
                        }
                    } else {

                        int bitNumber = (b2 & 0x38) >> 3;

                        if ((b2 & 0xC0) == 0x40) {  // BIT n, r
                            mask = (short) (0x01 << bitNumber);
                            if ((data & mask) != 0) {
                                //f = (short) ((f & F_CARRY) | F_HALFCARRY);
                                r.f().zf().reset();
                                r.f().nf().reset();
                                r.f().hf().set();

                            } else {
                                //f = (short) ((f & F_CARRY) | (F_HALFCARRY + F_ZERO));
                                r.f().zf().set();
                                r.f().nf().reset();
                                r.f().hf().set();
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
                case 0xCC:               // CALL Z, nnnnn
                    if (r.f().zf().intValue() == 1) {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                        r.sp().dec();
                        r.sp().dec();
                        addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                        addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                        r.pc((b3 << 8) + b2);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0xCD:               // CALL nnnn
                    r.pc().inc();
                    r.pc().inc();
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    r.pc((b3 << 8) + b2);
                    break;
                case 0xCE:               // ADC A, nn
                    r.pc().inc();
                    r.pc().inc();

                    if (r.f().cf().intValue() == 1) {
                        b2++;
                    }
                    r.f().reset();

                    if ((((a & 0x0F) + (b2 & 0x0F)) & 0xF0) != 0x00) {
                        r.f().hf().set();
                    }

                    a += b2;

                    if ((a & 0xFF00) != 0) {     // Perform 8-bit overflow and set zero flag
                        r.f().hf().set();
                        r.f().cf().set();
                        if (a == 0x0100) {
                            r.f().zf().set();
                            a = 0;
                        } else {
                            a &= 0x00FF;
                        }
                    }
                    break;
                case 0xC7:               // RST 00
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    //        terminate = true;
                    r.pc(0x00);
                    break;
                case 0xD0:               // RET NC
                    if (r.f().cf().intValue() == 0) {
                        r.pc((JavaBoy.unsign(addressRead(r.sp().intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(r.sp().intValue())));
                        r.sp().inc();
                        r.sp().inc();
                    } else {
                        r.pc().inc();
                    }
                    break;
                case 0xD1:               // POP DE
                    r.pc().inc();
                    e = JavaBoy.unsign(addressRead(r.sp().intValue()));
                    d = JavaBoy.unsign(addressRead(r.sp().intValue() + 1));
                    r.sp().inc();
                    r.sp().inc();
                    break;
                case 0xD2:               // JP NC, nnnn
                    if (r.f().cf().intValue() == 0) {
                        r.pc((b3 << 8) + b2);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0xD4:               // CALL NC, nnnn
                    if (r.f().cf().intValue() == 0) {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                        r.sp().dec();
                        r.sp().dec();
                        addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                        addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                        r.pc((b3 << 8) + b2);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0xD5:               // PUSH DE
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    //sp &= 0xFFFF;
                    addressWrite(r.sp().intValue(), e);
                    addressWrite(r.sp().intValue() + 1, d);
                    break;
                case 0xD6:               // SUB A, nn
                    r.pc().inc();
                    r.pc().inc();

                    r.f().zf().reset();
                    r.f().nf().set();
                    r.f().hf().reset();
                    r.f().cf().reset();

                    if ((((a & 0x0F) - (b2 & 0x0F)) & 0xFFF0) != 0x00) {
                        r.f().hf().set();
                    }

                    a -= b2;

                    if ((a & 0xFF00) != 0) {
                        a &= 0x00FF;
                        r.f().cf().set();
                    }
                    if (a == 0) {
                        r.f().zf().set();
                    }
                    break;
                case 0xD7:               // RST 10
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    r.pc(0x10);
                    break;
                case 0xD8:               // RET C
                    if (r.f().cf().intValue() == 1) {
                        r.pc((JavaBoy.unsign(addressRead(r.sp().intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(r.sp().intValue())));
                        r.sp().inc();
                        r.sp().inc();
                    } else {
                        r.pc().inc();
                    }
                    break;
                case 0xD9:               // RETI
                    interruptsEnabled = true;
                    r.pc((JavaBoy.unsign(addressRead(r.sp().intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(r.sp().intValue())));
                    r.sp().inc();
                    r.sp().inc();
                    break;
                case 0xDA:               // JP C, nnnn
                    if (r.f().cf().intValue() == 1) {
                        r.pc((b3 << 8) + b2);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0xDC:               // CALL C, nnnn
                    if (r.f().cf().intValue() == 1) {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                        r.sp().dec();
                        r.sp().dec();
                        addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                        addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                        r.pc((b3 << 8) + b2);
                    } else {
                        r.pc().inc();
                        r.pc().inc();
                        r.pc().inc();
                    }
                    break;
                case 0xDE:               // SBC A, nn
                    r.pc().inc();
                    r.pc().inc();
                    if (r.f().cf().intValue() == 1) {
                        b2++;
                    }

                    r.f().zf().reset();
                    r.f().nf().set();
                    r.f().hf().reset();
                    r.f().cf().reset();
                    if ((((a & 0x0F) - (b2 & 0x0F)) & 0xFFF0) != 0x00) {
                        r.f().hf().set();
                    }

                    a -= b2;

                    if ((a & 0xFF00) != 0) {
                        a &= 0x00FF;
                        r.f().cf().set();
                    }

                    if (a == 0) {
                        r.f().zf().set();
                    }
                    break;
                case 0xDF:               // RST 18
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    r.pc(0x18);
                    break;
                case 0xE0:               // LDH (FFnn), A
                    r.pc().inc();
                    r.pc().inc();
                    addressWrite(0xFF00 + b2, a);
                    break;
                case 0xE1:               // POP HL
                    r.pc().inc();
                    hl = (JavaBoy.unsign(addressRead(r.sp().intValue() + 1)) << 8) + JavaBoy.unsign(addressRead(r.sp().intValue()));
                    r.sp().inc();
                    r.sp().inc();
                    break;
                case 0xE2:               // LDH (FF00 + C), A
                    r.pc().inc();
                    addressWrite(0xFF00 + c, a);
                    break;
                case 0xE5:               // PUSH HL
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    //sp &= 0xFFFF;
                    addressWrite(r.sp().intValue() + 1, hl >> 8);
                    addressWrite(r.sp().intValue(), hl & 0x00FF);
                    break;
                case 0xE6:               // AND nn
                    r.pc().inc();
                    r.pc().inc();
                    a &= b2;
                    if (a == 0) {
                        r.f().zf().set();
                        r.f().nf().reset();
                        r.f().hf().reset();
                        r.f().cf().reset();
                    } else {
                        r.f().reset();
                    }
                    break;
                case 0xE7:               // RST 20
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    r.pc(0x20);
                    break;
                case 0xE8: {               // ADD SP, nn
                    r.pc().inc();
                    r.pc().inc();
                    int result = r.sp().intValue() + offset;
                    if ((result & 0xFFFF0000) != 0) {
                        r.f().cf().set();
                    } else {
                        r.f().cf().reset();
                    }
                    r.sp(result);
                    break;
                }
                case 0xE9:               // JP (HL)
                    r.pc().inc();
                    r.pc(hl);
                    break;
                case 0xEA:               // LD (nnnn), A
                    r.pc().inc();
                    r.pc().inc();
                    r.pc().inc();
                    addressWrite((b3 << 8) + b2, a);
                    break;
                case 0xEE:               // XOR A, nn
                    r.pc().inc();
                    r.pc().inc();
                    a ^= b2;
                    if (a == 0) {
                        r.f().zf().set();
                        r.f().nf().reset();
                        r.f().hf().reset();
                        r.f().cf().reset();
                    } else {
                        r.f().reset();
                    }
                    break;
                case 0xEF:               // RST 28
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    r.pc(0x28);
                    break;
                case 0xF0:               // LDH A, (FFnn)
                    r.pc().inc();
                    r.pc().inc();
                    a = JavaBoy.unsign(addressRead(0xFF00 + b2));
                    break;
                case 0xF1:               // POP AF
                    r.pc().inc();
                    r.f(JavaBoy.unsign(addressRead(r.sp().intValue())));
                    a = JavaBoy.unsign(addressRead(r.sp().intValue() + 1));
                    r.sp().inc();
                    r.sp().inc();
                    break;
                case 0xF2:               // LD A, (FF00 + C)
                    r.pc().inc();
                    a = JavaBoy.unsign(addressRead(0xFF00 + c));
                    break;
                case 0xF3:               // DI
                    r.pc().inc();
                    interruptsEnabled = false;
                    //    addressWrite(0xFFFF, 0);
                    break;
                case 0xF5:               // PUSH AF
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    //sp &= 0xFFFF;
                    addressWrite(r.sp().intValue(), r.f().intValue());
                    addressWrite(r.sp().intValue() + 1, a);
                    break;
                case 0xF6:               // OR A, nn
                    r.pc().inc();
                    r.pc().inc();
                    a |= b2;
                    if (a == 0) {

                        r.f().zf().set();
                        r.f().nf().reset();
                        r.f().hf().reset();
                        r.f().cf().reset();
                    } else {
                        r.f().reset();
                    }
                    break;
                case 0xF7:               // RST 30
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    r.pc(0x30);
                    break;
                case 0xF8:               // LD HL, SP + nn  ** HALFCARRY FLAG NOT SET ***
                    r.pc().inc();
                    r.pc().inc();
                    hl = (r.sp().intValue() + offset);
                    if ((hl & 0x10000) != 0) {
                        r.f().zf().reset();
                        r.f().nf().reset();
                        r.f().hf().reset();
                        r.f().cf().set();
                        hl &= 0xFFFF;
                    } else {
                        r.f().reset();
                    }
                    break;
                case 0xF9:               // LD SP, HL
                    r.pc().inc();
                    r.sp(hl);
                    break;
                case 0xFA:               // LD A, (nnnn)
                    r.pc().inc();
                    r.pc().inc();
                    r.pc().inc();
                    a = JavaBoy.unsign(addressRead((b3 << 8) + b2));
                    break;
                case 0xFB:               // EI
                    r.pc().inc();
                    ieDelay = 1;
                    //  interruptsEnabled = true;
                    //  addressWrite(0xFFFF, 0xFF);
                    break;
                case 0xFE:               // CP nn     ** FLAGS ARE WRONG! **
                    r.pc().inc();
                    r.pc().inc();
                    r.f().reset();
                    if (b2 == a) {
                        r.f().zf().set();
                    } else {
                        if (a < b2) {
                            r.f().cf().set();
                        }
                    }
                    break;
                case 0xFF:               // RST 38
                    r.pc().inc();
                    r.sp().dec();
                    r.sp().dec();
                    addressWrite(r.sp().intValue() + 1, r.pc().getHigherByte().intValue());
                    addressWrite(r.sp().intValue(), r.pc().getLowerByte().intValue());
                    r.pc(0x38);
                    break;

                default:

                    if ((b1 & 0xC0) == 0x80) {       // Byte 0x10?????? indicates ALU op
                        r.pc().inc();
                        int operand = registerRead(b1 & 0x07);
                        switch ((b1 & 0x38) >> 3) {
                            case 1: // ADC A, r
                                if (r.f().cf().intValue() == 1) {
                                    operand++;
                                }
                                // Note!  No break!
                            case 0: // ADD A, r

                                r.f().reset();

                                if ((((a & 0x0F) + (operand & 0x0F)) & 0xF0) != 0x00) {
                                    r.f().hf().set();
                                }

                                a += operand;

                                if (a == 0) {
                                    r.f().zf().set();
                                }

                                if ((a & 0xFF00) != 0) {     // Perform 8-bit overflow and set zero flag
                                    if (a == 0x0100) {
                                        r.f().zf().set();
                                        r.f().hf().set();
                                        r.f().cf().set();

                                        a = 0;
                                    } else {
                                        r.f().hf().set();
                                        r.f().cf().set();
                                        a &= 0x00FF;
                                    }
                                }
                                break;
                            case 3: // SBC A, r
                                if (r.f().cf().intValue() == 1) {
                                    operand++;
                                }
                                // Note! No break!
                                // todo ARRRRRRGH
                            case 2: // SUB A, r
                                r.f().zf().reset();
                                r.f().nf().set();
                                r.f().hf().reset();
                                r.f().cf().reset();

                                if ((((a & 0x0F) - (operand & 0x0F)) & 0xFFF0) != 0x00) {
                                    r.f().hf().set();
                                }

                                a -= operand;

                                if ((a & 0xFF00) != 0) {
                                    a &= 0x00FF;
                                    r.f().cf().set();
                                }
                                if (a == 0) {
                                    r.f().zf().set();
                                }

                                break;
                            case 4: // AND A, r
                                a &= operand;
                                if (a == 0) {
                                    r.f().zf().set();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().reset();
                                } else {
                                    r.f().reset();
                                }
                                break;
                            case 5: // XOR A, r
                                a ^= operand;
                                if (a == 0) {
                                    r.f().zf().set();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().reset();
                                } else {
                                    r.f().reset();
                                }
                                break;
                            case 6: // OR A, r
                                a |= operand;
                                if (a == 0) {
                                    r.f().zf().set();
                                    r.f().nf().reset();
                                    r.f().hf().reset();
                                    r.f().cf().reset();
                                } else {
                                    r.f().reset();
                                }
                                break;
                            case 7: // CP A, r (compare)
                                r.f().zf().reset();
                                r.f().nf().set();
                                r.f().hf().reset();
                                r.f().cf().reset();
                                if (a == operand) {
                                    r.f().zf().set();
                                }
                                if (a < operand) {
                                    r.f().cf().set();
                                }
                                if ((a & 0x0F) < (operand & 0x0F)) {
                                    r.f().hf().set();
                                }
                                break;
                        }
                    } else if ((b1 & 0xC0) == 0x40) {   // Byte 0x01xxxxxxx indicates 8-bit ld

                        r.pc().inc();
                        registerWrite((b1 & 0x38) >> 3, registerRead(b1 & 0x07));

                    } else {
                        System.out.println("Unrecognized opcode (" + String.format("%02X", b1) + ")");
                        r.pc().inc();
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

            cartridge.update();


            initiateInterrupts();
        }
    }
}
