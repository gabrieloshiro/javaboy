package javaboy;

class InterruptController {

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