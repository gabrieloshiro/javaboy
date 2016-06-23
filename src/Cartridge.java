/*

JavaBoy
                                  
COPYRIGHT (C) 2001 Neil Millstone and The Victoria University of Manchester
                                                                         ;;;
This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation; either version 2 of the License, or (at your option)
any later version.        

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
more details.


You should have received a copy of the GNU General Public License along with
this program; if not, write to the Free Software Foundation, Inc., 59 Temple
Place - Suite 330, Boston, MA 02111-1307, USA.

*/


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

/**
 * This class represents the game cartridge and contains methods to load the ROM and battery RAM
 * (if necessary) from disk or over the web, and handles emulation of ROM mappers and RAM banking.
 * It is missing emulation of MBC3 (this is very rare).
 */

class Cartridge {
    /**
     * Translation between ROM size byte contained in the ROM header, and the number
     * of 16Kb ROM banks the cartridge will contain
     */
    private final int[][] romSizeTable = {{0, 2}, {1, 4}, {2, 8}, {3, 16}, {4, 32},
            {5, 64}, {6, 128}, {7, 256}, {0x52, 72}, {0x53, 80}, {0x54, 96}};

    /**
     * RTC Reg names
     */
    private final byte SECONDS = 0;
    private final byte MINUTES = 1;
    private final byte HOURS = 2;
    private final byte DAYS_LO = 3;
    private final byte DAYS_HI = 4;

    /**
     * Contains the complete ROM image of the cartridge
     */
    byte[] rom;

    /**
     * Contains the RAM on the cartridge
     */
    private byte[] ram = new byte[0x10000];

    /**
     * Cartridge type - index into cartTypeTable[][]
     */
    private int cartType;

    /**
     * Starting address of the ROM bank at 0x4000 in CPU address space
     */
    private int pageStart = 0x4000;

    /**
     * The bank number which is currently mapped at 0x4000 in CPU address space
     */
    private int currentBank = 1;

    /**
     * The bank which has been saved when the debugger changes the ROM mapping.  The mapping is
     * restored from this register when execution resumes
     */
    private int savedBank = -1;

    /**
     * The RAM bank number which is currently mapped at 0xA000 in CPU address space
     */
    private int ramBank;
    private int ramPageStart;

    private boolean mbc1LargeRamMode = false;
    private boolean ramEnabled;

    /**
     * Real time clock registers.  Only used on MBC3
     */
    private int[] RTCReg = new int[5];
    private long lastSecondIncrement;

