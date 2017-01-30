package javaboy.lang;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class BitTest {

    @Test
    public void toggleTest() {
        Bit zero = Bit.from(0);
        Bit one = Bit.from(1);

        assertThat(zero.toggle(), is(Bit.ONE));
        assertThat(one.toggle(), is(Bit.ZERO));
    }

    @Test
    public void booleanFactoryTest() {
        Bit zero = Bit.from(false);
        Bit one = Bit.from(true);

        assertThat(zero.intValue(), is(0));
        assertThat(one.intValue(), is(1));
    }

    @Test
    public void intFactoryTest() {
        Bit zero = Bit.from(0);
        Bit one = Bit.from(1);

        assertThat(zero.booleanValue(), is(false));
        assertThat(one.booleanValue(), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void intFactoryExceptionTest() {
        Bit error = Bit.from(69);
    }

}
