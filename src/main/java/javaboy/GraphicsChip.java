package javaboy;

import java.awt.*;
import java.awt.image.DirectColorModel;
import java.awt.image.MemoryImageSource;

class GraphicsChip {

    /**
     * Tile uses the background palette
     */
    static final int TILE_BKG = 0;

    /**
     * Tile uses the first sprite palette
     */
    static final int TILE_OBJ_1 = 4;

    /**
     * Tile uses the second sprite palette
     */
    static final int TILE_OBJ_2 = 8;

    /**
     * Tile is flipped horizontally
     */
    private static final int TILE_FLIP_X = 1;

    /**
     * Tile is flipped vertically
     */
    private static final int TILE_FLIP_Y = 2;

    /**
     * The current contents of the video memory, mapped in at 0x8000 - 0x9FFF
     */
    private byte[] videoRam = new byte[0x8000];

    GameboyPalette backgroundPalette;
    GameboyPalette obj1Palette;
    GameboyPalette obj2Palette;

    boolean spritesEnabled = true;
    boolean bgEnabled = true;
    boolean winEnabled = true;

    /**
     * The image containing the Gameboy screen
     */
    private Image backBuffer;

    /**
     * The current frame skip value
     */
    private int frameSkip = 2;

    /**
     * The number of frames that have been drawn so far in the current frame sampling period
     */
    private int framesDrawn = 0;

    /**
     * Amount of time to wait between frames (ms)
     */
    int frameWaitTime = 0;

    /**
     * The current frame has finished drawing
     */
    boolean frameDone = false;
    long startTime = 0;

    /**
     * Selection of one of two addresses for the BG and Window tile data areas
     */
    boolean bgWindowDataSelect = true;

    /**
     * If true, 8x16 sprites are being used.  Otherwise, 8x8.
     */
    boolean doubledSprites = false;

    /**
     * Selection of one of two address for the BG tile map.
     */
    boolean hiBgTileMapAddress = false;
    private Dmgcpu dmgcpu;
    private int vidRamStart = 0;

    /**
     * Tile cache
     */
    private GameboyTile[] tiles = new GameboyTile[384 * 2];

    // Hacks to allow some raster effects to work.  Or at least not to break as badly.
    private boolean savedWindowDataSelect = false;

    private boolean windowEnableThisLine = false;
    private int windowStopLine = 144;

    GraphicsChip(Component a, Dmgcpu d) {
        dmgcpu = d;

        backgroundPalette = new GameboyPalette(0, 1, 2, 3);
        obj1Palette = new GameboyPalette(0, 1, 2, 3);
        obj2Palette = new GameboyPalette(0, 1, 2, 3);

        backBuffer = a.createImage(160, 144);

        for (int r = 0; r < 384 * 2; r++) {
            tiles[r] = new GameboyTile(a);
        }
    }

    /**
     * Calculate the number of frames per second for the current sampling period
     */
    private void calculateFPS() {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        if (framesDrawn > 30) {
            long delay = System.currentTimeMillis() - startTime;
            int averageFPS = (int) ((framesDrawn) / (delay / 1000f));
            startTime = System.currentTimeMillis();
            int timePerFrame;

            if (averageFPS != 0) {
                timePerFrame = 1000 / averageFPS;
            } else {
                timePerFrame = 100;
            }
            frameWaitTime = 17 - timePerFrame + frameWaitTime;
            framesDrawn = 0;
        }
    }

    /**
     * Flush the tile cache
     */
    void dispose() {
        for (int r = 0; r < 384 * 2; r++) {
            if (tiles[r] != null) tiles[r].dispose();
        }
    }

    /**
     * Reads data from the specified video RAM address
     */
    short addressRead(int addr) {
        return videoRam[addr + vidRamStart];
    }

    /**
     * Writes data to the specified video RAM address
     */
    void addressWrite(int addr, byte data) {
        if (addr < 0x1800) {   // Bkg Tile data area
            int tileStart = 0;
            tiles[(addr >> 4) + tileStart].invalidate();
            videoRam[addr + vidRamStart] = data;
        } else {
            videoRam[addr + vidRamStart] = data;
        }
    }

    /**
     * Invalidates all tiles in the tile cache that have the given attributes.
     * These will be regenerated next time they are drawn.
     */
    void invalidateAll(int attribs) {
        for (int r = 0; r < 384 * 2; r++) {
            tiles[r].invalidate(attribs);
        }
    }

