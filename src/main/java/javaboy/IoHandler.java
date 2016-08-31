package javaboy;

import org.pmw.tinylog.Logger;
import javaboy.lang.Short;

/**
 * This class handles all the memory mapped IO in the range
 * FF00 - FF4F.  It also handles high memory accessed by the
 * LDH instruction which is located at 0xFF50 - 0xFFFF.
 */

class IoHandler {

    /**
     * Data contained in the handled memory area
     */
    byte[] registers = new byte[0x100];

    /**
     * Reference to the current CPU object
     */
    private Dmgcpu dmgcpu;

    /**
     * Create an IoHandler for the specified CPU
     */
    IoHandler(Dmgcpu d) {
        dmgcpu = d;
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
        switch (num) {
            case 0x41:         // LCDSTAT

                int output = 0;

                if (registers[0x44] == registers[0x45]) {
                    output |= 4;
                }

                int cyclePos = dmgcpu.instrCount % dmgcpu.INSTRS_PER_HBLANK;
                int sectionLength = dmgcpu.INSTRS_PER_HBLANK / 6;

                if (JavaBoy.unsign(registers[0x44]) > 144) {
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
                return (byte) (output | (registers[0x41] & 0xF8));

            default:
                return registers[num];
        }
    }

    /**
     * Write data to IO Ram
     */
    void ioWrite(int num, short data) {

        switch (num) {

            // DIV
            case 0x04:
                registers[0x04] = 0;
                break;

            // TAC
            case 0x07:
                dmgcpu.timaEnabled = (data & 0x04) != 0;

                int instrsPerSecond = dmgcpu.INSTRS_PER_VBLANK * 60;
                int clockFrequency = (data & 0x03);

                switch (clockFrequency) {
                    case 0:
                        dmgcpu.instrsPerTima = (instrsPerSecond / 4096);
                        break;
                    case 1:
                        dmgcpu.instrsPerTima = (instrsPerSecond / 262144);
                        break;
                    case 2:
                        dmgcpu.instrsPerTima = (instrsPerSecond / 65536);
                        break;
                    case 3:
                        dmgcpu.instrsPerTima = (instrsPerSecond / 16384);
                        break;
                }
                break;


            case 0x40:
                // LCDC
                dmgcpu.graphicsChip.bgEnabled = true;

                // BIT 5
                dmgcpu.graphicsChip.winEnabled = (data & 0x20) == 0x20;

                // BIT 4
                dmgcpu.graphicsChip.bgWindowDataSelect = (data & 0x10) == 0x10;

                dmgcpu.graphicsChip.hiBgTileMapAddress = (data & 0x08) == 0x08;

                // BIT 2
                dmgcpu.graphicsChip.doubledSprites = (data & 0x04) == 0x04;

                // BIT 1
                dmgcpu.graphicsChip.spritesEnabled = (data & 0x02) == 0x02;

                if ((data & 0x01) == 0x00) {     // BIT 0
                    dmgcpu.graphicsChip.bgEnabled = false;
                    dmgcpu.graphicsChip.winEnabled = false;
                }

                registers[0x40] = (byte) data;
                break;

            // DMA
            case 0x46:
                int sourceAddress = (data << 8);

                // This could be sped up using System.arrayCopy, but hey.
                for (int i = 0x00; i < 0xA0; i++) {
                    dmgcpu.write(new Short(0xFE00 + i), dmgcpu.read(new Short(sourceAddress + i)));
                }
                // This is meant to be run at the same time as the CPU is executing
                // instructions, but I don't think it's crucial.
                break;

            case 0x47:           // FF47 - BKG and WIN palette
                //    Logger.debug("Palette created!");
                dmgcpu.graphicsChip.backgroundPalette.decodePalette(data);
                if (registers[num] != (byte) data) {
                    registers[num] = (byte) data;
                    dmgcpu.graphicsChip.invalidateAll(GraphicsChip.TILE_BKG);
                }
                break;
            case 0x48:           // FF48 - OBJ1 palette
                dmgcpu.graphicsChip.obj1Palette.decodePalette(data);
                if (registers[num] != (byte) data) {
                    registers[num] = (byte) data;
                    dmgcpu.graphicsChip.invalidateAll(GraphicsChip.TILE_OBJ_1);
                }
                break;
            case 0x49:           // FF49 - OBJ2 palette
                dmgcpu.graphicsChip.obj2Palette.decodePalette(data);
                if (registers[num] != (byte) data) {
                    registers[num] = (byte) data;
                    dmgcpu.graphicsChip.invalidateAll(GraphicsChip.TILE_OBJ_2);
                }
                break;

            case 0x55:
                if (((registers[0x55] & 0x80) == 0) && ((data & 0x80) == 0)) {
                    int dmaSrc = (JavaBoy.unsign(registers[0x51]) << 8) +
                            (JavaBoy.unsign(registers[0x52]) & 0xF0);
                    int dmaDst = ((JavaBoy.unsign(registers[0x53]) & 0x1F) << 8) +
                            (JavaBoy.unsign(registers[0x54]) & 0xF0) + 0x8000;
                    int dmaLen = ((JavaBoy.unsign(data) & 0x7F) * 16) + 16;

                    if (dmaLen > 2048) dmaLen = 2048;

                    for (int r = 0; r < dmaLen; r++) {
                        dmgcpu.write(new Short(dmaDst + r), dmgcpu.read(new Short(dmaSrc + r)));
                    }
                }

                registers[0x55] = (byte) data;
                break;

            default:
                registers[num] = (byte) data;
                break;
        }
    }
}
