package javaboy;

import java.awt.*;

/**
 * This class represents a palette.  There can be three
 * palettes, one for the background and window, and two
 * for sprites.
 */

class GameboyPalette {

    /**
     * Data for which colour maps to which RGB value
     */
    private short[] data = new short[4];

    private int[] gbcData = new int[4];

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
     * Get the palette from the internal Gameboy Color format
     */
    byte getGbcColours(int entryNo, boolean high) {
        if (high) {
            return (byte) (gbcData[entryNo] >> 8);

        } else {
            return (byte) (gbcData[entryNo] & 0x00FF);

        }
    }

    /**
     * Set the palette from the internal Gameboy Color format
     */
    void setGbcColours(int entryNo, boolean high, int dat) {
        if (high) {
            gbcData[entryNo] = (gbcData[entryNo] & 0x00FF) | (dat << 8);

        } else {
            gbcData[entryNo] = (gbcData[entryNo] & 0xFF00) | dat;

        }

        int red = (gbcData[entryNo] & 0x001F) << 3;

        int green = (gbcData[entryNo] & 0x03E0) >> 2;

        int blue = (gbcData[entryNo] & 0x7C00) >> 7;

        data[0] = 0;
        data[1] = 1;
        data[2] = 2;
        data[3] = 3;

        Color c = new Color(red, green, blue);

        colours[entryNo] = c.getRGB();
    }

    /**
     * Set the palette from the internal Gameboy format
     */
    void decodePalette(int pal) {
        data[0] = (short) ((pal & 0x03) >> 0);
        data[1] = (short) ((pal & 0x0C) >> 2);
        data[2] = (short) ((pal & 0x30) >> 4);
        data[3] = (short) ((pal & 0xC0) >> 6);
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
    short getEntry(int e) {
        return data[e];
    }
}
