package javaboy;

import javaboy.lang.Byte;
import javaboy.lang.FlagRegister;
import javaboy.lang.Short;

/**
 * Created by gabrieloshiro on 2016-06-27.
 */
public class Registers {

    public Registers() {
    }

    private final Byte a = new Byte();
    private final Byte b = new Byte();
    private final Byte c = new Byte();
    private final Byte d = new Byte();
    private final Byte e = new Byte();
    private final Byte h = new Byte();
    private final Byte l = new Byte();

    private final FlagRegister f = new FlagRegister();

    private final Short pc = new Short();
    private final Short sp = new Short();

    public Byte a() {
        return a;
    }

    public void a(byte a) {
        this.a.setValue(a);
    }

    public Byte b() {
        return b;
    }

    public void b(byte b) {
        this.b.setValue(b);
    }

    public Byte c() {
        return c;
    }

    public void c(byte c) {
        this.c.setValue(c);
    }

    public Byte d() {
        return d;
    }

    public void d(byte d) {
        this.d.setValue(d);
    }

    public Byte e() {
        return e;
    }

    public void e(byte e) {
        this.e.setValue(e);
    }

    public FlagRegister f() {
        return f;
    }

    public void f(byte f) {
        this.f.setValue(f);
    }

    public Byte h() {
        return h;
    }

    public void h(byte h) {
        this.h.setValue(h);
    }

    public Byte l() {
        return l;
    }

    public void l(byte l) {
        this.l.setValue(l);
    }

    public void af(short i) {
        a.setValue(i >> 8);
        f.setValue(i & 0xFF);
    }

    public void bc(short i) {
        b.setValue(i >> 8);
        c.setValue(i & 0xFF);
    }

    public void de(short i) {
        d.setValue(i >> 8);
        e.setValue(i & 0xFF);
    }

    public void hl(short i) {
        h.setValue(i >> 8);
        l.setValue(i & 0xFF);
    }

    public Short pc() {
        return pc;
    }

    public void pc(short s) {
        this.pc.setValue(s);
    }

    public void pc(int i) {
        this.pc.setValue(i);
    }

    public Short sp() {
        return sp;
    }

    public void sp(short s) {
        this.sp.setValue(s);
    }

    public void sp(int i) {
        this.sp.setValue(i);
    }
}
