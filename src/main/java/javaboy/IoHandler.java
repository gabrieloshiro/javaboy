package javaboy;

import javaboy.graphics.GraphicsChip;
import javaboy.lang.Byte;
import javaboy.memory.Memory;
import org.pmw.tinylog.Logger;
import javaboy.lang.Short;

/**
 * This class handles all the memory mapped IO in the range
 * FF00 - FF4F.  It also handles high memory accessed by the
 * LDH instruction which is located at 0xFF50 - 0xFFFF.
 */

public class IoHandler implements ReadableWritable {

    /**
     * Data contained in the handled memory area
     */
//    public final byte[] registers = new byte[0x100];
    public final Memory io = new Memory(0xFF00, 0x100);

    /**
     * Reference to the current CPU object
     */
    private final Cpu cpu;
    
    private final InstructionCounter instructionCounter;

    /**
     * Create an IoHandler for the specified CPU
     */
    IoHandler(Cpu cpu, InstructionCounter instructionCounter) {
        this.cpu = cpu;
        this.instructionCounter = instructionCounter;
        reset();
    }

    /**
     * Initialize IO to initial power on state
     */
    void reset() {
        Logger.debug("Hardware reset");
        for (int r = 0; r < 0xFF; r++) {
            ioWrite(r, (short) 0x00);
        }
        ioWrite(0x40, (short) 0x91);
        ioWrite(0x0F, (short) 0x01);
    }

    /**
     * Read data from IO Ram
     */
    short ioRead(int num) {

        Short address = new Short(0xFF00 + num);

        switch (num) {
            case 0x41:         // LCDSTAT

                int output = 0;

                if (io.read(new Short(0xFF44)).equals(io.read(new Short(0xFF45)))) {
                    output |= 4;
                }

                int cyclePos = instructionCounter.getCount() % GraphicsConstants.INSTRS_PER_HBLANK;
                int sectionLength = GraphicsConstants.INSTRS_PER_HBLANK / 6;

                if (io.read(new Short(0xFF44)).intValue() > GraphicsChip.HEIGHT) {
                    output |= 1;
                } else {
                    if (cyclePos <= sectionLength * 3) {
                        // Mode 0
                    } else if (cyclePos <= sectionLength * 4) {
                        // Mode 2
                        output |= 2;
                    } else {
                        output |= 3;
                    }
                }
                return (byte) (output | (io.read(new Short(0xFF41)).intValue() & 0xF8));

            default:
                return (short) io.read(address).intValue();
        }
    }

    /**
     * Write data to IO Ram
     */
    void ioWrite(int num, short data) {

        Short address = new Short(0xFF00 + num);
        Byte dataByte = new Byte(data);

        switch (num) {

            // DIV
            case 0x04:
                io.write(new Short(0xFF04), new Byte(data));
//                registers[0x04] = 0;
                break;

            // TAC
            case 0x07:
                cpu.timaEnabled = (data & 0x04) != 0;

                int instrsPerSecond = GraphicsConstants.INSTRS_PER_VBLANK * 60;
                int clockFrequency = (data & 0x03);

                switch (clockFrequency) {
                    case 0:
                        cpu.instructionsPerTima = (instrsPerSecond / 4096);
                        break;
                    case 1:
                        cpu.instructionsPerTima = (instrsPerSecond / 262144);
                        break;
                    case 2:
                        cpu.instructionsPerTima = (instrsPerSecond / 65536);
                        break;
                    case 3:
                        cpu.instructionsPerTima = (instrsPerSecond / 16384);
                        break;
                }
                break;


            case 0x40:
                // LCDC
                cpu.graphicsChip.bgEnabled = true;

                // BIT 5
                cpu.graphicsChip.winEnabled = (data & 0x20) == 0x20;

                // BIT 4
                cpu.graphicsChip.bgWindowDataSelect = (data & 0x10) == 0x10;

                cpu.graphicsChip.hiBgTileMapAddress = (data & 0x08) == 0x08;

                // BIT 2
                cpu.graphicsChip.doubledSprites = (data & 0x04) == 0x04;

                // BIT 1
                cpu.graphicsChip.spritesEnabled = (data & 0x02) == 0x02;

                if ((data & 0x01) == 0x00) {     // BIT 0
                    cpu.graphicsChip.bgEnabled = false;
                    cpu.graphicsChip.winEnabled = false;
                }
                io.write(new Short(0xFF40), new Byte(data));
//                registers[0x40] = (byte) data;
                break;

            // DMA
            case 0x46:
                int sourceAddress = (data << 8);

                // This could be sped up using System.arrayCopy, but hey.
                for (int i = 0x00; i < 0xA0; i++) {
                    cpu.write(new Short(0xFE00 + i), cpu.read(new Short(sourceAddress + i)));
                }
                // This is meant to be run at the same time as the CPU is executing
                // instructions, but I don't think it's crucial.
                break;

            case 0x47:           // FF47 - BKG and WIN palette
                //    Logger.debug("Palette created!");
                cpu.graphicsChip.backgroundPalette.decodePalette(data);
                if (io.read(address).intValue() != (byte) data) {
                    io.write(address, dataByte);
                    cpu.graphicsChip.invalidateAll(GraphicsChip.TILE_BKG);
                }
                break;
            case 0x48:           // FF48 - OBJ1 palette
                cpu.graphicsChip.obj1Palette.decodePalette(data);
                if (io.read(address).intValue() != (byte) data) {
                    io.write(address, dataByte);
                    cpu.graphicsChip.invalidateAll(GraphicsChip.TILE_OBJ_1);
                }
                break;
            case 0x49:           // FF49 - OBJ2 palette
                cpu.graphicsChip.obj2Palette.decodePalette(data);
                if (io.read(address).intValue() != (byte) data) {
                    io.write(address, dataByte);
                    cpu.graphicsChip.invalidateAll(GraphicsChip.TILE_OBJ_2);
                }
                break;

            case 0x55:


                if (((io.read(new Short(0xFF55)).intValue() & 0x80) == 0) && ((data & 0x80) == 0)) {
                    int dmaSrc = ((io.read(new Short(0xFF51)).intValue()) << 8) +
                            (io.read(new Short(0xFF52)).intValue() & 0xF0);
                    int dmaDst = ((io.read(new Short(0xFF53)).intValue() & 0x1F) << 8) +
                            (io.read(new Short(0xFF54)).intValue() & 0xF0) + 0x8000;
                    int dmaLen = ((Shorts.unsigned(data) & 0x7F) * 16) + 16;

                    if (dmaLen > 2048) dmaLen = 2048;

                    for (int r = 0; r < dmaLen; r++) {
                        Short destination = new Short(dmaDst + r);
                        Short source = new Short(dmaSrc + r);
                        cpu.write(destination, cpu.read(source));
                    }
                }

//                registers[0x55] = (byte) data;
                io.write(new Short(0xFF55), dataByte);
                break;

            default:
                io.write(address, dataByte);
//                registers[num] = (byte) data;
                break;
        }
    }

    @Override
    public Byte read(Short address) {
        return io.read(address);
    }

    @Override
    public void write(Short address, Byte data) {
        io.write(address, data);
    }
}
