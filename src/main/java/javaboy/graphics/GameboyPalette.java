package javaboy.graphics;

import javaboy.lang.Byte;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a palette.  There can be three
 * palettes, one for the background and window, and two
 * for sprites.
 */

public class GameboyPalette {

    /**
     * Data for which colour maps to which RGB value
     */
    private final List<Byte> data = new ArrayList<>(4);

    /**
     * Default RGB colour values
     */
    private final int[] colours = {0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000};

    /**
     * Create a palette with the specified colour mappings
     */
    GameboyPalette(int c1, int c2, int c3, int c4) {
        data.add(0, new Byte(c1));
        data.add(1, new Byte(c2));
        data.add(2, new Byte(c3));
        data.add(3, new Byte(c4));
    }

    /**
     * Set the palette from the internal Gameboy format
     */
    public void decodePalette(int palette) {
        data.add(0, new Byte((palette & 0x03)));
        data.add(1, new Byte((palette & 0x0C) >> 2));
        data.add(2, new Byte((palette & 0x30) >> 4));
        data.add(3, new Byte((palette & 0xC0) >> 6));
    }

    /**
     * Get the RGB colour value for a specific colour entry
     */
    int getRgbEntry(int entry) {
        return colours[data.get(entry).intValue()];
    }

    /**
     * Get the colour number for a specific colour entry
     */
    int getEntry(int entry) {
        return data.get(entry).intValue();
    }
}
