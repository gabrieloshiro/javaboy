package javaboy;

import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;

class Registers {

    public final Byte a = new Byte();
    public final FlagRegister f = new FlagRegister();
    public final Short af = new Short(a, f);

    public final Byte b = new Byte();
    public final Byte c = new Byte();
    public final Short bc = new Short(b, c);

    public final Byte d = new Byte();
    public final Byte e = new Byte();
    public final Short de = new Short(d, e);

    public final Byte h = new Byte();
    public final Byte l = new Byte();
    public final Short hl = new Short(h, l);

    public final Short pc = new Short();
    public final Short sp = new Short();

    private final ReadableWritable memory;

    public Registers(ReadableWritable memory) {
        this.memory = memory;
    }

    public int registerRead(int regNum) {
        switch (regNum) {
            case 0:
                return b.intValue();
            case 1:
                return c.intValue();
            case 2:
                return d.intValue();
            case 3:
                return e.intValue();
            case 4:
                return h.intValue();
            case 5:
                return l.intValue();
            case 6:
                return memory.read(hl).intValue();
            case 7:
                return a.intValue();
            default:
                return -1;
        }
    }

    public void registerWrite(int regNum, int data) {
        switch (regNum) {
            case 0:
                b.setValue(data);
                break;
            case 1:
                c.setValue(data);
                break;
            case 2:
                d.setValue(data);
                break;
            case 3:
                e.setValue(data);
                break;
            case 4:
                h.setValue(data);
                break;
            case 5:
                l.setValue(data);
                break;
            case 6:
                memory.write(hl, new Byte(data));
                break;
            case 7:
                a.setValue(data);
                break;
            default:
                break;
        }
    }


}