    /**
     * Create a cartridge object, loading ROM and any associated battery RAM from the cartridge
     * filename given.  Loads via the web if JavaBoy is running as an applet
     */
    Cartridge(String romFileName) {
        InputStream is;
        try {
            is = new FileInputStream(new File(romFileName));

            byte[] firstBank = new byte[0x04000];

            int total = 0x04000;
            do {
                total -= is.read(firstBank, 0x04000 - total, total);      // Read the first bank (bank 0)
            } while (total > 0);

            cartType = firstBank[0x0147];

            /*
      Number of 16Kb ROM banks
     */
            int numBanks = lookUpCartSize(firstBank[0x0148]);

            rom = new byte[0x04000 * numBanks];   // Recreate the ROM array with the correct size

            // Copy first bank into main rom array
            for (int r = 0; r < 0x4000; r++) {
                rom[r] = firstBank[r];
            }

            total = 0x04000 * (numBanks - 1);           // Calculate total ROM size (first one already loaded)
            do {                                  // Read ROM into memory
                total -= is.read(rom, rom.length - total, total); // Read the entire ROM
            } while (total > 0);
            is.close();

            System.out.println("Loaded ROM '" + romFileName + "'.  " + numBanks + " banks, " + (numBanks * 16) + "Kb.  " + getNumRAMBanks() + " RAM banks.");
            /*
      Contains strings of the standard names of the cartridge mapper chips, indexed by
      cartridge type
     */
            String[] cartTypeTable = {"ROM Only",             /* 00 */
                    "ROM+MBC1",             /* 01 */
                    "ROM+MBC1+RAM",         /* 02 */
                    "ROM+MBC1+RAM+BATTERY", /* 03 */
                    "Unknown",              /* 04 */
                    "ROM+MBC2",             /* 05 */
                    "ROM+MBC2+BATTERY",     /* 06 */
                    "Unknown",              /* 07 */
                    "ROM+RAM",              /* 08 */
                    "ROM+RAM+BATTERY",      /* 09 */
                    "Unknown",              /* 0A */
                    "Unsupported ROM+MMM01",/* 0B */
                    "Unsupported ROM+MMM01+SRAM",             /* 0C */
                    "Unsupported ROM+MMM01+SRAM+BATTERY",     /* 0D */
                    "Unknown",				 /* 0E */
                    "ROM+MBC3+TIMER+BATTERY",     /* 0F */
                    "ROM+MBC3+TIMER+RAM+BATTERY", /* 10 */
                    "ROM+MBC3",             /* 11 */
                    "ROM+MBC3+RAM",         /* 12 */
                    "ROM+MBC3+RAM+BATTERY", /* 13 */
                    "Unknown",              /* 14 */
                    "Unknown",              /* 15 */
                    "Unknown",              /* 16 */
                    "Unknown",              /* 17 */
                    "Unknown",              /* 18 */
                    "ROM+MBC5",             /* 19 */
                    "ROM+MBC5+RAM",         /* 1A */
                    "ROM+MBC5+RAM+BATTERY", /* 1B */
                    "ROM+MBC5+RUMBLE",      /* 1C */
                    "ROM+MBC5+RUMBLE+RAM",  /* 1D */
                    "ROM+MBC5+RUMBLE+RAM+BATTERY"  /* 1E */};
            System.out.println("Type: " + cartTypeTable[cartType] + " (" + JavaBoy.hexByte(cartType) + ")");

            // Set up the real time clock
            Calendar rightNow = Calendar.getInstance();

            int days = rightNow.get(Calendar.DAY_OF_YEAR);
            int hour = rightNow.get(Calendar.HOUR_OF_DAY);
            int minute = rightNow.get(Calendar.MINUTE);
            int second = rightNow.get(Calendar.SECOND);

            RTCReg[SECONDS] = second;
            RTCReg[MINUTES] = minute;
            RTCReg[HOURS] = hour;
            RTCReg[DAYS_LO] = days & 0x00FF;
            RTCReg[DAYS_HI] = (days & 0x01FF) >> 8;

            long realTimeStart = System.currentTimeMillis();
            lastSecondIncrement = realTimeStart;

        } catch (IOException e) {
            System.out.println("Error opening ROM image '" + romFileName + "'!");
        }
    }

    void update() {
        // Update the realtime clock from the system time
        long millisSinceLastUpdate = System.currentTimeMillis() - lastSecondIncrement;

        while (millisSinceLastUpdate > 1000) {
            millisSinceLastUpdate -= 1000;
            RTCReg[SECONDS]++;
            if (RTCReg[SECONDS] == 60) {
                RTCReg[MINUTES]++;
                RTCReg[SECONDS] = 0;
                if (RTCReg[MINUTES] == 60) {
                    RTCReg[HOURS]++;
                    RTCReg[MINUTES] = 0;
                    if (RTCReg[HOURS] == 24) {
                        if (RTCReg[DAYS_LO] == 255) {
                            RTCReg[DAYS_LO] = 0;
                            RTCReg[DAYS_HI] = 1;
                        } else {
                            RTCReg[DAYS_LO]++;
                        }
                        RTCReg[HOURS] = 0;
                    }
                }
            }
            lastSecondIncrement = System.currentTimeMillis();
        }
    }

