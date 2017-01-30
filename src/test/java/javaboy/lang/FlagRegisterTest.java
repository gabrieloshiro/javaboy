package javaboy.lang;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class FlagRegisterTest {

    @Test
    public void flagsTest() {
        FlagRegister flags = new FlagRegister();

        flags.zf(Bit.ONE);
        flags.nf(Bit.ZERO);
        flags.hf(Bit.ONE);
        flags.cf(Bit.ZERO);

        assertThat(flags.intValue(), is(0b1010_0000));

        flags.zf(Bit.ZERO);
        flags.nf(Bit.ONE);
        flags.hf(Bit.ZERO);
        flags.cf(Bit.ONE);

        assertThat(flags.intValue(), is(0b0101_0000));
    }

    @Test
    public void zeroFlagTest() {
        FlagRegister flags = new FlagRegister();
        flags.setValue(0b0101_1111);

        flags.setZeroFlagForResult(0x00);
        assertThat(flags.intValue(), is(0b1101_0000));

        flags.setZeroFlagForResult(0x99);
        assertThat(flags.intValue(), is(0b0101_0000));
    }

    @Test
    public void getterTest() {
        FlagRegister flags = new FlagRegister();
        flags.setValue(0b0101_0000);

        assertThat(flags.zf(), is(Bit.ZERO));
        assertThat(flags.nf(), is(Bit.ONE));
        assertThat(flags.hf(), is(Bit.ZERO));
        assertThat(flags.cf(), is(Bit.ONE));

        flags.setValue(0b1010_0000);

        assertThat(flags.zf(), is(Bit.ONE));
        assertThat(flags.nf(), is(Bit.ZERO));
        assertThat(flags.hf(), is(Bit.ONE));
        assertThat(flags.cf(), is(Bit.ZERO));
    }
}
