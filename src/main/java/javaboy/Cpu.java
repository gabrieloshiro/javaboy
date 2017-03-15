package javaboy;

import javaboy.graphics.GraphicsChip;
import javaboy.lang.Bit;
import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;
import javaboy.memory.Memory;
import javaboy.rom.loader.RomLoader;
import org.pmw.tinylog.Logger;

import java.awt.*;

import static javaboy.lang.Bit.ONE;
import static javaboy.lang.Bit.ZERO;

public class Cpu implements ReadableWritable {

    private static final int ROM_SIZE = 0x8000;

    private final Registers registers;

    private final Memory rom;

    private final InstructionCounter instructionCounter = new InstructionCounter();

    private boolean interruptsEnabled = false;

    /**
     * Used to implement the IE delay slot
     */
    private int ieDelay = -1;

    boolean timaEnabled = false;
    int instructionsPerTima = 6000;

    // 8Kb main system RAM appears at 0xC000 in address space
    // 32Kb for GBC
    private final byte[] mainRam = new byte[ROM_SIZE];

    // 256 bytes at top of RAM are used mainly for registers
    private final Memory oam = new Memory(0xFE00, 0x100);

    final GraphicsChip graphicsChip;
    public final IoHandler ioHandler;
    private final Component applet;

    Cpu(Component a) {
        rom = RomLoader.loadRom("bgblogo.gb", ROM_SIZE);
        registers = new Registers(this);
        graphicsChip = new GraphicsChip(a, this);
        ioHandler = new IoHandler(this, instructionCounter);
        applet = a;
    }

    @Override
    public Byte read(Short address) {

        switch ((address.intValue() & 0xF000)) {
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                return rom.read(address);

            case 0x8000:
            case 0x9000:
                return new Byte(graphicsChip.addressRead(address.intValue() - 0x8000));

            case 0xA000:
            case 0xB000:
                return rom.read(address);

            case 0xC000:
                return new Byte((mainRam[address.intValue() - 0xC000]));

            case 0xD000:
                return new Byte((mainRam[address.intValue() - 0xD000]));

            case 0xE000:
                return new Byte(mainRam[address.intValue() - 0xE000]);

            case 0xF000:
                if (address.intValue() < 0xFE00) {
                    return new Byte(mainRam[address.intValue() - 0xE000]);
                } else if (address.intValue() < 0xFF00) {
                    return oam.read(address);
                } else {
                    return new Byte(ioHandler.ioRead(address.intValue() - 0xFF00));
                }

            default:
                Logger.debug("Tried to read address " + address + ".  pc = " + String.format("%04X", registers.pc.intValue()));
                throw new IllegalStateException("");
        }

    }

    private void write(Short address, Short data) {
        write(address, data.getLowerByte());
        write(new Short(address.intValue() + 1), data.getUpperByte());
    }

