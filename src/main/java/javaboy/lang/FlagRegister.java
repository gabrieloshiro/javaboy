package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-28.
 */
public class FlagRegister {

    private final ZeroFlag zf = new ZeroFlag();
    private final Bit nf = new Bit();
    private final Bit hf = new Bit();
    private final Bit cf = new Bit();

    public FlagRegister() {
        reset();
    }

    public Byte byteValue() {
        return new Byte(intValue());
    }

    public int intValue() {
        return (zf.intValue() << 7) |
                (nf.intValue() << 6) |
                (hf.intValue() << 5) |
                (cf.intValue() << 4);
    }

    public void setValue(Byte b) {
        zf.setValue(b.getBit(7).intValue());
        nf.setValue(b.getBit(6).intValue());
        hf.setValue(b.getBit(5).intValue());
        cf.setValue(b.getBit(4).intValue());
    }

    public void setValue(int i) {
        zf.setValue(i >> 7);
        nf.setValue(i >> 6);
        hf.setValue(i >> 5);
        cf.setValue(i >> 4);
    }

    public void reset() {
        zf.setValue(0);
        nf.setValue(0);
        hf.setValue(0);
        cf.setValue(0);
    }

    public Bit zf() {
        return zf;
    }

    public void zf(int i) {
        zf.setValue(i);
    }

    public Bit nf() {
        return nf;
    }

    public void nf(int i) {
        nf.setValue(i);
    }

    public Bit hf() {
        return hf;
    }

    public void hf(int i) {
        hf.setValue(i);
    }

    public Bit cf() {
        return cf;
    }

    public void cf(int i) {
        cf.setValue(i);
    }

}

