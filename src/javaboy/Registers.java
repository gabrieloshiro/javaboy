package javaboy;

import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Registers {

    public Registers() {
        a = new Byte();
        b = new Byte();
        c = new Byte();
        d = new Byte();
        e = new Byte();
        h = new Byte();
        l = new Byte();

        f = new FlagRegister();
    }

    private Byte a, b, c, d, e, h, l;

    private FlagRegister f;

    public Byte a() {
        return a;
    }

    public void a(int a) {
        this.a.setValue(a);
    }

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

    public Byte d() {
        return d;
    }

    public void d(int d) {
        this.d.setValue(d);
    }

    public Byte e() {
        return e;
    }

    public void e(int e) {
        this.e.setValue(e);
    }

    public Byte h() {
        return h;
    }

    public void h(int h) {
        this.h.setValue(h);
    }

    public Byte l() {
        return l;
    }

    public void l(int l) {
        this.l.setValue(l);
    }

    public Short af() {
        return new Short(a, f.byteValue());
    }

    public void af(Short value) {
        a.setValue(value.getHigherByte().intValue());
        f.setValue(value.getLowerByte().intValue());
    }

    public void af(int i) {
        af(new Short(i));
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

    public Short de() {
        return new Short(d, e);
    }

    public void de(Short value) {
        d = value.getHigherByte();
        e = value.getLowerByte();
    }

    public void de(int i) {
        de(new Short(i));
    }

    public Short hl() {
        return new Short(h, l);
    }

    public void hl(Short value) {
        h = value.getHigherByte();
        l = value.getLowerByte();
    }

    public void hl(int i) {
        hl(new Short(i));
    }

}
