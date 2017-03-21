package javaboy;

import javaboy.graphics.GraphicsChip;
import javaboy.instruction.BaseOpcode;
import javaboy.instruction.ExtendedOpcode;
import javaboy.instruction.Instruction;
import javaboy.instruction.Opcode;
import javaboy.lang.Bit;
import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;
import javaboy.memory.MemoryController;
import org.pmw.tinylog.Logger;

import java.awt.*;

import static javaboy.lang.Bit.ONE;
import static javaboy.lang.Bit.ZERO;

public class Cpu implements ReadableWritable {

    private final Registers registers;
    private final InstructionCounter instructionCounter = new InstructionCounter();
    private final MemoryController memoryController;
    private final InterruptController interruptController;
    private boolean prefixCB;

    /**
     * Used to implement the IE delay slot
     */
    private int ieDelay = -1;

    boolean timaEnabled = false;
    int instructionsPerTima = 6000;

    final GraphicsChip graphicsChip;
    public final IoHandler ioHandler;
    private final Component applet;

    Cpu(Component a) {
        interruptController = new InterruptController();
        registers = new Registers(this);
        graphicsChip = new GraphicsChip(a, this);
        ioHandler = new IoHandler(this, instructionCounter);
        memoryController = new MemoryController(graphicsChip, ioHandler, registers);
        applet = a;
    }

    @Override
    public Byte read(Short address) {
        return memoryController.read(address);
    }

    private void write(Short address, Short data) {
        write(address, data.getLowerByte());
        write(new Short(address.intValue() + 1), data.getUpperByte());
    }

    @Override
    public void write(Short address, Byte data) {
        memoryController.write(address, data);
    }

