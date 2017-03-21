package javaboy;

import javaboy.instruction.BaseOpcode;
import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;

public class Registers {

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

    public int registerRead(Register register) {
        switch (register) {
            case B:
                return b.intValue();
            case C:
                return c.intValue();
            case D:
                return d.intValue();
            case E:
                return e.intValue();
            case H:
                return h.intValue();
            case L:
                return l.intValue();
            case MEM:
                return memory.read(hl).intValue();
            case A:
                return a.intValue();
            default:
                throw new IllegalArgumentException();
        }
    }

    public void registerWrite(Register register, int data) {
        switch (register) {
            case B:
                b.setValue(data);
                break;
            case C:
                c.setValue(data);
                break;
            case D:
                d.setValue(data);
                break;
            case E:
                e.setValue(data);
                break;
            case H:
                h.setValue(data);
                break;
            case L:
                l.setValue(data);
                break;
            case MEM:
                memory.write(hl, new Byte(data));
                break;
            case A:
                a.setValue(data);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public enum Register {
        A(7),
        B(0),
        C(1),
        D(2),
        E(3),
        H(4),
        L(5),
        MEM(6);

        private int index;

        Register(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        public static Register from(int index) {
            for (Register item: Register.values()) {
                if (index == item.getIndex()) {
                    return item;
                }
            }
            throw new IllegalArgumentException();
        }
    }

}
