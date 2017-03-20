package javaboy;

class InterruptController {


    public enum Interrupt {
        VBLANK(0b0000_0001),
        LCDC(0b0000_0010),
        TIMA(0b0000_0100),
        SERIAL(0b0000_1000),
        JOYPAD(0b0001_0000);

        private int mask;

        Interrupt(int mask) {
            this.mask = mask;
        }

        public int getBitMask() {
            return mask;
        }
    }

}