    /**
     * Resets the CPU to it's power on state.  Memory contents are not cleared.
     */
    void reset() {
        graphicsChip.dispose();
        ieDelay = -1;
        prefixCB = false;

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

    public static final Short INTERRUPT_FLAGS_ADDRESS = new Short(0xFF0F);
    public static final Short INTERRUPT_ENABLE_ADDRESS = new Short(0xFFFF);

    private Byte interruptFlags() {
        return ioHandler.read(INTERRUPT_FLAGS_ADDRESS);
    }

    private Byte interruptEnable() {
        return ioHandler.read(INTERRUPT_ENABLE_ADDRESS);
    }

    private boolean didInterruptOccur() {
        return (interruptFlags().intValue() & interruptEnable().intValue()) != 0;
    }

    private boolean didInterruptOccur(InterruptController.Interrupt interrupt) {
        return (interruptFlags().intValue() & interruptEnable().intValue() & interrupt.getBitMask()) != 0;
    }

    private void checkInterrupts() {
        InterruptController.Interrupt interrupt = checkInterrupt();

        if (interrupt == null) return;

        pushShort(registers.sp, registers.pc);
        interruptController.setMasterInterruptEnable(false);
        attendInterrupt(interrupt, interrupt.getAddress());
    }

    private InterruptController.Interrupt checkInterrupt() {
        if (didInterruptOccur(InterruptController.Interrupt.VBLANK)) {
            return InterruptController.Interrupt.VBLANK;
        }
        if (didInterruptOccur(InterruptController.Interrupt.LCDC)) {
            return InterruptController.Interrupt.LCDC;
        }
        if (didInterruptOccur(InterruptController.Interrupt.TIMA)) {
            return InterruptController.Interrupt.TIMA;
        }
        if (didInterruptOccur(InterruptController.Interrupt.SERIAL)) {
            return InterruptController.Interrupt.SERIAL;
        }
        if (didInterruptOccur(InterruptController.Interrupt.JOYPAD)) {
            return InterruptController.Interrupt.JOYPAD;
        }

        return null;
    }

    private void attendInterrupt(InterruptController.Interrupt interrupt, int address) {
        registers.pc.setValue(address);
        interruptFlags().setValue(interruptFlags().intValue() - interrupt.getBitMask());
    }

    /**
     * Initiate an interrupt of the specified type
     */
    private void triggerInterrupt(int interrupt) {
        Byte data = ioHandler.read(INTERRUPT_FLAGS_ADDRESS);
        data.setValue(data.intValue() | interrupt);
    }

    /**
     * Check for interrupts that need to be initiated
     */
    private void initiateInterrupts() {
        if (timaEnabled && ((instructionCounter.getCount() % instructionsPerTima) == 0)) {
            if (ioHandler.read(new Short(0xFF05)).intValue() == 0) {
                ioHandler.write(new Short(0xFF05), ioHandler.read(new Short(0xFF06))); // Set TIMA modulo
                if ((interruptEnable().intValue() & InterruptController.Interrupt.TIMA.getBitMask()) != 0)
                    triggerInterrupt(InterruptController.Interrupt.TIMA.getBitMask());
            }
            ioHandler.read(new Short(0xFF05)).inc();
        }

        short INSTRUCTIONS_PER_DIV = GraphicsConstants.BASE_INSTRUCTIONS_PER_DIV;
        if ((instructionCounter.getCount() % INSTRUCTIONS_PER_DIV) == 0) {
            ioHandler.read(new Short(0xFF04)).inc();
        }

        if ((instructionCounter.getCount() % GraphicsConstants.INSTRS_PER_HBLANK) == 0) {

            // LCY Coincidence
            // The +1 is due to the LCY register being just about to be incremented
            int cline = ioHandler.read(new Short(0xFF44)).intValue() + 1;
            if (cline == 152) cline = 0;

            if (((interruptEnable().intValue() & InterruptController.Interrupt.LCDC.getBitMask()) != 0) &&
                    ((ioHandler.read(new Short(0xFF41)).intValue() & 64) != 0) &&
                    (ioHandler.read(new Short(0xFF45)).intValue() == cline) && ((ioHandler.read(new Short(0xFF40)).intValue() & 0x80) != 0) && (cline < 0x90)) {
                triggerInterrupt(InterruptController.Interrupt.LCDC.getBitMask());
            }

            // Trigger on every line
            if (((interruptEnable().intValue() & InterruptController.Interrupt.LCDC.getBitMask()) != 0) &&
                    ((ioHandler.read(new Short(0xFF41)).intValue() & 0x8) != 0) && ((ioHandler.read(new Short(0xFF40)).intValue() & 0x80) != 0) && (cline < 0x90)) {
                triggerInterrupt(InterruptController.Interrupt.LCDC.getBitMask());
            }

            if (ioHandler.read(new Short(0xFF44)).intValue() == 143) {
                for (int r = GraphicsChip.HEIGHT; r < 170; r++) {
                    graphicsChip.notifyScanline(r);
                }
                if (((ioHandler.read(new Short(0xFF40)).intValue() & 0x80) != 0) && ((interruptEnable().intValue() & InterruptController.Interrupt.VBLANK.getBitMask()) != 0)) {
                    triggerInterrupt(InterruptController.Interrupt.VBLANK.getBitMask());
                    if (((ioHandler.read(new Short(0xFF41)).intValue() & 16) != 0) && ((interruptEnable().intValue() & InterruptController.Interrupt.LCDC.getBitMask()) != 0)) {
                        triggerInterrupt(InterruptController.Interrupt.LCDC.getBitMask());
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

    private void executeBaseOpcode(BaseOpcode opcode) {
        final FlagRegister newf = new FlagRegister();

        switch (opcode) {

            case NOP:
                break;

            case LD_BC_nn: {
                Short data = loadImmediateShort(registers.pc);
                load(registers.bc, data);
                break;
            }

            case LD_iBCi_A:
                write(registers.bc, registers.a);
                break;

            case INC_BC:
                registers.bc.inc();
                break;

            case INC_B:
                inc(registers.b);
                break;

            case DEC_B:
                dec(registers.b);
                break;

            case LD_B_n: {
                Byte data = loadImmediateByte(registers.pc);
                load(registers.b, data);
                break;
            }

            case RLCA:
                rlca(registers.a);
                break;

            case LD_inni_SP: {
                Short address = loadImmediateShort(registers.pc);
                write(address, registers.sp);
                break;
            }

            case ADD_HL_BC: {
                add(registers.hl, registers.bc);
                break;
            }

            case LD_A_iBCi: {
                Byte data = read(registers.bc);
                registers.a.setValue(data);
                break;
            }

            case DEC_BC:
                registers.bc.dec();
                break;

            case INC_C:
                inc(registers.c);
                break;

            case DEC_C:
                dec(registers.c);
                break;

            case LD_C_n: {
                Byte data = loadImmediateByte(registers.pc);
                load(registers.c, data);
                break;
            }

            case RRCA:
                rrca(registers.a);
                break;

            case STOP:
                registers.pc.inc();
                break;

            case LD_DE_nn: {
                Short data = loadImmediateShort(registers.pc);
                registers.de.setValue(data.intValue());
                break;
            }

            case LD_iDEi_A:
                write(registers.de, registers.a);
                break;

            case INC_DE:
                registers.de.inc();
                break;

            case INC_D:
                inc(registers.d);
                break;

            case DEC_D:
                dec(registers.d);
                break;

            case LD_D_n: {
                Byte data = loadImmediateByte(registers.pc);
                load(registers.d, data);
                break;
            }

            case RLA:
                rl(registers.a, registers.f.cf());
                break;

            case JR_n: {
                Byte offset = loadImmediateByte(registers.pc);
                jr(true, offset);
                break;
            }

            case ADD_HL_DE: {
                add(registers.hl, registers.de);
                break;
            }

            case LD_A_iDEi: {
                Byte data = read(registers.de);
                load(registers.a, data);
                break;
            }

            case DEC_DE:
                registers.de.inc();
                break;

            case INC_E:
                inc(registers.e);
                break;

            case DEC_E:
                dec(registers.e);
                break;

            case LD_E_n: {
                Byte data = loadImmediateByte(registers.pc);
                load(registers.e, data);
                break;
            }

            case RRA: {
                rra(registers.a, registers.f.cf());
                break;
            }

            case JR_NZ_n: {
                Byte address = loadImmediateByte(registers.pc);
                jr(registers.f.zf() == ZERO, address);
            }
            break;

            case LD_HL_nn: {
                Short address = loadImmediateShort(registers.pc);
                load(registers.hl, address);
                break;
            }

            case LDI_iHLi_A:
                write(registers.hl, registers.a);
                registers.hl.inc();
                break;

            case INC_HL:
                registers.hl.inc();
                break;

            case INC_H:
                inc(registers.h);
                break;

            case DEC_H:
                dec(registers.h);
                break;

            case LD_H_n: {
                Byte data = loadImmediateByte(registers.pc);
                load(registers.h, data);
                break;
            }

            case DAA:
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

            case JR_Z_n: {
                Byte address = loadImmediateByte(registers.pc);
                jr(registers.f.zf() == ONE, address);
                break;
            }

            case ADD_HL_HL: {
                add(registers.hl, registers.hl);
                break;
            }

            case LDI_A_iHLi: {
                Byte data = read(registers.hl);
                load(registers.a, data);
                registers.hl.inc();
                break;
            }

            case DEC_HL:
                registers.hl.dec();
                break;

            case INC_L:
                inc(registers.l);
                break;

            case DEC_L:
                dec(registers.l);
                break;

            case LD_L_n: {
                Byte address = loadImmediateByte(registers.pc);
                load(registers.l, address);
                break;
            }

            case CPL:
                registers.a.neg();
                registers.f.nf(ONE);
                registers.f.hf(ONE);
                break;

            case JR_NC_n: {
                Byte address = loadImmediateByte(registers.pc);
                jr(registers.f.cf() == ZERO, address);
                break;
            }

            case LD_SP_nn: {
                Short address = loadImmediateShort(registers.pc);
                load(registers.sp, address);
                break;
            }

            case LDD_iHLi_A:
                write(registers.hl, registers.a);
                registers.hl.dec();
                break;

            case INC_SP:
                registers.sp.inc();
                break;

            case INC_iHLi: {
                Byte data = read(registers.hl);
                inc(data);
                write(registers.hl, data);
                break;
            }

            case DEC_iHLi:
                registers.hl.dec();
                break;

            case LD_iHLi_n: {
                Byte address = loadImmediateByte(registers.pc);
                write(registers.hl, address);
                break;
            }

            case SCF:
                registers.f.nf(ZERO);
                registers.f.hf(ZERO);
                registers.f.cf(ONE);
                break;

            case JR_C_n: {
                Byte address = loadImmediateByte(registers.pc);
                jr(registers.f.cf() == ONE, address);
                break;
            }

            case ADD_HL_SP: {
                add(registers.hl, registers.sp);
                break;
            }

            case LDD_A_iHLi: {
                Byte data = read(registers.hl);
                load(registers.a, data);
                registers.hl.dec();
                break;
            }

            case DEC_SP:
                registers.sp.dec();
                break;

            case INC_A:
                inc(registers.a);
                break;

            case DEC_A:
                dec(registers.a);
                break;

            case LD_A_n: {
                Byte data = loadImmediateByte(registers.pc);
                load(registers.a, data);
                break;
            }

            case CCF:
                registers.f.nf(ZERO);
                registers.f.hf(ZERO);
                registers.f.cf(registers.f.cf().toggle());
                break;

            case LD_B_B:
                break;

                /*
                 HALT
                 */
            case HALT:
                interruptController.setMasterInterruptEnable(true);
                while (interruptFlags().intValue() == 0) {
                    initiateInterrupts();
                    instructionCounter.inc();
                }
                break;

            case XOR_A:
                xor(registers.a, registers.a);
                break;

            case RET_NZ:
                ret(registers.f.zf() == ZERO, registers.sp);
                break;

            case POP_BC: {
                load(registers.bc, popShort(registers.sp));
                break;
            }

            case JP_NZ_nn: {
                Short address = loadImmediateShort(registers.pc);
                jp(registers.f.zf() == ZERO, address);
                break;
            }

            case JP_nn: {
                Short address = loadImmediateShort(registers.pc);
                jp(address);
                break;
            }

            case CALL_NZ_nn:
                call(registers.f.zf() == ZERO);
                break;

            case PUSH_BC:
                pushShort(registers.sp, registers.bc);
                break;

            case ADD_n: {
                Byte data = loadImmediateByte(registers.pc);
                add(registers.a, data);
                break;
            }

            case RST_08:
                rst(0x08);
                break;

            case RET_Z: {
                ret(registers.f.zf() == ONE, registers.sp);
                break;
            }

            case RET:
                ret(registers.sp);
                break;

            case JP_Z_nn: {
                Short address = loadImmediateShort(registers.pc);
                jp(registers.f.zf() == ONE, address);
                break;
            }

            // Shift/bit test
            case PREFIX_CB:
                prefixCB = true;
                break;

            case CALL_Z_nn:
                call(registers.f.zf() == ONE);
                break;

            case CALL_nn: {
                call();
                break;
            }

            case ADC_n: {
                Byte data = loadImmediateByte(registers.pc);
                adc(registers.a, data, registers.f.cf());
                break;
            }

            case RST_00:
                rst(0x00);
                break;

            case RET_NC:
                ret(registers.f.cf() == ZERO, registers.sp);
                break;

            case POP_DE: {
                Short data = popShort(registers.sp);
                registers.de.setValue(data.intValue());
                break;
            }

            case JP_NC_nn: {
                Short address = loadImmediateShort(registers.pc);
                jp(registers.f.cf() == ZERO, address);
                break;
            }

            case CALL_NC_nn:
                call(registers.f.cf() == ZERO);
                break;

            case PUSH_DE:
                pushShort(registers.sp, registers.de);
                break;

            case SUB_n: {
                Byte data = loadImmediateByte(registers.pc);
                sub(registers.a, data);
                break;
            }

            case RST_10:
                rst(0x10);
                break;

            case RET_C:
                ret(registers.f.cf() == ONE, registers.sp);
                break;

            case RETI:
                interruptController.setMasterInterruptEnable(true);
                ret(registers.sp);
                break;

            case JP_C_nn: {
                Short address = loadImmediateShort(registers.pc);
                jp(registers.f.cf() == ONE, address);
                break;
            }

            case CALL_C_nn:
                call(registers.f.cf() == ONE);
                break;

            case SBC_n: {
                Byte data = loadImmediateByte(registers.pc);
                sbc(registers.a, data, registers.f.cf());
                break;
            }

            case RST_18:
                rst(0x18);
                break;

            case LDH_ini_A: {
                Byte data = loadImmediateByte(registers.pc);
                write(new Short(0xFF00 | data.intValue()), registers.a);
                break;
            }

            case POP_HL: {
                Short data = popShort(registers.sp);
                load(registers.hl, data);
                break;
            }

            // LDH (FF00 + C), A
            case LDH_iCi_A: {
                Short address = new Short(new Byte(0xFF), registers.c);
                write(address, registers.a);
                break;
            }

            case PUSH_HL:
                pushShort(registers.sp, registers.hl);
                break;

            case AND_n: {
                Byte data = loadImmediateByte(registers.pc);
                and(registers.a, data);
                break;
            }

            case RST_20:
                rst(0x20);
                break;

            case ADD_SP_nn: {
                Short data = loadImmediateShort(registers.pc);
                add(registers.sp, data);
                break;
            }

            case JP_HL: {
                jp(registers.hl);
                break;
            }

            case LD_inni_A: {
                Short address = loadImmediateShort(registers.pc);
                write(address, registers.a);
                break;
            }

            case XOR_n: {
                Byte data = loadImmediateByte(registers.pc);
                xor(registers.a, data);
                break;
            }

            case RST_28:
                rst(0x28);
                break;

            case LDH_A_ini: {
                Byte addressOffset = loadImmediateByte(registers.pc);
                Byte ff = new Byte(0xFF);

                Short address = new Short(ff, addressOffset);
                load(registers.a, read(address));
                break;
            }

            case POP_AF:
                load(registers.af, popShort(registers.sp));
                break;

            // LD A, (FF00 + C)
            case LDH_A_iCi: {
                Short address = new Short(new Byte(0xFF), registers.c);
                Byte data = read(address);
                load(registers.a, data);
                break;
            }

            case DI:
                interruptController.setMasterInterruptEnable(false);
                break;

            case PUSH_AF:
                pushShort(registers.sp, registers.af);
                break;

            case OR_n: {
                Byte data = loadImmediateByte(registers.pc);
                or(registers.a, data);
                break;
            }

            case RST_30:
                rst(0x30);
                break;

            // LD HL, SP + n  ** HALFCARRY FLAG NOT SET ***
            case LDHL_SP_n: {
                Byte offset = loadImmediateByte(registers.pc);
                int result = registers.sp.intValue() + offset.intValue();
                add(registers.hl, new Short(result));
                break;
            }

            case LD_SP_HL:
                registers.sp.setValue(registers.hl.intValue());
                break;

            case LD_A_inni: {
                Short address = loadImmediateShort(registers.pc);
                Byte data = read(address);
                load(registers.a, data);
                break;
            }

            case EI:
                ieDelay = 1;
                break;

            case CP_n: {
                Byte data = loadImmediateByte(registers.pc);
                cp(registers.a, data);
                break;
            }

            case RST_38:
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
                    throw new IllegalArgumentException("Unrecognized base opcode [" + String.format("%02X", opcode.intValue()) + "][" + opcode.name() + "]");
                }
        }

    }

    private void executeExtendedOpcode(ExtendedOpcode opcode) {
        final FlagRegister newf = new FlagRegister();

        int regNum = opcode.intValue() & 0x07;
        int data = registers.registerRead(regNum);

        if ((opcode.intValue() & 0xC0) == 0) {
            switch ((opcode.intValue() & 0xF8)) {

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
            int bitNumber = (opcode.intValue() & 0x38) >> 3;

            // BIT n, r
            if ((opcode.intValue() & 0xC0) == 0x40) {
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
            if ((opcode.intValue() & 0xC0) == 0x80) {
                mask = (short) (0xFF - (0x01 << bitNumber));
                data = (short) (data & mask);
                registers.registerWrite(regNum, data);
            }

            // SET n, r
            if ((opcode.intValue() & 0xC0) == 0xC0) {
                mask = (short) (0x01 << bitNumber);
                data = (short) (data | mask);
                registers.registerWrite(regNum, data);
            }
        }
    }

    final void execute() {
        graphicsChip.startTime = System.currentTimeMillis();

        while (true) {
            instructionCounter.inc();

            Opcode opcode = Instruction.from(loadImmediateByte(registers.pc), prefixCB);

            if (prefixCB) {
                executeExtendedOpcode((ExtendedOpcode) opcode);
                prefixCB = false;
            } else {
                executeBaseOpcode((BaseOpcode) opcode);
            }

            if (ieDelay != -1) {

                if (ieDelay > 0) {
                    ieDelay--;
                } else {
                    interruptController.setMasterInterruptEnable(true);
                    ieDelay = -1;
                }

            }

            if (interruptController.isMasterInterruptEnable()) {
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
     * ╔══════════════════════╗
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
     * └────┘    └───┴────────┴───┘
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
     * └────┘    └───┴────────┴───┘
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
     * └───┴────────┴───┘    └────┘
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
     * ┌───┬────────┬───┐    ┌────┐
     * 0 ══>│ 7 │ ═════> │ 0 │═══>│ CF │
     * └───┴────────┴───┘    └────┘
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
