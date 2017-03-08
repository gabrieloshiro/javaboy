package javaboy;

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

}
