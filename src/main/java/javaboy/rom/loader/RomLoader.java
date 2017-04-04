package javaboy.rom.loader;

import javaboy.memory.Memory;
import org.pmw.tinylog.Logger;

import java.io.*;

public class RomLoader {

    public static Memory loadRom(String filepath, int size) {
        try {
            InputStream is = new FileInputStream(new File(filepath));

            byte[] data = new byte[size];   // Recreate the ROM array with the correct size

            //noinspection ResultOfMethodCallIgnored
            is.read(data);
            is.close();

            Logger.debug("Loaded ROM 'bgblogo.gb'.  2 ROM banks, 32Kb.  0 RAM banks. Type: ROM Only");
            return new Memory(0x0000, data);
        } catch (IOException exception) {
            Logger.debug("Error opening ROM image");
            throw new IllegalArgumentException();
        }
    }



}
