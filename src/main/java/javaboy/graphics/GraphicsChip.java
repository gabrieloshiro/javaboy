package javaboy.graphics;

import javaboy.Cpu;
import javaboy.Shorts;
import javaboy.lang.Short;

import java.awt.*;
public class GraphicsChip {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 144;

    /**
     * Tile uses the background palette
     */
    public static final int TILE_BACKGROUND = 0;

    /**
     * Tile uses the first sprite palette
     */
    public static final int TILE_OBJECT_1 = 4;

    /**
     * Tile uses the second sprite palette
     */
    public static final int TILE_OBJECT_2 = 8;

    /**
     * Tile is flipped horizontally
     */
    public static final int TILE_FLIP_X = 1;

    /**
     * Tile is flipped vertically
     */
    public static final int TILE_FLIP_Y = 2;

    /**
     * The current contents of the video memory, mapped in at 0x8000 - 0x9FFF
     */
    private final byte[] videoRam = new byte[0x8000];

    public final GameboyPalette backgroundPalette;
    public final GameboyPalette obj1Palette;
    public final GameboyPalette obj2Palette;

    public boolean spritesEnabled = true;
    public boolean bgEnabled = true;
    public boolean winEnabled = true;

    /**
     * The image containing the Gameboy screen
     */
    private final Image backBuffer;

    /**
     * The current frame skip value
     */
    private final int frameSkip = 2;

    /**
     * The number of frames that have been drawn so far in the current frame sampling period
     */
    private int framesDrawn = 0;

    /**
     * Amount of time to wait between frames (ms)
     */
    public int frameWaitTime = 0;

    /**
     * The current frame has finished drawing
     */
    public boolean frameDone = false;
    public long startTime = 0;

    /**
     * Selection of one of two addresses for the BG and Window tile data areas
     */
    public boolean bgWindowDataSelect = true;

    /**
     * If true, 8x16 sprites are being used.  Otherwise, 8x8.
     */
    public boolean doubledSprites = false;

    /**
     * Selection of one of two address for the BG tile map.
     */
    public boolean hiBgTileMapAddress = false;
    private final Cpu cpu;
    private final int vidRamStart = 0;

    /**
     * Tile cache
     */
    private final GameboyTile[] tiles = new GameboyTile[384 * 2];

    // Hacks to allow some raster effects to work.  Or at least not to break as badly.
    private boolean savedWindowDataSelect = false;

    private boolean windowEnableThisLine = false;
    private int windowStopLine = GraphicsChip.HEIGHT;

