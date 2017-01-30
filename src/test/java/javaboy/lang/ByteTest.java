package javaboy.lang;

import org.junit.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ByteTest {

    @Test
    public void nibbleTest() {
        Byte value = new Byte(0xAB);

        assertThat(value.lowerNibble(), is(0xB));
        assertThat(value.upperNibble(), is(0xA));

        int lowerNibble = value.lowerNibble();
        int upperNibble = value.upperNibble();

        value.upperNibble(lowerNibble);
        value.lowerNibble(upperNibble);

        assertThat(value.intValue(), is(0xBA));
    }

    @Test
    public void bitTest() {
        Byte value = new Byte(0b10101010);

        assertThat(value.getBit(7), is(Bit.ONE));
        assertThat(value.getBit(6), is(Bit.ZERO));
        assertThat(value.getBit(5), is(Bit.ONE));
        assertThat(value.getBit(4), is(Bit.ZERO));
        assertThat(value.getBit(3), is(Bit.ONE));
        assertThat(value.getBit(2), is(Bit.ZERO));
        assertThat(value.getBit(1), is(Bit.ONE));
        assertThat(value.getBit(0), is(Bit.ZERO));

        value.setBit(7, Bit.ZERO);
        value.setBit(6, Bit.ONE);
        value.setBit(5, Bit.ZERO);
        value.setBit(4, Bit.ONE);
        value.setBit(3, Bit.ZERO);
        value.setBit(2, Bit.ONE);
        value.setBit(1, Bit.ZERO);
        value.setBit(0, Bit.ONE);

        assertThat(value.intValue(), is(0b01010101));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void getBitOutOfRange() {
        Byte value = new Byte();

        value.getBit(24);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setBitOutOfRange() {
        Byte value = new Byte();

        value.setBit(42, Bit.ONE);
    }

    @Test
    public void swapTest() {
        Byte value = new Byte(0x76);
        value.swap();

        assertThat(value.intValue(),is(0x67));
    }

    @Test
    public void byteTest() {
        Byte value = new Byte((byte) 0x98);

        assertThat(value.upperNibble(), is(0x9));
        assertThat(value.lowerNibble(), is(0x8));
    }

    @Test
    public void constructorTest() {
        Byte value1 = new Byte(0x87);
        Byte value2 = new Byte(0x62);

        value1.setValue(value2);

        assertThat(value1.intValue(), is(0x62));
    }

}
