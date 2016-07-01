package javaboy.lang;

/**
 * Created by gabrieloshiro on 2016-06-28.
 */
public class ZeroFlag extends Bit {

    public void setIfZero(int i) {
        setValue(i == 0);
    }

    public void setIfZero(Bit b) {
        setValue(b.intValue() == 0);
    }

}
