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

    boolean hdmaRunning;

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
        System.out.println("Hardware reset");
        for (int r = 0; r < 0xFF; r++) {
            ioWrite(r, (short) 0x00);
        }
        ioWrite(0x40, (short) 0x91);
        ioWrite(0x0F, (short) 0x01);
        hdmaRunning = false;
    }

    void performHdma() {
        int dmaSrc = (JavaBoy.unsign(registers[0x51]) << 8) +
                (JavaBoy.unsign(registers[0x52]) & 0xF0);
        int dmaDst = ((JavaBoy.unsign(registers[0x53]) & 0x1F) << 8) +
                (JavaBoy.unsign(registers[0x54]) & 0xF0) + 0x8000;

        for (int r = 0; r < 16; r++) {
            dmgcpu.addressWrite(dmaDst + r, dmgcpu.addressRead(dmaSrc + r));
        }

        dmaSrc += 16;
        dmaDst += 16;
        registers[0x51] = (byte) ((dmaSrc & 0xFF00) >> 8);
        registers[0x52] = (byte) (dmaSrc & 0x00F0);
        registers[0x53] = (byte) ((dmaDst & 0x1F00) >> 8);
        registers[0x54] = (byte) (dmaDst & 0x00F0);

        int len = JavaBoy.unsign(registers[0x55]);
        if (len == 0x00) {
            registers[0x55] = (byte) 0xFF;
            hdmaRunning = false;
        } else {
            len--;
            registers[0x55] = (byte) len;
        }

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

            case 0x55:
                return registers[0x55];

            case 0x69:       // GBC BG Sprite palette

                if (dmgcpu.gbcFeatures) {
                    int palNumber = (registers[0x68] & 0x38) >> 3;
                    return dmgcpu.graphicsChipOld.gbcBackground[palNumber].getGbcColours(
                            (JavaBoy.unsign(registers[0x68]) & 0x06) >> 1,
                            (JavaBoy.unsign(registers[0x68]) & 0x01) == 1);
                } else {
                    return registers[num];
                }


            case 0x6B:       // GBC OBJ Sprite palette

                if (dmgcpu.gbcFeatures) {
                    int palNumber = (registers[0x6A] & 0x38) >> 3;
                    return dmgcpu.graphicsChipOld.gbcSprite[palNumber].getGbcColours(
                            (JavaBoy.unsign(registers[0x6A]) & 0x06) >> 1,
                            (JavaBoy.unsign(registers[0x6A]) & 0x01) == 1);
                } else {
                    return registers[num];
                }


            default:
                return registers[num];
        }
    }

    /**
     * Write data to IO Ram
     */
    void ioWrite(int num, short data) {

        if (num <= 0x4B) {
            //  System.out.println("Write of register " + JavaBoy.hexByte(num) + " to " + JavaBoy.hexWord(data) + " at " + JavaBoy.hexWord(dmgcpu.pc));
        }

        switch (num) {
            case 0x00:           // FF00 - Joypad
                short output = 0x0F;
                if ((data & 0x10) == 0x00) {   // P14
//                    if (padRight) {
//                        output &= ~1;
//                    }
//                    if (padLeft) {
//                        output &= ~2;
//                    }
//                    if (padUp) {
//                        output &= ~4;
//                    }
//                    if (padDown) {
//                        output &= ~8;
//                    }
                }
                if ((data & 0x20) == 0x00) {   // P15
//                    if (padA) {
//                        output &= ~0x01;
//                    }
//                    if (padB) {
//                        output &= ~0x02;
//                    }
//                    if (padSelect) {
//                        output &= ~0x04;
//                    }
//                    if (padStart) {
//                        output &= ~0x08;
//                    }
                }
                output |= (data & 0xF0);
                registers[0x00] = (byte) (output);
                //    System.out.println("Joypad port = " + JavaBoy.hexByte(data) + " output = " + JavaBoy.hexByte(output) + "(PC=" + JavaBoy.hexWord(dmgcpu.pc) + ")");
                break;

            case 0x02:           // Serial

                registers[0x02] = (byte) data;


                if ((registers[0x02] & 0x01) == 1) {
                    registers[0x01] = (byte) 0xFF; // when no LAN connection, always receive 0xFF from port.  Simulates empty socket.
                    dmgcpu.triggerInterruptIfEnabled(dmgcpu.INT_SER);
                    registers[0x02] &= 0x7F;
                }
                break;

            case 0x04:           // DIV
                registers[04] = 0;
                break;

            case 0x07:           // TAC
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

            case 0x10:           // Sound channel 1, sweep
                registers[0x10] = (byte) data;
                break;

            case 0x11:           // Sound channel 1, length and wave duty
                registers[0x11] = (byte) data;
                break;

            case 0x12:           // Sound channel 1, volume envelope
                registers[0x12] = (byte) data;
                break;

            case 0x13:           // Sound channel 1, frequency low
                registers[0x13] = (byte) data;
                break;

            case 0x14:           // Sound channel 1, frequency high
                registers[0x14] = (byte) data;

                break;

            case 0x17:           // Sound channel 2, volume envelope
                registers[0x17] = (byte) data;
                break;

            case 0x18:           // Sound channel 2, frequency low
                registers[0x18] = (byte) data;
                break;

            case 0x19:           // Sound channel 2, frequency high
                registers[0x19] = (byte) data;
                break;

            case 0x16:           // Sound channel 2, length and wave duty
                registers[0x16] = (byte) data;
                break;

            case 0x1A:           // Sound channel 3, on/off
                registers[0x1A] = (byte) data;
                break;

            case 0x1B:           // Sound channel 3, length
                registers[0x1B] = (byte) data;
                break;

            case 0x1C:           // Sound channel 3, volume
                registers[0x1C] = (byte) data;
                break;

            case 0x1D:           // Sound channel 3, frequency lower 8-bit
                registers[0x1D] = (byte) data;
                break;

            case 0x1E:           // Sound channel 3, frequency higher 3-bit
                registers[0x1E] = (byte) data;
                break;

            case 0x20:           // Sound channel 4, length
                registers[0x20] = (byte) data;
                break;
            case 0x21:           // Sound channel 4, volume envelope
                registers[0x21] = (byte) data;
                break;

            case 0x22:           // Sound channel 4, polynomial parameters
                registers[0x22] = (byte) data;
                break;

            case 0x23:          // Sound channel 4, initial/consecutive
                registers[0x23] = (byte) data;
                break;

            case 0x25:           // Stereo select
                registers[0x25] = (byte) data;

                break;

            case 0x30:
            case 0x31:
            case 0x32:
            case 0x33:
            case 0x34:
            case 0x35:
            case 0x36:
            case 0x37:
            case 0x38:
            case 0x39:
            case 0x3A:
            case 0x3B:
            case 0x3C:
            case 0x3D:
            case 0x3E:
            case 0x3F:
                registers[num] = (byte) data;
                break;


            case 0x40:           // LCDC
                //    System.out.println("LCDC write at " + JavaBoy.hexWord(dmgcpu.pc) + " = " + JavaBoy.hexWord(data));
                dmgcpu.graphicsChipOld.bgEnabled = true;

                // BIT 5
                dmgcpu.graphicsChipOld.winEnabled = (data & 0x20) == 0x20;

                // BIT 4
                dmgcpu.graphicsChipOld.bgWindowDataSelect = (data & 0x10) == 0x10;

                dmgcpu.graphicsChipOld.hiBgTileMapAddress = (data & 0x08) == 0x08;

                // BIT 2
                dmgcpu.graphicsChipOld.doubledSprites = (data & 0x04) == 0x04;

                // BIT 1
                dmgcpu.graphicsChipOld.spritesEnabled = (data & 0x02) == 0x02;

                if ((data & 0x01) == 0x00) {     // BIT 0
                    dmgcpu.graphicsChipOld.bgEnabled = false;
                    dmgcpu.graphicsChipOld.winEnabled = false;
                }

                registers[0x40] = (byte) data;
                break;

            case 0x41:
                //    System.out.println("STAT set to " + data + " lcdc is " + JavaBoy.unsign(registers[0x44]) + " pc is " + JavaBoy.hexWord(dmgcpu.pc));
                registers[0x41] = (byte) data;
                break;

            case 0x42:           // SCY
                //    System.out.println("SCY set to " + data + " lcdc is " + JavaBoy.unsign(registers[0x44]) + " pc is " + JavaBoy.hexWord(dmgcpu.pc));
                registers[0x42] = (byte) data;
                break;

            case 0x43:           // SCX
                //    System.out.println("SCX set to " + data + " lcdc is " + JavaBoy.unsign(registers[0x44]) + " pc is " + JavaBoy.hexWord(dmgcpu.pc));
                registers[0x43] = (byte) data;
                break;

            case 0x46:           // DMA
                int sourceAddress = (data << 8);
                //    System.out.println("DMA Transfer initiated from " + JavaBoy.hexWord(sourceAddress) + "!");

                // This could be sped up using System.arrayCopy, but hey.
                for (int i = 0x00; i < 0xA0; i++) {
                    dmgcpu.addressWrite(0xFE00 + i, dmgcpu.addressRead(sourceAddress + i));
                }
                // This is meant to be run at the same time as the CPU is executing
                // instructions, but I don't think it's crucial.
                break;
            case 0x47:           // FF47 - BKG and WIN palette
                //    System.out.println("Palette created!");
                dmgcpu.graphicsChipOld.backgroundPalette.decodePalette(data);
                if (registers[num] != (byte) data) {
                    registers[num] = (byte) data;
                    dmgcpu.graphicsChipOld.invalidateAll(GraphicsChip.TILE_BKG);
                }
                break;
            case 0x48:           // FF48 - OBJ1 palette
                dmgcpu.graphicsChipOld.obj1Palette.decodePalette(data);
                if (registers[num] != (byte) data) {
                    registers[num] = (byte) data;
                    dmgcpu.graphicsChipOld.invalidateAll(GraphicsChip.TILE_OBJ1);
                }
                break;
            case 0x49:           // FF49 - OBJ2 palette
                dmgcpu.graphicsChipOld.obj2Palette.decodePalette(data);
                if (registers[num] != (byte) data) {
                    registers[num] = (byte) data;
                    dmgcpu.graphicsChipOld.invalidateAll(GraphicsChip.TILE_OBJ2);
                }
                break;

            case 0x4F:
                if (dmgcpu.gbcFeatures) {
                    dmgcpu.graphicsChipOld.tileStart = (data & 0x01) * 384;
                    dmgcpu.graphicsChipOld.vidRamStart = (data & 0x01) * 0x2000;
                }
                registers[0x4F] = (byte) data;
                break;


            case 0x55:
                if ((!hdmaRunning) && ((registers[0x55] & 0x80) == 0) && ((data & 0x80) == 0)) {
                    int dmaSrc = (JavaBoy.unsign(registers[0x51]) << 8) +
                            (JavaBoy.unsign(registers[0x52]) & 0xF0);
                    int dmaDst = ((JavaBoy.unsign(registers[0x53]) & 0x1F) << 8) +
                            (JavaBoy.unsign(registers[0x54]) & 0xF0) + 0x8000;
                    int dmaLen = ((JavaBoy.unsign(data) & 0x7F) * 16) + 16;

                    if (dmaLen > 2048) dmaLen = 2048;

                    for (int r = 0; r < dmaLen; r++) {
                        dmgcpu.addressWrite(dmaDst + r, dmgcpu.addressRead(dmaSrc + r));
                    }
                } else {
                    if ((JavaBoy.unsign(data) & 0x80) == 0x80) {
                        hdmaRunning = true;
                        //      System.out.println("HDMA started");
                        registers[0x55] = (byte) (data & 0x7F);
                        break;
                    } else if ((hdmaRunning) && ((JavaBoy.unsign(data) & 0x80) == 0)) {
                        hdmaRunning = false;
                        //      System.out.println("HDMA stopped");
                    }
                }

                registers[0x55] = (byte) data;
                break;


            case 0x69:           // FF69 - BCPD: GBC BG Palette data write

                if (dmgcpu.gbcFeatures) {
                    int palNumber = (registers[0x68] & 0x38) >> 3;
                    dmgcpu.graphicsChipOld.gbcBackground[palNumber].setGbcColours(
                            (JavaBoy.unsign(registers[0x68]) & 0x06) >> 1,
                            (JavaBoy.unsign(registers[0x68]) & 0x01) == 1, JavaBoy.unsign(data));
                    dmgcpu.graphicsChipOld.invalidateAll(palNumber * 4);

                    if ((JavaBoy.unsign(registers[0x68]) & 0x80) != 0) {
                        registers[0x68]++;
                    }

                }


                registers[0x69] = (byte) data;
                break;

            case 0x6B:           // FF6B - OCPD: GBC Sprite Palette data write

                if (dmgcpu.gbcFeatures) {
                    int palNumber = (registers[0x6A] & 0x38) >> 3;
                    //     System.out.print("Pal " + palNumber + "  ");
                    dmgcpu.graphicsChipOld.gbcSprite[palNumber].setGbcColours(
                            (JavaBoy.unsign(registers[0x6A]) & 0x06) >> 1,
                            (JavaBoy.unsign(registers[0x6A]) & 0x01) == 1, JavaBoy.unsign(data));
                    dmgcpu.graphicsChipOld.invalidateAll((palNumber * 4) + 32);

                    if ((JavaBoy.unsign(registers[0x6A]) & 0x80) != 0) {
                        if ((registers[0x6A] & 0x3F) == 0x3F) {
                            registers[0x6A] = (byte) 0x80;
                        } else {
                            registers[0x6A]++;
                        }
                    }
                }

                registers[0x6B] = (byte) data;
                break;


            case 0x70:           // FF70 - GBC Work RAM bank
                if (dmgcpu.gbcFeatures) {
                    if (((data & 0x07) == 0) || ((data & 0x07) == 1)) {
                        dmgcpu.gbcRamBank = 1;
                    } else {
                        dmgcpu.gbcRamBank = data & 0x07;
                    }
                }
                registers[0x70] = (byte) data;
                break;


            default:

                registers[num] = (byte) data;
                break;
        }
    }
}

