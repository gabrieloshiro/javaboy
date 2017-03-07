package javaboy;

import org.pmw.tinylog.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RomLoader {

    public static Memory loadRom(String filepath, int size) {
        InputStream is;
        try {
            is = new FileInputStream(new File(filepath));

            byte[] data = new byte[size];   // Recreate the ROM array with the correct size

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
