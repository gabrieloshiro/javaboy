package javaboy.memory;

import javaboy.IoHandler;
import javaboy.ReadableWritable;
import javaboy.Registers;
import javaboy.graphics.GraphicsChip;
import javaboy.lang.Byte;
import javaboy.lang.Short;
import javaboy.rom.loader.RomLoader;
import org.pmw.tinylog.Logger;

public class MemoryController implements ReadableWritable {

    private static final int ROM_SIZE = 0x8000;

    private final Memory rom;

    // 8Kb main system RAM appears at 0xC000 in address space
    // 32Kb for GBC
    private final byte[] mainRam = new byte[ROM_SIZE];

    // 256 bytes at top of RAM are used mainly for registers
    private final Memory oam = new Memory(0xFE00, 0x100);

    private final Registers registers;
    private final GraphicsChip graphicsChip;
    private final IoHandler ioHandler;

    public MemoryController(GraphicsChip graphicsChip, IoHandler ioHandler, Registers registers) {
        rom = RomLoader.loadRom("bgblogo.gb", ROM_SIZE);
        this.graphicsChip = graphicsChip;
        this.ioHandler = ioHandler;
        this.registers = registers;
    }

    @Override
    public Byte read(Short address) {
        switch ((address.intValue() & 0xF000)) {
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                return rom.read(address);

            case 0x8000:
            case 0x9000:
                return new Byte(graphicsChip.addressRead(address.intValue() - 0x8000));

            case 0xA000:
            case 0xB000:
                return rom.read(address);

            case 0xC000:
                return new Byte((mainRam[address.intValue() - 0xC000]));

            case 0xD000:
                return new Byte((mainRam[address.intValue() - 0xD000]));

            case 0xE000:
                return new Byte(mainRam[address.intValue() - 0xE000]);

            case 0xF000:
                if (address.intValue() < 0xFE00) {
                    return new Byte(mainRam[address.intValue() - 0xE000]);
                } else if (address.intValue() < 0xFF00) {
                    return oam.read(address);
                } else {
                    return new Byte(ioHandler.ioRead(address.intValue() - 0xFF00));
                }

            default:
                Logger.debug("Tried to read address " + address + ".  pc = " + String.format("%04X", registers.pc.intValue()));
                throw new IllegalStateException("");
        }
    }

    @Override
    public void write(Short address, Byte data) {
        switch (address.intValue() & 0xF000) {
            case 0x0000:
            case 0x1000:
            case 0x2000:
            case 0x3000:
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                break;

            case 0x8000:
            case 0x9000:
                graphicsChip.addressWrite(address.intValue() - 0x8000, (byte) data.intValue());
                break;

            case 0xA000:
            case 0xB000:
                break;

            case 0xC000:
                mainRam[address.intValue() - 0xC000] = (byte) data.intValue();
                break;

            case 0xD000:
                mainRam[address.intValue() - 0xD000] = (byte) data.intValue();
                break;

            case 0xE000:
                mainRam[address.intValue() - 0xE000] = (byte) data.intValue();
                break;

            case 0xF000:
                if (address.intValue() < 0xFE00) {
                    try {
                        mainRam[address.intValue() - 0xE000] = (byte) data.intValue();
                    } catch (ArrayIndexOutOfBoundsException e) {
                        Logger.debug("Address error: " + address + " pc = " + String.format("%04X", registers.pc.intValue()));
                    }
                } else if (address.intValue() < 0xFF00) {
                    oam.write(address, data);
                } else {
                    ioHandler.ioWrite(address.intValue() - 0xFF00, (short) data.intValue());
                }
                break;
        }

    }
}
