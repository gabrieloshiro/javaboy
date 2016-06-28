package javaboy;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Registers {

    private Byte b, c;

    public Byte b() {
        return b;
    }

    public void b(int b) {
        this.b.setValue(b);
    }

    public Byte c() {
        return c;
    }

    public void c(int c) {
        this.c.setValue(c);
    }

    public Short bc() {
        return new Short(b, c);
    }

    public void bc(Short value) {
        b = value.getHigherByte();
        c = value.getLowerByte();
    }

    public void bc(int i) {
        bc(new Short(i));
    }
}