    /**
     * Draw sprites into the back buffer which have the given priority
     */
    private void drawSprites(Graphics back, int priority) {

        int vidRamAddress;

        // Draw sprites
        for (int i = 0; i < 40; i++) {
            int spriteX = dmgcpu.addressRead(0xFE01 + (i * 4)) - 8;
            int spriteY = dmgcpu.addressRead(0xFE00 + (i * 4)) - 16;
            int tileNum = dmgcpu.addressRead(0xFE02 + (i * 4));
            int attributes = dmgcpu.addressRead(0xFE03 + (i * 4));

            if ((attributes & 0x80) >> 7 == priority) {

                int spriteAttrib = 0;

                if (doubledSprites) {
                    tileNum &= 0xFE;
                }

                vidRamAddress = tileNum << 4;
                if ((attributes & 0x10) != 0) {
                    spriteAttrib |= TILE_OBJ_2;
                } else {
                    spriteAttrib |= TILE_OBJ_1;
                }

                if ((attributes & 0x20) != 0) {
                    spriteAttrib |= TILE_FLIP_X;
                }
                if ((attributes & 0x40) != 0) {
                    spriteAttrib |= TILE_FLIP_Y;
                }

                if (tiles[tileNum].invalid(spriteAttrib)) {
                    tiles[tileNum].validate(videoRam, vidRamAddress, spriteAttrib);
                }

                if ((spriteAttrib & TILE_FLIP_Y) != 0) {
                    if (doubledSprites) {
                        tiles[tileNum].draw(back, spriteX, spriteY + 8, spriteAttrib);
                    } else {
                        tiles[tileNum].draw(back, spriteX, spriteY, spriteAttrib);
                    }
                } else {
                    tiles[tileNum].draw(back, spriteX, spriteY, spriteAttrib);
                }

                if (doubledSprites) {
                    if (tiles[tileNum + 1].invalid(spriteAttrib)) {
                        tiles[tileNum + 1].validate(videoRam, vidRamAddress + 16, spriteAttrib);
                    }


                    if ((spriteAttrib & TILE_FLIP_Y) != 0) {
                        tiles[tileNum + 1].draw(back, spriteX, spriteY, spriteAttrib);
                    } else {
                        tiles[tileNum + 1].draw(back, spriteX, spriteY + 8, spriteAttrib);
                    }
                }
            }
        }

    }

    /**
     * This must be called by the CPU for each scanline drawn by the display hardware.  It
     * handles drawing of the background layer
     */
    void notifyScanline(int line) {

        if ((framesDrawn % frameSkip) != 0) {
            return;
        }

        if (line == 0) {
            clearFrameBuffer();
            drawSprites(backBuffer.getGraphics(), 1);
            //spritesEnabledThisFrame = spritesEnabled;
            windowStopLine = 144;
            windowEnableThisLine = winEnabled;
        }

        // SpritesEnabledThisFrame should be true if sprites were ever on this frame
        //if (spritesEnabled) spritesEnabledThisFrame = true;

        if (windowEnableThisLine) {
            if (!winEnabled) {
                windowStopLine = line;
                windowEnableThisLine = false;
            }
        }

        // Fix to screwed up status bars.  Record which data area is selected on the
        // first line the window is to be displayed.  Will work unless this is changed
        // after window is started
        // NOTE: Still no real support for hblank effects on window/sprites
        if (line == JavaBoy.unsign(dmgcpu.ioHandler.registers[0x4A]) + 1) {        // Compare against WY reg
            savedWindowDataSelect = bgWindowDataSelect;
        }

        int xPixelOfs = JavaBoy.unsign(dmgcpu.ioHandler.registers[0x43]) % 8;
        int yPixelOfs = JavaBoy.unsign(dmgcpu.ioHandler.registers[0x42]) % 8;

        if (((yPixelOfs + line) % 8 == 4) || (line == 0)) {

            if ((line >= 144) && (line < 152)) notifyScanline(line + 8);

            Graphics back = backBuffer.getGraphics();

            int xTileOfs = JavaBoy.unsign(dmgcpu.ioHandler.registers[0x43]) / 8;
            int yTileOfs = JavaBoy.unsign(dmgcpu.ioHandler.registers[0x42]) / 8;
            int bgStartAddress, tileNum;

            int y = ((line + yPixelOfs) / 8);

            if (hiBgTileMapAddress) {
                bgStartAddress = 0x1C00;  /* 1C00 */
            } else {
                bgStartAddress = 0x1800;
            }

            int tileNumAddress, vidMemAddr;

            for (int x = 0; x < 21; x++) {
                if (bgWindowDataSelect) {
                    tileNumAddress = bgStartAddress +
                            (((y + yTileOfs) % 32) * 32) + ((x + xTileOfs) % 32);

                    tileNum = JavaBoy.unsign(videoRam[tileNumAddress]);
                } else {
                    tileNumAddress = bgStartAddress +
                            (((y + yTileOfs) % 32) * 32) + ((x + xTileOfs) % 32);

                    tileNum = 256 + videoRam[tileNumAddress];
                }

                int attribs;

                vidMemAddr = (tileNum << 4);
                attribs = TILE_BKG;

                if (tiles[tileNum].invalid(attribs)) {
                    tiles[tileNum].validate(videoRam, vidMemAddr, attribs);
                }
                tiles[tileNum].
                        draw(back, (8 * x) - xPixelOfs, (8 * y) - yPixelOfs, attribs);
            }
        }
    }

