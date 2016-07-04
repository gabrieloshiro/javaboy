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

        pc = new Short();
        sp = new Short();
    }

    private Byte a, b, c, d, e, h, l;

    private FlagRegister f;

    private Short pc, sp;

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

    public FlagRegister f() {
        return f;
    }

    public void f(int f) {
        this.f.setValue(f);
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

    public void af(int i) {
        a.setValue(i >> 8);
        f.setValue(i & 0xFF);
    }

    public void bc(int i) {
        b.setValue(i >> 8);
        c.setValue(i & 0xFF);
    }

    public void de(int i) {
        d.setValue(i >> 8);
        e.setValue(i & 0xFF);
    }

    public void hl(int i) {
        h.setValue(i >> 8);
        l.setValue(i & 0xFF);
    }

    public Short pc() {
        return pc;
    }

    public void pc(int i) {
        this.pc.setValue(i);
    }

    public Short sp() {
        return sp;
    }

    public void sp(int i) {
        this.sp.setValue(i);
    }
}
