package javaboy.memory;

import javaboy.InterruptController;
import javaboy.IoHandler;
import javaboy.ReadableWritable;
import javaboy.Registers;
import javaboy.graphics.GraphicsChip;
import javaboy.lang.Byte;
import javaboy.lang.Short;
import javaboy.rom.loader.RomLoader;
import org.pmw.tinylog.Logger;

/**
 * ┌─────────────────────────┐ 0x0000
 * │                         │
 * │    Interrupt Address    │
 * │       RST Address       │
 * │                         │
 * ├─────────────────────────┤ 0x0100
 * │                         │
 * │    Cartridge Header     │
 * │      ROM Data area      │
 * │                         │
 * ├─────────────────────────┤ 0x0150 <-- Program start address
 * │                         │
 * │    User program area    │
 * │         32 kB           │
 * │                         │
 * ├─────────────────────────┤ 0x8000
 * │                         │
 * │       Video Ram         │
 * │                         │
 * │                         │
 * ├─────────────────────────┤ 0xA000
 * │                         │
 * │    External Expansion   │
 * │      Working RAM        │
 * │                         │
 * ├─────────────────────────┤ 0xC000
 * │                         │
 * │      Working RAM        │
 * │                         │
 * │                         │
 * ├─────────────────────────┤ 0xE000
 * │XXXXXXXXXXXXXXXXXXXXXXXXX│
 * │XXXXX Prohibited XXXXXXXX│
 * │XXXXXXXXXXXXXXXXXXXXXXXXX│
 * │XXXXXXXXXXXXXXXXXXXXXXXXX│
 * ├─────────────────────────┤ 0xFE00
 * │                         │
 * │     OAM (40 Objects)    │
 * │       40 x 32 bits      │
 * │                         │
 * ├─────────────────────────┤ 0xFEA0
 * │XXXXXXXXXXXXXXXXXXXXXXXXX│
 * │XXXXX Prohibited XXXXXXXX│
 * │XXXXXXXXXXXXXXXXXXXXXXXXX│
 * │XXXXXXXXXXXXXXXXXXXXXXXXX│
 * ├─────────────────────────┤ 0xFF00
 * │                         │
 * │   Port/Mode Registers   │
 * │    Control Registers    │
 * │     Sound Registers     │
 * │                         │
 * ├─────────────────────────┤ 0xFF80
 * │                         │
 * │       Stack RAM         │
 * │                         │
 * │                         │
 * ├─────────────────────────┤ 0xFFFE
 * │                         │
 * │    Interrupt Enable     │
 * │       Register          │
 * │                         │
 * └─────────────────────────┘ 0xFFFF
 */
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
    private final InterruptController interruptController;

    public MemoryController(GraphicsChip graphicsChip, IoHandler ioHandler, Registers registers, InterruptController interruptController) {
        rom = RomLoader.loadRom("bgblogo.gb", ROM_SIZE);
        this.graphicsChip = graphicsChip;
        this.ioHandler = ioHandler;
        this.registers = registers;
        this.interruptController = interruptController;
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
                if (address.intValue() == InterruptController.FLAGS_ADDRESS || address.intValue() == InterruptController.ENABLE_ADDRESS) {
                    return interruptController.read(address);
                }

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
                if (address.intValue() == InterruptController.FLAGS_ADDRESS || address.intValue() == InterruptController.ENABLE_ADDRESS) {
                    interruptController.write(address, data);
                    break;
                }

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
