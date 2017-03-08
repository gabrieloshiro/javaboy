package javaboy.graphics;

import java.awt.*;
import java.awt.image.DirectColorModel;
import java.awt.image.MemoryImageSource;

/**
 * This class represents a tile in the tile data area.  It
 * contains images for a tile in each of it's three palettes
 * and images that are flipped horizontally and vertically.
 * The images are only created when needed, by calling
 * updateImage().  They can then be drawn by calling draw().
 */
class GameboyTile {

    private static final int IMAGE_COUNT = 64;


    private GraphicsChip graphicsChip;
    Image[] image = new Image[IMAGE_COUNT];

    /**
     * True, if the tile's image in the image[] array is a valid representation of the tile as it
     * appears in video memory.
     */
    boolean[] valid = new boolean[IMAGE_COUNT];

    MemoryImageSource[] source = new MemoryImageSource[IMAGE_COUNT];

    /**
     * Current magnification value of Gameboy screen
     */
    int[] imageData = new int[IMAGE_COUNT];
    Component a;

    /**
     * Initialize a new Gameboy tile
     */
    GameboyTile(GraphicsChip graphicsChip, Component a) {
        this.graphicsChip = graphicsChip;
        allocateImage(GraphicsChip.TILE_BKG, a);
        this.a = a;
    }

    /**
     * Allocate memory for the tile image with the specified attributes
     */
    void allocateImage(int attributes, Component a) {
        source[attributes] = new MemoryImageSource(8, 8,
                new DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000),
                imageData, 0, 8);
        source[attributes].setAnimated(true);
        image[attributes] = a.createImage(source[attributes]);
    }

    /**
     * Free memory used by this tile
     */
    void dispose() {
        for (int r = 0; r < IMAGE_COUNT; r++) {
            if (image[r] != null) {
                image[r].flush();
                valid[r] = false;
            }
        }
    }

    /**
     * Returns true if this tile does not contain a valid image for the tile with the specified
     * attributes
     */
    boolean invalid(int attributes) {
        return (!valid[attributes]);
    }

    /**
     * Create the image of a tile in the tile cache by reading the relevant data from video
     * memory
     */
    private void updateImage(byte[] videoRam, int offset, int attribs) {
        int px, py;
        int rgbValue;

        if (image[attribs] == null) {
            allocateImage(attribs, a);
        }

        GameboyPalette pal;

        if ((attribs & GraphicsChip.TILE_OBJ_1) != 0) {
            pal = graphicsChip.obj1Palette;
        } else if ((attribs & GraphicsChip.TILE_OBJ_2) != 0) {
            pal = graphicsChip.obj2Palette;
        } else {
            pal = graphicsChip.backgroundPalette;
        }

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {

                if ((attribs & GraphicsChip.TILE_FLIP_X) != 0) {
                    px = 7 - x;
                } else {
                    px = x;
                }
                if ((attribs & GraphicsChip.TILE_FLIP_Y) != 0) {
                    py = 7 - y;
                } else {
                    py = y;
                }

                int pixelColorLower = (videoRam[offset + (py * 2)] & (0x80 >> px)) >> (7 - px);
                int pixelColorUpper = (videoRam[offset + (py * 2) + 1] & (0x80 >> px)) >> (7 - px);

                int entryNumber = (pixelColorUpper * 2) + pixelColorLower;
                pal.getEntry(entryNumber);

                rgbValue = pal.getRgbEntry(entryNumber);

 /* Turn on transparency for background */

                if (entryNumber == 0) {
                    rgbValue &= 0x00FFFFFF;
                }

                imageData[(y * 8) + x] = rgbValue;

            }
        }

        source[attribs].newPixels();
        valid[attribs] = true;
    }

    /**
     * Draw the tile with the specified attributes into the graphics context given
     */
    void draw(Graphics g, int x, int y, int attribs) {
        g.drawImage(image[attribs], x, y, null);
    }

    /**
     * Ensure that the tile is valid
     */
    void validate(byte[] videoRam, int offset, int attribs) {
        if (!valid[attribs]) {
            updateImage(videoRam, offset, attribs);
        }
    }

    /**
     * Invalidate tile with the specified palette, including all flipped versions.
     */
    void invalidate(int attribs) {
        valid[attribs] = false;       /* Invalidate original image and */
        if (image[attribs] != null) image[attribs].flush();
        valid[attribs + 1] = false;   /* all flipped versions in cache */
        if (image[attribs + 1] != null) image[attribs + 1].flush();
        valid[attribs + 2] = false;
        if (image[attribs + 2] != null) image[attribs + 2].flush();
        valid[attribs + 3] = false;
        if (image[attribs + 3] != null) image[attribs + 3].flush();
    }

    /**
     * Invalidate this tile
     */
    void invalidate() {
        for (int r = 0; r < IMAGE_COUNT; r++) {
            valid[r] = false;
            if (image[r] != null) image[r].flush();
            image[r] = null;
        }
    }

}