    /**
     * Returns the byte currently mapped to a CPU address.  Addr must be in the range 0x0000 - 0x4000 or
     * 0xA000 - 0xB000 (for RAM access)
     */
    final byte addressRead(int addr) {
        if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
            switch (cartType) {
                case 0x0F:
                case 0x10:
                case 0x11:
                case 0x12:
                case 0x13: {	/* MBC3 */
                    if (ramBank >= 0x04) {
                        //	   System.out.println("Reading RTC reg " + ramBank + " is " + RTCReg[ramBank - 0x08]);
                        return (byte) RTCReg[ramBank - 0x08];
                    } else {
                        return ram[addr - 0xA000 + ramPageStart];
                    }
                }

                default: {
                    return ram[addr - 0xA000 + ramPageStart];
                }
            }
        }
        if (addr < 0x4000) {
            return rom[addr];
        } else {
            return rom[pageStart + addr - 0x4000];
        }
        //  }
    }


    /**
     * Returns a string summary of the current mapper status
     */
    String getMapInfo() {
        String out;
        switch (cartType) {
            case 0 /* No mapper */ :
            case 8:
            case 9:
                return "This ROM has no mapper.";
            case 1 /* MBC1      */ :
                return "MBC1: ROM bank " + JavaBoy.hexByte(currentBank) + " mapped to " +
                        " 4000 - 7FFFF";
            case 2 /* MBC1+RAM  */ :
            case 3 /* MBC1+RAM+BATTERY */ :
                out = "MBC1: ROM bank " + JavaBoy.hexByte(currentBank) + " mapped to " +
                        " 4000 - 7FFFF.  ";
                if (mbc1LargeRamMode) {
                    out = out + "Cartridge is in 16MBit ROM/8KByte RAM Mode.";
                } else {
                    out = out + "Cartridge is in 4MBit ROM/32KByte RAM Mode.";
                }
                return out;
            case 5:
            case 6:
                return "MBC2: ROM bank " + JavaBoy.hexByte(currentBank) + " mapped to 4000 - 7FFF";

            case 0x19:
            case 0x1C:
                return "MBC5: ROM bank " + JavaBoy.hexByte(currentBank) + " mapped to 4000 - 7FFF";

            case 0x1A:
            case 0x1B:
            case 0x1D:
            case 0x1E:
                return "MBC5: ROM bank " + JavaBoy.hexByte(currentBank) + " mapped to 4000 - 7FFF";

        }
        return "Unknown mapper.";
    }

    /**
     * Maps a ROM bank into the CPU address space at 0x4000
     */
    void mapRom(int bankNo) {
        currentBank = bankNo;
        pageStart = 0x4000 * bankNo;
    }

    void reset() {
        mapRom(1);
    }

    /**
     * Save the current mapper state
     */
    void saveMapping() {
        if ((cartType != 0) && (savedBank == -1)) savedBank = currentBank;
    }

    /**
     * Restore the saved mapper state
     */
    void restoreMapping() {
        if (savedBank != -1) {
            System.out.println("- ROM Mapping restored to bank " + JavaBoy.hexByte(savedBank));
            addressWrite(0x2000, savedBank);
            savedBank = -1;
        }
    }

    /**
     * Writes to an address in CPU address space.  Writes to ROM may cause a mapping change.
     */
    final void addressWrite(int addr, int data) {
        int ramAddress;


        switch (cartType) {

            case 0: /* ROM Only */
                break;

            case 1: /* MBC1 */
            case 2:
            case 3:
                if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
                    if (ramEnabled) {
                        ramAddress = addr - 0xA000 + ramPageStart;
                        ram[ramAddress] = (byte) data;
                    }
                }
                if ((addr >= 0x2000) && (addr <= 0x3FFF)) {
                    int bankNo = data & 0x1F;
                    if (bankNo == 0) bankNo = 1;
                    mapRom((currentBank & 0x60) | bankNo);
                } else if ((addr >= 0x6000) && (addr <= 0x7FFF)) {
                    //      ram = new byte[0x8000];
                    //      ram = new byte[0x2000];
                    mbc1LargeRamMode = (data & 1) == 1;
                } else if (addr <= 0x1FFF) {
                    ramEnabled = (data & 0x0F) == 0x0A;
                } else if ((addr <= 0x5FFF) && (addr >= 0x4000)) {
                    if (mbc1LargeRamMode) {
                        ramBank = (data & 0x03);
                        ramPageStart = ramBank * 0x2000;
                        //      System.out.println("RAM bank " + ramBank + " selected!");
                    } else {
                        mapRom((currentBank & 0x1F) | ((data & 0x03) << 5));
                    }
                }
                break;

            case 5:
            case 6:
                if ((addr >= 0x2000) && (addr <= 0x3FFF) && ((addr & 0x0100) != 0)) {
                    int bankNo = data & 0x1F;
                    if (bankNo == 0) bankNo = 1;
                    mapRom(bankNo);
                }
                if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
                    if (ramEnabled) ram[addr - 0xA000 + ramPageStart] = (byte) data;
                }

                break;

            case 0x0F:
            case 0x10:
            case 0x11:
            case 0x12:
            case 0x13:	/* MBC3 */

                // Select ROM bank
                if ((addr >= 0x2000) && (addr <= 0x3FFF)) {
                    int bankNo = data & 0x7F;
                    if (bankNo == 0) bankNo = 1;
                    mapRom(bankNo);
                } else if ((addr <= 0x5FFF) && (addr >= 0x4000)) {
                    // Select RAM bank
                    ramBank = data;

                    if (ramBank < 0x04) {
                        ramPageStart = ramBank * 0x2000;
                    }
                    //     System.out.println("RAM bank " + ramBank + " selected!");
                }
                if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
                    // Let the game write to RAM
                    if (ramBank <= 0x03) {
                        ram[addr - 0xA000 + ramPageStart] = (byte) data;
                    } else {
                        // Write to realtime clock registers
                        RTCReg[ramBank - 0x08] = data;
                        //     System.out.println("RTC Reg " + ramBank + " = " + data);
                    }

                }
                break;


            case 0x19:
            case 0x1A:
            case 0x1B:
            case 0x1C:
            case 0x1D:
            case 0x1E:

                if ((addr >= 0x2000) && (addr <= 0x2FFF)) {
                    int bankNo = (currentBank & 0xFF00) | data;
                    mapRom(bankNo);
                }
                if ((addr >= 0x3000) && (addr <= 0x3FFF)) {
                    int bankNo = (currentBank & 0x00FF) | ((data & 0x01) << 8);
                    mapRom(bankNo);
                }

                if ((addr >= 0x4000) && (addr <= 0x5FFF)) {
                    ramBank = (data & 0x07);
                    ramPageStart = ramBank * 0x2000;
                    //     System.out.println("RAM bank " + ramBank + " selected!");
                }
                if ((addr >= 0xA000) && (addr <= 0xBFFF)) {
                    ram[addr - 0xA000 + ramPageStart] = (byte) data;
                }
                break;


        }

    }

    private int getNumRAMBanks() {
        switch (rom[0x149]) {
            case 0: {
                return 0;
            }
            case 1:
            case 2: {
                return 1;
            }
            case 3: {
                return 4;
            }
            case 4: {
                return 16;
            }
        }
        return 0;
    }

    /**
     * Returns the number of 16Kb banks in a cartridge from the header size byte.
     */
    private int lookUpCartSize(int sizeByte) {
        int i = 0;
        while ((i < romSizeTable.length) && (romSizeTable[i][0] != sizeByte)) {
            i++;
        }

        if (romSizeTable[i][0] == sizeByte) {
            return romSizeTable[i][1];
        } else {
            return -1;
        }
    }

}

