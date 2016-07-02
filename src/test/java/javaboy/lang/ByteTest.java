package javaboy.lang;

import org.junit.*;
import org.pmw.tinylog.Logger;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Byte Tester.
 *
 * @author Chaotic Gabibo <ChaoticGabibo@gmail.com>
 * @version 1.0
 * @since <pre>Jul 1, 2016</pre>
 */
public class ByteTest {

    private static Byte b;
    private static int model;

    @BeforeClass
    public static void beforeClass() throws Exception {
        b = new Byte();
        model = 0;

        Logger.debug("Reset with 0");
        assertEquals(model, b.intValue());

        model = new Random().nextInt();
        b.setValue(model);
        model = model & 0xFF;
        Logger.debug("Init with random value " + model);
        assertEquals(model, b.intValue());
    }

    @AfterClass
    public static void afterClass() throws Exception {
        b = null;
    }

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
        printValues();
    }

    @Test
    public void testInc() throws Exception {
        Logger.debug("Increment");
        b.inc();
        model = (model + 1) & 0xFF;
        assertEquals(model, b.intValue());
    }

    @Test
    public void testDec() throws Exception {
        Logger.debug("Decrement");
        b.dec();
        model = (model - 1) & 0xFF;
        assertEquals(model, b.intValue());
    }

    @Test
    public void testSetLowerNibble() throws Exception {
        Logger.debug("Set Lower Nibble");
        int nibble = new Random().nextInt();
        b.setLowerNibble(nibble);

        model = (model & 0xF0) | (nibble & 0x0F);
        assertEquals(model & 0xF, b.getLowerNibble());
    }

    @Test
    public void testSetHigherNibble() throws Exception {
        Logger.debug("Set Higher Nibble");
        int nibble = new Random().nextInt();
        b.setHigherNibble(nibble);

        model = (model & 0x0F) | ((nibble & 0x0F) << 4);
        assertEquals(model >> 4, b.getHigherNibble());
    }

    @Test
    public void testSwap() throws Exception {
        Logger.debug("Swap");
        b.swap();

        model = ((model << 4) & 0xF0) | (model >> 4);
        assertEquals(model, b.intValue());
    }

    @Test
    public void testSetBit() throws Exception {
        int bit0 = new Random().nextInt() & 1;
        int bit1 = new Random().nextInt() & 1;
        int bit2 = new Random().nextInt() & 1;
        int bit3 = new Random().nextInt() & 1;
        int bit4 = new Random().nextInt() & 1;
        int bit5 = new Random().nextInt() & 1;
        int bit6 = new Random().nextInt() & 1;
        int bit7 = new Random().nextInt() & 1;

        b.setBit(0, bit0);
        b.setBit(1, bit1);
        b.setBit(2, bit2);
        b.setBit(3, bit3);
        b.setBit(4, bit4);
        b.setBit(5, bit5);
        b.setBit(6, bit6);
        b.setBit(7, bit7);

        model = bit7 << 7 |
                bit6 << 6 |
                bit5 << 5 |
                bit4 << 4 |
                bit3 << 3 |
                bit2 << 2 |
                bit1 << 1 |
                bit0;

        assertEquals((model >> 7) & 1, b.getBit(7).intValue());
        assertEquals((model >> 6) & 1, b.getBit(6).intValue());
        assertEquals((model >> 5) & 1, b.getBit(5).intValue());
        assertEquals((model >> 4) & 1, b.getBit(4).intValue());
        assertEquals((model >> 3) & 1, b.getBit(3).intValue());
        assertEquals((model >> 2) & 1, b.getBit(2).intValue());
        assertEquals((model >> 1) & 1, b.getBit(1).intValue());
        assertEquals((model >> 0) & 1, b.getBit(0).intValue());
    }

    private void printValues() {
        Logger.debug("\n     HEX                 DEC\n" +
                "ACTUAL EXPECTED     ACTUAL EXPECTED\n" +
                "  " + toHexString(b.intValue()) + "       " + toHexString(model) + "        " + toDecString(b.intValue()) + "      " + toDecString(model) + "\n"
        );
    }

    private String toHexString(int i) {
        return String.format("%02X", i);
    }

    private String toDecString(int i) {
        return String.format("%03d", (byte)i);
    }

} 