    /**
     * Clears the frame buffer to the background colour
     */
    private void clearFrameBuffer() {
        Graphics back = backBuffer.getGraphics();
        back.setColor(new Color(backgroundPalette.getRgbEntry(0)));
        back.fillRect(0, 0, 160, 144);
    }

    /**
     * Draw the current graphics frame into the given graphics context
     */
    boolean draw(Graphics g, int startX, int startY) {
        int tileNum;

        calculateFPS();
        if ((framesDrawn % frameSkip) != 0) {
            frameDone = true;
            framesDrawn++;
            return false;
        } else {
            framesDrawn++;
        }
        Graphics back = backBuffer.getGraphics();

  /* Draw window */
        if (winEnabled) {
            int wx, wy;
            int windowStartAddress;

            if ((dmgcpu.ioHandler.registers[0x40] & 0x40) != 0) {
                windowStartAddress = 0x1C00;
            } else {
                windowStartAddress = 0x1800;
            }
            wx = JavaBoy.unsign(dmgcpu.ioHandler.registers[0x4B]) - 7;
            wy = JavaBoy.unsign(dmgcpu.ioHandler.registers[0x4A]);

            back.setColor(new Color(backgroundPalette.getRgbEntry(0)));
            back.fillRect(wx, wy, 160, 144);

            int tileAddress;
            int attribs, tileDataAddress;

            for (int y = 0; y < 19 - (wy / 8); y++) {
                for (int x = 0; x < 21 - (wx / 8); x++) {
                    tileAddress = windowStartAddress + (y * 32) + x;

                    //     if (!bgWindowDataSelect) {
                    if (!savedWindowDataSelect) {
                        tileNum = 256 + videoRam[tileAddress];
                    } else {
                        tileNum = JavaBoy.unsign(videoRam[tileAddress]);
                    }
                    tileDataAddress = tileNum << 4;

                    attribs = TILE_BKG;

                    if (wy + y * 8 < windowStopLine) {
                        if (tiles[tileNum].invalid(attribs)) {
                            tiles[tileNum].validate(videoRam, tileDataAddress, attribs);
                        }
                        tiles[tileNum].draw(back, wx + x * 8, wy + y * 8, attribs);
                    }
                }
            }
        }

        // Draw sprites if the flag was on at any time during this frame
        drawSprites(back, 0);

        g.drawImage(backBuffer, startX, startY, null);

        frameDone = true;
        return true;
    }


    /**
     * This class represents a tile in the tile data area.  It
     * contains images for a tile in each of it's three palettes
     * and images that are flipped horizontally and vertically.
     * The images are only created when needed, by calling
     * updateImage().  They can then be drawn by calling draw().
     */
    private class GameboyTile {

        Image[] image = new Image[64];

        /**
         * True, if the tile's image in the image[] array is a valid representation of the tile as it
         * appears in video memory.
         */
        boolean[] valid = new boolean[64];

        MemoryImageSource[] source = new MemoryImageSource[64];

        /**
         * Current magnification value of Gameboy screen
         */
        int[] imageData = new int[64];
        Component a;

        /**
         * Initialize a new Gameboy tile
         */
        GameboyTile(Component a) {
            allocateImage(TILE_BKG, a);
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
            for (int r = 0; r < 64; r++) {
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
        void updateImage(byte[] videoRam, int offset, int attribs) {
            int px, py;
            int rgbValue;

            if (image[attribs] == null) {
                allocateImage(attribs, a);
            }

            GameboyPalette pal;

            if ((attribs & TILE_OBJ_1) != 0) {
                pal = obj1Palette;
            } else if ((attribs & TILE_OBJ_2) != 0) {
                pal = obj2Palette;
            } else {
                pal = backgroundPalette;
            }

            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {

                    if ((attribs & TILE_FLIP_X) != 0) {
                        px = 7 - x;
                    } else {
                        px = x;
                    }
                    if ((attribs & TILE_FLIP_Y) != 0) {
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
            for (int r = 0; r < 64; r++) {
                valid[r] = false;
                if (image[r] != null) image[r].flush();
                image[r] = null;
            }
        }

    }

}
