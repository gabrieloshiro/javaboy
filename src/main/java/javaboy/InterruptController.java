package javaboy;

import javaboy.lang.Byte;
import javaboy.lang.Short;

public class InterruptController implements ReadableWritable {

    public static final int FLAGS_ADDRESS = 0xFF0F;
    public static final int ENABLE_ADDRESS = 0xFFFF;

    private boolean masterInterruptEnable;

    private final Byte flags = new Byte();
    private final Byte enable = new Byte();

    public boolean isMasterInterruptEnable() {
        return masterInterruptEnable;
    }

    public void setMasterInterruptEnable(boolean masterInterruptEnable) {
        this.masterInterruptEnable = masterInterruptEnable;
    }

    @Override
    public Byte read(Short address) {
        switch (address.intValue()) {
            case FLAGS_ADDRESS:
                return flags;
            case ENABLE_ADDRESS:
                return enable;
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void write(Short address, Byte data) {
        switch (address.intValue()) {
            case FLAGS_ADDRESS:
                flags.setValue(data.intValue());
                break;
            case ENABLE_ADDRESS:
                enable.setValue(data.intValue());
                break;
            default:
                throw new IllegalArgumentException("Address [" + address.intValue() + "]");
        }
    }

    public enum Interrupt {
        VBLANK(0b0000_0001, 0x40),
        LCDC(0b0000_0010, 0x48),
        TIMA(0b0000_0100, 0x50),
        SERIAL(0b0000_1000, 0x58),
        JOYPAD(0b0001_0000, 0x60);

        private int mask;
        private int address;

        Interrupt(int mask, int address) {
            this.mask = mask;
            this.address = address;
        }

        public int getBitMask() {
            return mask;
        }

        public int getAddress() {
            return address;
        }
    }
}
