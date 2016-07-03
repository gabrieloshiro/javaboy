package javaboy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

class Cartridge {

    byte[] rom;

    Cartridge(String romFileName) {
        InputStream is;
        try {
            is = new FileInputStream(new File(romFileName));

            rom = new byte[0x08000];   // Recreate the ROM array with the correct size

            is.read(rom);
            is.close();

            System.out.println("Loaded ROM '" + romFileName + "'.  2 ROM banks, 32Kb.  0 RAM banks. Type: ROM Only");

        } catch (IOException e) {
            System.out.println("Error opening ROM image '" + romFileName + "'!");
        }
    }

    /**
     * Returns the byte currently mapped to a CPU address.  Addr must be in the range 0x0000 - 0x4000 or
     * 0xA000 - 0xB000 (for RAM access)
     */
    final byte addressRead(int addr) {
        return rom[addr];
    }
}