    public GraphicsChip(Component a, Cpu d) {
        cpu = d;

        backgroundPalette = new GameboyPalette(0, 1, 2, 3);
        obj1Palette = new GameboyPalette(0, 1, 2, 3);
        obj2Palette = new GameboyPalette(0, 1, 2, 3);

        backBuffer = a.createImage(GraphicsChip.WIDTH, GraphicsChip.HEIGHT);

        for (int r = 0; r < 384 * 2; r++) {
            tiles[r] = new GameboyTile(this, a);
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
    public void dispose() {
        for (int r = 0; r < 384 * 2; r++) {
            if (tiles[r] != null) tiles[r].dispose();
        }
    }

    /**
     * Reads data from the specified video RAM address
     */
    public short addressRead(int address) {
        return videoRam[address + vidRamStart];
    }

    /**
     * Writes data to the specified video RAM address
     */
    public void addressWrite(int address, byte data) {
        if (address < 0x1800) {   // Bkg Tile data area
            int tileStart = 0;
            tiles[(address >> 4) + tileStart].invalidate();
            videoRam[address + vidRamStart] = data;
        } else {
            videoRam[address + vidRamStart] = data;
        }
    }

    /**
     * Invalidates all tiles in the tile cache that have the given attributes.
     * These will be regenerated next time they are drawn.
     */
    public void invalidateAll(int attribs) {
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
            int spriteX = cpu.read(new Short(0xFE01 + (i * 4))).intValue() - 8;
            int spriteY = cpu.read(new Short(0xFE00 + (i * 4))).intValue() - 16;
            int tileNum = cpu.read(new Short(0xFE02 + (i * 4))).intValue();
            int attributes = cpu.read(new Short(0xFE03 + (i * 4))).intValue();

            if ((attributes & 0x80) >> 7 == priority) {

                int spriteAttrib = 0;

                if (doubledSprites) {
                    tileNum &= 0xFE;
                }

                vidRamAddress = tileNum << 4;
                if ((attributes & 0x10) != 0) {
                    spriteAttrib |= TILE_OBJECT_2;
                } else {
                    spriteAttrib |= TILE_OBJECT_1;
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
    public void notifyScanline(int line) {

        if ((framesDrawn % frameSkip) != 0) {
            return;
        }

        if (line == 0) {
            clearFrameBuffer();
            drawSprites(backBuffer.getGraphics(), 1);
            windowStopLine = GraphicsChip.HEIGHT;
            windowEnableThisLine = winEnabled;
        }

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
        if (line == cpu.ioHandler.read(new Short(0xFF4A)).intValue() + 1) {        // Compare against WY reg
            savedWindowDataSelect = bgWindowDataSelect;
        }

        int xPixelOfs = cpu.ioHandler.read(new Short(0xFF43)).intValue() % 8;
        int yPixelOfs = cpu.ioHandler.read(new Short(0xFF42)).intValue() % 8;

        if (((yPixelOfs + line) % 8 == 4) || (line == 0)) {

            if ((line >= GraphicsChip.HEIGHT) && (line < 152)) notifyScanline(line + 8);

            Graphics back = backBuffer.getGraphics();

            int xTileOfs = cpu.ioHandler.read(new Short(0xFF43)).intValue() / 8;
            int yTileOfs = cpu.ioHandler.read(new Short(0xFF42)).intValue() / 8;
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

                    tileNum = Shorts.unsigned(videoRam[tileNumAddress]);
                } else {
                    tileNumAddress = bgStartAddress +
                            (((y + yTileOfs) % 32) * 32) + ((x + xTileOfs) % 32);

                    tileNum = 256 + videoRam[tileNumAddress];
                }

                int attribs;

                vidMemAddr = (tileNum << 4);
                attribs = TILE_BACKGROUND;

                if (tiles[tileNum].invalid(attribs)) {
                    tiles[tileNum].validate(videoRam, vidMemAddr, attribs);
                }
                tiles[tileNum].draw(back, (8 * x) - xPixelOfs, (8 * y) - yPixelOfs, attribs);
            }
        }
    }

    /**
     * Clears the frame buffer to the background colour
     */
    private void clearFrameBuffer() {
        Graphics back = backBuffer.getGraphics();
        back.setColor(new Color(backgroundPalette.getRgbEntry(0)));
        back.fillRect(0, 0, GraphicsChip.WIDTH, GraphicsChip.HEIGHT);
    }

    /**
     * Draw the current graphics frame into the given graphics context
     */
    public boolean draw(Graphics graphics, int startX, int startY) {
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

            if ((cpu.ioHandler.read(new Short(0xFF40)).intValue() & 0x40) != 0) {
                windowStartAddress = 0x1C00;
            } else {
                windowStartAddress = 0x1800;
            }
            wx = cpu.ioHandler.read(new Short(0xFF4B)).intValue() - 7;
            wy = cpu.ioHandler.read(new Short(0xFF4A)).intValue();

            back.setColor(new Color(backgroundPalette.getRgbEntry(0)));
            back.fillRect(wx, wy, GraphicsChip.WIDTH, GraphicsChip.HEIGHT);

            int tileAddress;
            int attribs, tileDataAddress;

            for (int y = 0; y < 19 - (wy / 8); y++) {
                for (int x = 0; x < 21 - (wx / 8); x++) {
                    tileAddress = windowStartAddress + (y * 32) + x;

                    if (!savedWindowDataSelect) {
                        tileNum = 256 + videoRam[tileAddress];
                    } else {
                        tileNum = Shorts.unsigned(videoRam[tileAddress]);
                    }
                    tileDataAddress = tileNum << 4;

                    attribs = TILE_BACKGROUND;

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

        graphics.drawImage(backBuffer, startX, startY, null);

        frameDone = true;
        return true;
    }


}
