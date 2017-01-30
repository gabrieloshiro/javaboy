package javaboy.lang;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ShortTest {

    @Test
    public void byteConstructorTest() {
        Byte upper = new Byte(0xAB);
        Byte lower = new Byte(0xCD);

        Short value = new Short(upper, lower);

        assertThat(value.intValue(), is(0xABCD));
    }

    @Test
    public void byteGetterTest() {
        Short value = new Short(0x9876);

        assertThat(value.getUpperByte().intValue(), is(0x98));
        assertThat(value.getLowerByte().intValue(), is(0x76));
    }

    @Test
    public void signedShortFromByteTest() {
        Byte positive = new Byte(32);

        Short value = Short.signedShortFromByte(positive);
        assertThat(value.intValue(), is(32));

        Byte negative = new Byte(0x8F);

        value = Short.signedShortFromByte(negative);
        assertThat(value.intValue(), is(0xFF8F));
    }

    @Test
    public void incTest() {
        Short value = new Short(0x000F);
        value.inc();
        assertThat(value.intValue(), is(0x0010));

        value.setValue(0xFFFF);
        value.inc();
        assertThat(value.intValue(), is(0x0000));

        value.setValue(0x0FFF);
        value.inc();
        assertThat(value.intValue(), is(0x1000));
    }

    @Test
    public void decTest() {
        Short value = new Short();
        value.dec();
        assertThat(value.intValue(), is(0xFFFF));

        value.setValue(0xF000);
        value.dec();
        assertThat(value.intValue(), is(0xEFFF));

        value.setValue(0xFF00);
        value.dec();
        assertThat(value.intValue(), is(0xFEFF));
    }

}
