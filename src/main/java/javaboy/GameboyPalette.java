package javaboy;

/**
 * This class represents a palette.  There can be three
 * palettes, one for the background and window, and two
 * for sprites.
 */

class GameboyPalette {

    /**
     * Data for which colour maps to which RGB value
     */
    private int[] data = new int[4];

    /**
     * Default RGB colour values
     */
    private int[] colours = {0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000};

    /**
     * Create a palette with the specified colour mappings
     */
    GameboyPalette(int c1, int c2, int c3, int c4) {
        data[0] = (short) c1;
        data[1] = (short) c2;
        data[2] = (short) c3;
        data[3] = (short) c4;
    }

    /**
     * Set the palette from the internal Gameboy format
     */
    void decodePalette(int palette) {
        data[0] = (palette & 0x03) >> 0;
        data[1] = (palette & 0x0C) >> 2;
        data[2] = (palette & 0x30) >> 4;
        data[3] = (palette & 0xC0) >> 6;
    }

    /**
     * Get the RGB colour value for a specific colour entry
     */
    int getRgbEntry(int e) {
        return colours[data[e]];
    }

    /**
     * Get the colour number for a specific colour entry
     */
    int getEntry(int e) {
        return data[e];
    }
}