    @Override
    public void write(Short address, Byte data) {

        switch (address.intValue() & 0xF000) {
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
                graphicsChip.addressWrite(address.intValue() - 0x8000, (byte) data.intValue());
                break;

            case 0xA000:
            case 0xB000:
                break;

            case 0xC000:
                mainRam[address.intValue() - 0xC000] = (byte) data.intValue();
                break;

            case 0xD000:
                mainRam[address.intValue() - 0xD000] = (byte) data.intValue();
                break;

            case 0xE000:
                mainRam[address.intValue() - 0xE000] = (byte) data.intValue();
                break;

            case 0xF000:
                if (address.intValue() < 0xFE00) {
                    try {
                        mainRam[address.intValue() - 0xE000] = (byte) data.intValue();
                    } catch (ArrayIndexOutOfBoundsException e) {
                        Logger.debug("Address error: " + address + " pc = " + String.format("%04X", registers.pc.intValue()));
                    }
                } else if (address.intValue() < 0xFF00) {
                    oam.write(address, data);
                } else {
                    ioHandler.ioWrite(address.intValue() - 0xFF00, (short) data.intValue());
                }
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
        registers.pc.setValue(0x0100);
        registers.sp.setValue(0xFFFE);

        registers.f.zf(ONE);
        registers.f.nf(ZERO);
        registers.f.hf(ONE);
        registers.f.cf(ONE);

        registers.a.setValue(0x01);
        registers.bc.setValue(0x0013);
        registers.de.setValue(0x00D8);
        registers.hl.setValue(0x014D);
        Logger.debug("CPU reset");
        ioHandler.reset();
    }

    /**
     * If an interrupt is enabled an the interrupt register shows that it has occurred, jump to
     * the relevant interrupt vector address
     */
    private void checkInterrupts() {
        int intFlags = ioHandler.read(new Short(0xFF0F)).intValue();
        int ieReg = ioHandler.read(new Short(0xFFFF)).intValue();
        if ((intFlags & ieReg) != 0) {
            registers.sp.dec();
            registers.sp.dec();
            write(registers.sp, registers.pc);// Push current program counter onto stack
            interruptsEnabled = false;

            if ((intFlags & ieReg & Interrupts.INT_VBLANK) != 0) {
                registers.pc.setValue(0x40);                      // Jump to Vblank interrupt address
                intFlags -= Interrupts.INT_VBLANK;
            } else if ((intFlags & ieReg & Interrupts.INT_LCDC) != 0) {
                registers.pc.setValue(0x48);
                intFlags -= Interrupts.INT_LCDC;
            } else if ((intFlags & ieReg & Interrupts.INT_TIMA) != 0) {
                registers.pc.setValue(0x50);
                intFlags -= Interrupts.INT_TIMA;
            } else if ((intFlags & ieReg & Interrupts.INT_SER) != 0) {
                registers.pc.setValue(0x58);
                intFlags -= Interrupts.INT_SER;
            } else if ((intFlags & ieReg & Interrupts.INT_P10) != 0) {    // Joypad interrupt
                registers.pc.setValue(0x60);
                intFlags -= Interrupts.INT_P10;
            }

            ioHandler.write(new Short(0xFF0F), new Byte(intFlags));
        }
    }

    /**
     * Initiate an interrupt of the specified type
     */
    private void triggerInterrupt(int interrupt) {
        Byte data = ioHandler.read(new Short(0xFF0F));
        ioHandler.write(new Short(0xFF0F), new Byte(data.intValue() | interrupt));
    }

    /**
     * Check for interrupts that need to be initiated
     */
    private void initiateInterrupts() {
        if (timaEnabled && ((instructionCounter.getCount() % instructionsPerTima) == 0)) {
            if (ioHandler.read(new Short(0xFF05)).intValue() == 0) {
                ioHandler.write(new Short(0xFF05), ioHandler.read(new Short(0xFF06))); // Set TIMA modulo
                if ((ioHandler.read(new Short(0xFFFF)).intValue() & Interrupts.INT_TIMA) != 0)
                    triggerInterrupt(Interrupts.INT_TIMA);
            }
            ioHandler.read(new Short(0xFF05)).inc();
        }

        short INSTRS_PER_DIV = GraphicsConstants.BASE_INSTRS_PER_DIV;
        if ((instructionCounter.getCount() % INSTRS_PER_DIV) == 0) {
            ioHandler.read(new Short(0xFF04)).inc();
        }

        if ((instructionCounter.getCount() % GraphicsConstants.INSTRS_PER_HBLANK) == 0) {

            // LCY Coincidence
            // The +1 is due to the LCY register being just about to be incremented
            int cline = ioHandler.read(new Short(0xFF44)).intValue() + 1;
            if (cline == 152) cline = 0;

            if (((ioHandler.read(new Short(0xFFFF)).intValue() & Interrupts.INT_LCDC) != 0) &&
                    ((ioHandler.read(new Short(0xFF41)).intValue() & 64) != 0) &&
                    (ioHandler.read(new Short(0xFF45)).intValue() == cline) && ((ioHandler.read(new Short(0xFF40)).intValue() & 0x80) != 0) && (cline < 0x90)) {
                triggerInterrupt(Interrupts.INT_LCDC);
            }

            // Trigger on every line
            if (((ioHandler.read(new Short(0xFFFF)).intValue() & Interrupts.INT_LCDC) != 0) &&
                    ((ioHandler.read(new Short(0xFF41)).intValue() & 0x8) != 0) && ((ioHandler.read(new Short(0xFF40)).intValue() & 0x80) != 0) && (cline < 0x90)) {
                triggerInterrupt(Interrupts.INT_LCDC);
            }

            if (ioHandler.read(new Short(0xFF44)).intValue() == 143) {
                for (int r = GraphicsChip.HEIGHT; r < 170; r++) {
                    graphicsChip.notifyScanline(r);
                }
                if (((ioHandler.read(new Short(0xFF40)).intValue() & 0x80) != 0) && ((ioHandler.read(new Short(0xFFFF)).intValue() & Interrupts.INT_VBLANK) != 0)) {
                    triggerInterrupt(Interrupts.INT_VBLANK);
                    if (((ioHandler.read(new Short(0xFF41)).intValue() & 16) != 0) && ((ioHandler.read(new Short(0xFFFF)).intValue() & Interrupts.INT_LCDC) != 0)) {
                        triggerInterrupt(Interrupts.INT_LCDC);
                    }
                }

                if (graphicsChip.frameWaitTime >= 0) {
                    try {
                        java.lang.Thread.sleep(graphicsChip.frameWaitTime);
                    } catch (InterruptedException e) {
                        Logger.debug("Error while thread sleeping.");
                    }
                }
            }

            graphicsChip.notifyScanline(ioHandler.read(new Short(0xFF44)).intValue());
            ioHandler.read(new Short(0xFF44)).inc();

            if (ioHandler.read(new Short(0xFF44)).intValue() >= 153) {
                //     Logger.debug("VBlank");

                ioHandler.read(new Short(0xFF44)).setValue(0);
                graphicsChip.frameDone = false;
                applet.repaint();
                try {
                    while (!graphicsChip.frameDone) {
                        java.lang.Thread.sleep(1);
                    }
                } catch (InterruptedException ignored) {
                    Logger.debug("Error while sleeping.");
                }
            }
        }
    }

    final void execute() {

        final FlagRegister newf = new FlagRegister();

        graphicsChip.startTime = System.currentTimeMillis();

        while (true) {
            instructionCounter.inc();

            Byte opcode = loadImmediateByte(registers.pc);

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
                    Short data = loadImmediateShort(registers.pc);
                    load(registers.bc, data);
                    break;
                }

                /*
                  LD (BC), A
                 */
                case 0x02:
                    write(registers.bc, registers.a);
                    break;

                /*
                  INC BC
                 */
                case 0x03:
                    registers.bc.inc();
                    break;

                /*
                  INC B
                */
                case 0x04:
                    inc(registers.b);
                    break;

                /*
                  DEC B
                 */
                case 0x05:
                    dec(registers.b);
                    break;

                /*
                  LD B, n
                 */
                case 0x06: {
                    Byte data = loadImmediateByte(registers.pc);
                    load(registers.b, data);
                    break;
                }

                /*
                  RLCA
                 */
                case 0x07:
                    rlca(registers.a);
                    break;

                /*
                  LD (nn), SP
                */
                case 0x08: {
                    Short address = loadImmediateShort(registers.pc);
                    write(address, registers.sp);
                    break;
                }

                /*
                  ADD HL, BC
                 */
                case 0x09: {
                    add(registers.hl, registers.bc);
                    break;
                }

                /*
                  LD A, (BC)
                 */
                case 0x0A: {
                    Byte data = read(registers.bc);
                    registers.a.setValue(data);
                    break;
                }

                /*
                  DEC BC
                 */
                case 0x0B:
                    registers.bc.dec();
                    break;

                /*
                  INC C
                 */
                case 0x0C:
                    inc(registers.c);
                    break;

                /*
                  DEC C
                 */
                case 0x0D:
                    dec(registers.c);
                    break;

                /*
                  LD C, n
                 */
                case 0x0E: {
                    Byte data = loadImmediateByte(registers.pc);
                    load(registers.c, data);
                    break;
                }

                /*
                 RRCA
                 */
                case 0x0F:
                    rrca(registers.a);
                    break;

                /*
                 STOP
                 */
                case 0x10:
                    registers.pc.inc();
                    break;

                /*
                 LD DE, nn
                 */
                case 0x11: {
                    Short data = loadImmediateShort(registers.pc);
                    registers.de.setValue(data.intValue());
                    break;
                }

                /*
                 LD (DE), A
                 */
                case 0x12:
                    write(registers.de, registers.a);
                    break;

                /*
                 INC DE
                 */
                case 0x13:
                    registers.de.inc();
                    break;

                /*
                 INC D
                 */
                case 0x14:
                    inc(registers.d);
                    break;

                /*
                 DEC D
                 */
                case 0x15:
                    dec(registers.d);
                    break;

                /*
                 LD D, n
                 */
                case 0x16: {
                    Byte data = loadImmediateByte(registers.pc);
                    load(registers.d, data);
                    break;
                }

                /*
                 RL A
                 */
                case 0x17:
                    rl(registers.a, registers.f.cf());
                    break;

                /*
                 JR n
                 */
                case 0x18: {
                    Byte offset = loadImmediateByte(registers.pc);
                    jr(true, offset);
                    break;
                }

                /*
                 ADD HL, DE
                 */
                case 0x19: {
                    add(registers.hl, registers.de);
                    break;
                }

                /*
                 LD A, (DE)
                 */
                case 0x1A: {
                    Byte data = read(registers.de);
                    load(registers.a, data);
                    break;
                }

                /*
                 DEC DE
                 */
                case 0x1B:
                    registers.de.inc();
                    break;

                /*
                 INC E
                 */
                case 0x1C:
                    inc(registers.e);
                    break;

                /*
                 DEC E
                 */
                case 0x1D:
                    dec(registers.e);
                    break;

                /*
                LD E, n
                */
                case 0x1E: {
                    Byte data = loadImmediateByte(registers.pc);
                    load(registers.e, data);
                    break;
                }

                /*
                 RR A
                 */
                case 0x1F: {
                    rra(registers.a, registers.f.cf());
                    break;
                }

                /*
                 JR NZ, n
                  */
                case 0x20: {
                    Byte address = loadImmediateByte(registers.pc);
                    jr(registers.f.zf() == ZERO, address);
                }
                break;

                /*
                 LD HL, nn
                 */
                case 0x21: {
                    Short address = loadImmediateShort(registers.pc);
                    load(registers.hl, address);
                    break;
                }

                /*
                 LDH (HL), A
                 */
                case 0x22:
                    write(registers.hl, registers.a);
                    registers.hl.inc();
                    break;

                /*
                 INC HL
                 */
                case 0x23:
                    registers.hl.inc();
                    break;

                /*
                 INC H
                 */
                case 0x24:
                    inc(registers.h);
                    break;

                /*
                 DEC H
                 */
                case 0x25:
                    dec(registers.h);
                    break;

                /*
                 LD H, n
                 */
                case 0x26: {
                    Byte data = loadImmediateByte(registers.pc);
                    load(registers.h, data);
                    break;
                }

                /*
                 DAA
                 */
                case 0x27:
                    int upperNibble = registers.a.upperNibble();
                    int lowerNibble = registers.a.lowerNibble();

                    newf.setValue(0);
                    newf.nf(registers.f.nf());

                    if (registers.f.nf().intValue() == 0) {

                        if (registers.f.cf().intValue() == 0) {
                            if ((upperNibble <= 8) && (lowerNibble >= 0xA) &&
                                    (registers.f.hf().intValue() == 0)) {
                                registers.a.setValue(registers.a.intValue() + 0x06);
                            }

                            if ((upperNibble <= 9) && (lowerNibble <= 0x3) &&
                                    (registers.f.hf().intValue() == 1)) {
                                registers.a.setValue(registers.a.intValue() + 0x06);
                            }

                            if ((upperNibble >= 0xA) && (lowerNibble <= 0x9) &&
                                    (registers.f.hf().intValue() == 0)) {
                                registers.a.setValue(registers.a.intValue() + 0x60);
                                newf.cf(ONE);
                            }

                            if ((upperNibble >= 0x9) && (lowerNibble >= 0xA) &&
                                    (registers.f.hf().intValue() == 0)) {
                                registers.a.setValue(registers.a.intValue() + 0x66);
                                newf.cf(ONE);
                            }

                            if ((upperNibble >= 0xA) && (lowerNibble <= 0x3) &&
                                    (registers.f.hf().intValue() == 1)) {
                                registers.a.setValue(registers.a.intValue() + 0x66);
                                newf.cf(ONE);
                            }

                        } else {  // If carry set

                            if ((upperNibble <= 0x2) && (lowerNibble <= 0x9) &&
                                    (registers.f.hf().intValue() == 0)) {
                                registers.a.setValue(registers.a.intValue() + 0x60);
                                newf.cf(ONE);
                            }

                            if ((upperNibble <= 0x2) && (lowerNibble >= 0xA) &&
                                    (registers.f.hf().intValue() == 0)) {
                                registers.a.setValue(registers.a.intValue() + 0x66);
                                newf.cf(ONE);
                            }

                            if ((upperNibble <= 0x3) && (lowerNibble <= 0x3) &&
                                    (registers.f.hf().intValue() == 1)) {
                                registers.a.setValue(registers.a.intValue() + 0x66);
                                newf.cf(ONE);
                            }

                        }

                    } else { // Subtract is set

                        if (registers.f.cf().intValue() == 0) {

                            if ((upperNibble <= 0x8) && (lowerNibble >= 0x6) &&
                                    (registers.f.hf().intValue() == 1)) {
                                registers.a.setValue(registers.a.intValue() + 0xFA);
                            }

                        } else { // Carry is set

                            if ((upperNibble >= 0x7) && (lowerNibble <= 0x9) &&
                                    (registers.f.hf().intValue() == 0)) {
                                registers.a.setValue(registers.a.intValue() + 0xA0);
                                newf.cf(ONE);
                            }

                            if ((upperNibble >= 0x6) && (lowerNibble >= 0x6) &&
                                    (registers.f.hf().intValue() == 1)) {
                                registers.a.setValue(registers.a.intValue() + 0x9A);
                                newf.cf(ONE);
                            }

                        }

                    }

                    if (registers.a.intValue() == 0) {
                        newf.zf(ONE);
                    }

                    registers.f.setValue(newf.intValue());

                    break;

                /*
                 JR Z, n
                 */
                case 0x28: {
                    Byte address = loadImmediateByte(registers.pc);
                    jr(registers.f.zf() == ONE, address);
                    break;
                }

                /*
                 ADD HL, HL
                 */
                case 0x29: {
                    add(registers.hl, registers.hl);
                    break;
                }

                /*
                 LDI A, (HL)
                 */
                case 0x2A: {
                    Byte data = read(registers.hl);
                    load(registers.a, data);
                    registers.hl.inc();
                    break;
                }

                /*
                 DEC HL
                 */
                case 0x2B:
                    registers.hl.dec();
                    break;

                /*
                 INC L
                 */
                case 0x2C:
                    inc(registers.l);
                    break;

                /*
                 DEC L
                 */
                case 0x2D:
                    dec(registers.l);
                    break;

                /*
                 LD L, n
                 */
                case 0x2E: {
                    Byte address = loadImmediateByte(registers.pc);
                    load(registers.l, address);
                    break;
                }

                /*
                 CPL A
                 */
                case 0x2F:
                    registers.a.setValue((~registers.a.intValue()));
                    registers.f.nf(ONE);
                    registers.f.hf(ONE);
                    break;

                /*
                 JR NC, n
                 */
                case 0x30: {
                    Byte address = loadImmediateByte(registers.pc);
                    jr(registers.f.cf() == ZERO, address);
                    break;
                }

                /*
                 LD SP, nn
                 */
                case 0x31: {
                    Short address = loadImmediateShort(registers.pc);
                    load(registers.sp, address);
                    break;
                }

                /*
                 LD (HL-), A
                 */
                case 0x32:
                    write(registers.hl, registers.a);
                    registers.hl.dec();
                    break;

                /*
                 INC SP
                 */
                case 0x33:
                    registers.sp.inc();
                    break;

                /*
                 INC (HL)
                  */
                case 0x34: {
                    Byte data = read(registers.hl);
                    inc(data);
                    write(registers.hl, data);
                    break;
                }

                /*
                 DEC (HL)
                 */
                case 0x35:
                    registers.hl.dec();
                    break;

                /*
                 LD (HL), n
                 */
                case 0x36: {
                    Byte address = loadImmediateByte(registers.pc);
                    write(registers.hl, address);
                    break;
                }

                /*
                 SCF
                 */
                case 0x37:
                    registers.f.nf(ZERO);
                    registers.f.hf(ZERO);
                    registers.f.cf(ONE);
                    break;

                /*
                 JR C, n
                 */
                case 0x38: {
                    Byte address = loadImmediateByte(registers.pc);
                    jr(registers.f.cf() == ONE, address);
                    break;
                }

                /*
                 ADD HL, SP
                 */
                case 0x39: {
                    add(registers.hl, registers.sp);
                    break;
                }

                /*
                 LD A, (HL-)
                 */
                case 0x3A: {
                    Byte data = read(registers.hl);
                    load(registers.a, data);
                    registers.hl.dec();
                    break;
                }

                /*
                 DEC SP
                 */
                case 0x3B:
                    registers.sp.dec();
                    break;

                /*
                 INC A
                 */
                case 0x3C:
                    inc(registers.a);
                    break;

                /*
                 DEC A
                 */
                case 0x3D:
                    dec(registers.a);
                    break;

                /*
                 LD A, n
                 */
                case 0x3E: {
                    Byte data = loadImmediateByte(registers.pc);
                    load(registers.a, data);
                    break;
                }

                /*
                 CCF
                 */
                case 0x3F:
                    registers.f.nf(ZERO);
                    registers.f.hf(ZERO);
                    registers.f.cf(registers.f.cf().toggle());
                    break;

                case 0x52:
                    break;

                /*
                 HALT
                 */
                case 0x76:
                    interruptsEnabled = true;
                    while (ioHandler.read(new Short(0xFF0F)).intValue() == 0) {
                        initiateInterrupts();
                        instructionCounter.inc();
                    }
                    break;

                /*
                 XOR A, A
                 */
                case 0xAF:
                    xor(registers.a, registers.a);
                    break;

                /*
                 RET NZ
                 */
                case 0xC0:
                    ret(registers.f.zf() == ZERO, registers.sp);
                    break;

                // POP BC
                case 0xC1: {
                    load(registers.bc, popShort(registers.sp));
                    break;
                }

                // JP NZ, n
                case 0xC2: {
                    Short address = loadImmediateShort(registers.pc);
                    jp(registers.f.zf() == ZERO, address);
                    break;
                }

                /*
                 JP nn
                 */
                case 0xC3: {
                    Short address = loadImmediateShort(registers.pc);
                    jp(address);
                    break;
                }

                /*
                 CALL NZ, nn
                 */
                case 0xC4:
                    call(registers.f.zf() == ZERO);
                    break;

                /*
                 PUSH BC
                 */
                case 0xC5:
                    pushShort(registers.sp, registers.bc);
                    break;

                /*
                 ADD A, n
                 */
                case 0xC6: {
                    Byte data = loadImmediateByte(registers.pc);
                    add(registers.a, data);
                    break;
                }

                // RST 08
                case 0xCF:
                    rst(0x08);
                    break;

                // RET Z
                case 0xC8: {
                    ret(registers.f.zf() == ONE, registers.sp);
                    break;
                }

                /*
                 RET
                 */
                case 0xC9:
                    ret(registers.sp);
                    break;

                /*
                 JP Z, nn
                 */
                case 0xCA: {
                    Short address = loadImmediateShort(registers.pc);
                    jp(registers.f.zf() == ONE, address);
                    break;
                }

                // Shift/bit test
                case 0xCB: {
                    Byte operand = loadImmediateByte(registers.pc);
                    int regNum = operand.intValue() & 0x07;
                    int data = registers.registerRead(regNum);
                    if ((operand.intValue() & 0xC0) == 0) {
                        switch ((operand.intValue() & 0xF8)) {

                            // RLC A
                            case 0x00:
                                registers.f.setValue(0);
                                if ((data & 0x80) == 0x80) {
                                    registers.f.cf(ONE);
                                }
                                data <<= 1;
                                if (registers.f.cf().intValue() == 1) {
                                    data |= 1;
                                }

                                data &= 0xFF;
                                if (data == 0) {
                                    registers.f.zf(ONE);
                                }
                                registers.registerWrite(regNum, data);
                                break;

                            // RRC A
                            case 0x08:
                                registers.f.setValue(0);
                                if ((data & 0x01) == 0x01) {
                                    registers.f.cf(ONE);
                                }
                                data >>= 1;
                                if (registers.f.cf().intValue() == 1) {
                                    data |= 0x80;
                                }
                                if (data == 0) {
                                    registers.f.zf(ONE);
                                }
                                registers.registerWrite(regNum, data);
                                break;

                            // RL r
                            case 0x10:
                                newf.setValue(0);
                                if ((data & 0x80) == 0x80) {
                                    newf.cf(ONE);
                                }
                                data <<= 1;

                                if (registers.f.cf().intValue() == 1) {
                                    data |= 1;
                                }

                                data &= 0xFF;
                                if (data == 0) {
                                    newf.zf(ONE);
                                }
                                registers.f.setValue(newf.intValue());
                                registers.registerWrite(regNum, data);
                                break;

                            // RR r
                            case 0x18:
                                newf.setValue(0);
                                if ((data & 0x01) == 0x01) {
                                    newf.cf(ONE);

                                }
                                data >>= 1;

                                if (registers.f.cf().intValue() == 1) {
                                    data |= 0x80;
                                }

                                if (data == 0) {
                                    newf.zf(ONE);
                                }
                                registers.f.setValue(newf.intValue());
                                registers.registerWrite(regNum, data);
                                break;

                            // SLA r
                            case 0x20:
                                registers.f.setValue(0);
                                if ((data & 0x80) == 0x80) {
                                    registers.f.cf(ONE);
                                }

                                data <<= 1;

                                data &= 0xFF;
                                if (data == 0) {
                                    registers.f.zf(ONE);
                                }
                                registers.registerWrite(regNum, data);
                                break;

                            // SRA r
                            case 0x28:
                                short topBit;

                                topBit = (short) (data & 0x80);
                                registers.f.setValue(0);
                                if ((data & 0x01) == 0x01) {
                                    registers.f.cf(ONE);
                                }

                                data >>= 1;
                                data |= topBit;

                                if (data == 0) {
                                    registers.f.zf(ONE);
                                }
                                registers.registerWrite(regNum, data);
                                break;

                            // SWAP r
                            case 0x30:

                                data = (short) (((data & 0x0F) << 4) | ((data & 0xF0) >> 4));
                                registers.f.setValue(0);
                                if (data == 0) {
                                    registers.f.zf(ONE);
                                }
                                registers.registerWrite(regNum, data);
                                break;

                            // SRL r
                            case 0x38:
                                registers.f.setValue(0);
                                if ((data & 0x01) == 0x01) {
                                    registers.f.cf(ONE);
                                }

                                data >>= 1;

                                if (data == 0) {
                                    registers.f.zf(ONE);
                                }
                                registers.registerWrite(regNum, data);
                                break;
                        }
                    } else {
                        int mask;
                        int bitNumber = (operand.intValue() & 0x38) >> 3;

                        // BIT n, r
                        if ((operand.intValue() & 0xC0) == 0x40) {
                            mask = (short) (0x01 << bitNumber);
                            registers.f.nf(ZERO);
                            registers.f.hf(ONE);
                            if ((data & mask) != 0) {
                                registers.f.zf(ZERO);
                            } else {
                                registers.f.zf(ONE);
                            }
                        }

                        // RES n, r
                        if ((operand.intValue() & 0xC0) == 0x80) {
                            mask = (short) (0xFF - (0x01 << bitNumber));
                            data = (short) (data & mask);
                            registers.registerWrite(regNum, data);
                        }

                        // SET n, r
                        if ((operand.intValue() & 0xC0) == 0xC0) {
                            mask = (short) (0x01 << bitNumber);
                            data = (short) (data | mask);
                            registers.registerWrite(regNum, data);
                        }
                    }

                    break;
                }

                // CALL Z, nn
                case 0xCC:
                    call(registers.f.zf() == ONE);
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
                    Byte data = loadImmediateByte(registers.pc);
                    adc(registers.a, data, registers.f.cf());
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
                    ret(registers.f.cf() == ZERO, registers.sp);
                    break;

                /*
                 POP DE
                  */
                case 0xD1: {
                    Short data = popShort(registers.sp);
                    registers.de.setValue(data.intValue());
                    break;
                }

                /*
                 JP NC, nn
                 */
                case 0xD2: {
                    Short address = loadImmediateShort(registers.pc);
                    jp(registers.f.cf() == ZERO, address);
                    break;
                }

                /*
                 CALL NC, nn
                 */
                case 0xD4:
                    call(registers.f.cf() == ZERO);
                    break;

                /*
                 PUSH DE
                 */
                case 0xD5:
                    pushShort(registers.sp, registers.de);
                    break;

                /*
                 SUB A, n
                 */
                case 0xD6: {
                    Byte data = loadImmediateByte(registers.pc);
                    sub(registers.a, data);
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
                    ret(registers.f.cf() == ONE, registers.sp);
                    break;

                /*
                 RETI
                  */
                case 0xD9:
                    interruptsEnabled = true;
                    ret(registers.sp);
                    break;

                /*
                 JP C, nn
                 */
                case 0xDA: {
                    Short address = loadImmediateShort(registers.pc);
                    jp(registers.f.cf() == ONE, address);
                    break;
                }

                /*
                 CALL C, nn
                 */
                case 0xDC:
                    call(registers.f.cf() == ONE);
                    break;

                // SBC A, n
                case 0xDE: {
                    Byte data = loadImmediateByte(registers.pc);
                    sbc(registers.a, data, registers.f.cf());
                    break;
                }

                // RST 18
                case 0xDF:
                    rst(0x18);
                    break;

                // LDH (n), A
                case 0xE0: {
                    Byte data = loadImmediateByte(registers.pc);
                    write(new Short(0xFF00 | data.intValue()), registers.a);
                    break;
                }

                // POP HL
                case 0xE1: {
                    Short data = popShort(registers.sp);
                    load(registers.hl, data);
                    break;
                }

                // LDH (FF00 + C), A
                case 0xE2: {
                    Short address = new Short(new Byte(0xFF), registers.c);
                    write(address, registers.a);
                    break;
                }

                // PUSH HL
                case 0xE5:
                    pushShort(registers.sp, registers.hl);
                    break;

                // AND n
                case 0xE6: {
                    Byte data = loadImmediateByte(registers.pc);
                    and(registers.a, data);
                    break;
                }

                // RST 20
                case 0xE7:
                    rst(0x20);
                    break;

                // ADD SP, nn
                case 0xE8: {
                    Short data = loadImmediateShort(registers.pc);
                    add(registers.sp, data);
                    break;
                }

                /*
                 JP HL
                 */
                case 0xE9: {
                    jp(registers.hl);
                    break;
                }

                /*
                 LD (nn), A
                 */
                case 0xEA: {
                    Short address = loadImmediateShort(registers.pc);
                    write(address, registers.a);
                    break;
                }

                // XOR A, n
                case 0xEE: {
                    Byte data = loadImmediateByte(registers.pc);
                    xor(registers.a, data);
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
                    Byte addressOffset = loadImmediateByte(registers.pc);
                    Byte ff = new Byte(0xFF);

                    Short address = new Short(ff, addressOffset);
                    load(registers.a, read(address));
                    break;
                }

                /*
                 POP AF
                 */
                case 0xF1:
                    load(registers.af, popShort(registers.sp));
                    break;

                // LD A, (FF00 + C)
                case 0xF2: {
                    Short address = new Short(new Byte(0xFF), registers.c);
                    Byte data = read(address);
                    load(registers.a, data);
                    break;
                }

                // DI
                case 0xF3:
                    interruptsEnabled = false;
                    break;

                // PUSH AF
                case 0xF5:
                    pushShort(registers.sp, registers.af);
                    break;

                // OR A, n
                case 0xF6: {
                    Byte data = loadImmediateByte(registers.pc);
                    or(registers.a, data);
                    break;
                }

                // RST 30
                case 0xF7:
                    rst(0x30);
                    break;

                // LD HL, SP + n  ** HALFCARRY FLAG NOT SET ***
                case 0xF8: {
                    Byte offset = loadImmediateByte(registers.pc);
                    int result = registers.sp.intValue() + offset.intValue();
                    add(registers.hl, new Short(result));
                    break;
                }

                // LD SP, HL
                case 0xF9:
                    registers.sp.setValue(registers.hl.intValue());
                    break;

                /*
                 LD A, (nn)
                 */
                case 0xFA: {
                    Short address = loadImmediateShort(registers.pc);
                    Byte data = read(address);
                    load(registers.a, data);
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
                    Byte data = loadImmediateByte(registers.pc);
                    cp(registers.a, data);
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
                        int operand = registers.registerRead(opcode.intValue() & 0x07);
                        switch ((opcode.intValue() & 0x38) >> 3) {

                            // ADC A, r
                            case 1:
                                adc(registers.a, new Byte(operand), registers.f.cf());
                                break;

                            // ADD A, r
                            case 0: {
                                add(registers.a, new Byte(operand));
                                break;
                            }

                            // SBC A, r
                            case 3:
                                sbc(registers.a, new Byte(operand), registers.f.cf());
                                break;

                            // SUB A, r
                            case 2: {
                                sub(registers.a, new Byte(operand));
                                break;
                            }

                            // AND A, r
                            case 4:
                                and(registers.a, new Byte(operand));
                                break;

                            // XOR A, r
                            case 5:
                                xor(registers.a, new Byte(operand));
                                break;

                            // OR A, r
                            case 6:
                                or(registers.a, new Byte(operand));
                                break;

                            // CP A, r (compare)
                            case 7:
                                cp(registers.a, new Byte(operand));
                                break;
                        }

                    } else if ((opcode.intValue() & 0xC0) == 0x40) {   // Byte 0x01xxxxxxx indicates 8-bit ld
                        registers.registerWrite((opcode.intValue() & 0x38) >> 3, registers.registerRead(opcode.intValue() & 0x07));
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
        Short address = loadImmediateShort(registers.pc);
        if (condition) {
            call(address);
        }
    }

    private void call(Short address) {
        pushShort(registers.sp, registers.pc);
        jp(address);
    }

    private void jp(boolean condition, Short address) {
        if (condition) {
            jp(address);
        }
    }

    private void jp(Short address) {
        registers.pc.setValue(address.intValue());
    }

    private void ret(Short stackAddress) {
        Short address = popShort(stackAddress);
        registers.pc.setValue(address.intValue());
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

    private void jr(boolean condition, Byte address) {
        if (condition) {
            add(registers.pc, address);
        }
    }

    private void add(Short pc, Byte address) {
        Short addressShort = Short.signedShortFromByte(address);
        add(pc, addressShort);
    }

    private void rst(int address) {
        registers.sp.dec();
        write(registers.sp, registers.pc.getUpperByte());
        registers.sp.dec();
        write(registers.sp, registers.pc.getLowerByte());
        load(registers.pc, new Short(address));
    }


    private Byte loadImmediateByte(Short address) {
        Byte immediate = read(address);
        address.inc();
        return immediate;
    }

    private Short loadImmediateShort(Short address) {
        Byte lowerByte = loadImmediateByte(address);
        Byte upperByte = loadImmediateByte(address);

        return new Short(upperByte, lowerByte);
    }

    private void adc(Byte left, Byte right, Bit carry) {

        registers.f.setValue(0);

        int lowerResult = left.lowerNibble() + right.lowerNibble() + carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            registers.f.hf(ONE);
        }

        int result = left.intValue() + right.intValue() + carry.intValue();

        if ((result & 0x100) == 0x100) {
            registers.f.cf(ONE);
        }

        if ((result & 0xFF) == 0) {
            registers.f.zf(ONE);
        }

        left.setValue(result);
    }

    private void add(Byte left, Byte right) {
        adc(left, right, ZERO);
    }

    private void inc(Byte left) {
        add(left, new Byte(1));
    }

    private void sbc(Byte left, Byte right, Bit carry) {
        registers.f.nf(ONE);

        int lowerResult = left.lowerNibble() - right.lowerNibble() - carry.intValue();

        if ((lowerResult & 0x10) == 0x10) {
            registers.f.hf(ONE);
        } else {
            registers.f.hf(ZERO);
        }

        int result = left.intValue() - right.intValue() - carry.intValue();

        if ((result & 0x100) == 0x100) {
            registers.f.cf(ONE);
        } else {
            registers.f.cf(ZERO);
        }

        if ((result & 0xFF) == 0) {
            registers.f.zf(ONE);
        } else {
            registers.f.zf(ZERO);
        }

        left.setValue(result);
    }

    private void sub(Byte left, Byte right) {
        sbc(left, right, ZERO);
    }

    private void cp(Byte left, Byte right) {
        int originalValue = left.intValue();
        sub(left, right);
        left.setValue(originalValue);
    }

    private void dec(Byte left) {
        sub(left, new Byte(1));
    }

    private void or(Byte left, Byte right) {
        int result = left.intValue() | right.intValue();

        registers.f.setValue(0);

        if (result == 0) {
            registers.f.zf(ONE);
        }

        left.setValue(result);
    }

    private void xor(Byte left, Byte right) {
        int result = left.intValue() ^ right.intValue();

        registers.f.setValue(0);

        if (result == 0) {
            registers.f.zf(ONE);
        }

        left.setValue(result);
    }

    private void and(Byte left, Byte right) {
        int result = left.intValue() & right.intValue();

        registers.f.nf(ZERO);
        registers.f.hf(ONE);
        registers.f.cf(ZERO);

        if (result == 0) {
            registers.f.zf(ONE);
        } else {
            registers.f.zf(ZERO);
        }

        left.setValue(result);
    }

    /**
     * RL
     * <p>
     * ╔═════════════════════════════════╗
     * ║  ┌────┐    ┌───┬────────┬───┐   ║
     * ╚══│ CF │<═══│ 7 │ <═════ │ 0 │<══╝
     * └────┘    └───┴────────┴───┘
     */
    private void rl(Byte operand, Bit carry) {
        int result = ((operand.intValue() << 1) | carry.intValue()) & 0xFF;

        registers.f.nf(ZERO);
        registers.f.hf(ZERO);
        registers.f.cf(operand.getBit(7));
        registers.f.setZeroFlagForResult(result);

        operand.setValue(result);
    }

    private void rla(Byte operand) {
        rl(operand, operand.getBit(7));
        registers.f.zf(ZERO);
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
        registers.f.zf(ZERO);
    }

    /**
     * RR
     * <p>
     * ╔════════════════════════════════╗
     * ║  ┌────┐    ┌───┬────────┬───┐  ║
     * ╚═>│ CF │═══>│ 7 │ ═════> │ 0 │══╝
     *    └────┘    └───┴────────┴───┘
     */
    private void rr(Byte operand, Bit carry) {
        int result = (carry.intValue() << 7) | (operand.intValue() >> 1);

        registers.f.nf(ZERO);
        registers.f.hf(ZERO);
        registers.f.cf(operand.getBit(0));
        registers.f.setZeroFlagForResult(result);

        operand.setValue(result);
    }

    private void rra(Byte operand, Bit carry) {
        rr(operand, carry);
        registers.f.zf(ZERO);
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
        registers.f.zf(ZERO);
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

        registers.f.nf(ZERO);
        registers.f.hf(ZERO);
        registers.f.cf(operand.getBit(7));
        registers.f.setZeroFlagForResult(result);

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
    private void sra(Byte operand, Bit sign) {
        int result = (sign.intValue() << 7) | (operand.intValue() >> 1);

        registers.f.nf(ZERO);
        registers.f.hf(ZERO);
        registers.f.cf(operand.getBit(0));
        registers.f.setZeroFlagForResult(result);

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

    private void add(Short left, Short right) {

        registers.f.nf(ZERO);

        int lowerResult = (left.intValue() & 0x0FFF) + (right.intValue() & 0x0FFF);

        if ((lowerResult & 0x1000) == 0x1000) {
            registers.f.hf(ONE);
        } else {
            registers.f.hf(ZERO);
        }

        int result = left.intValue() + right.intValue();

        if ((result & 0x10000) == 0x10000) {
            registers.f.cf(ONE);
        } else {
            registers.f.cf(ZERO);
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
