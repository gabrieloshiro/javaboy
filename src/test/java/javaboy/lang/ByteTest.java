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
    }

    /**
     * Method: inc()
     */
    @Test
    public void testInc() throws Exception {
        //TODO: Test goes here...
    }

    /**
     * Method: dec()
     */
    @Test
    public void testDec() throws Exception {
        //TODO: Test goes here...
    }

    /**
     * Method: getLowerNibble()
     */
    @Test
    public void testGetLowerNibble() throws Exception {
        //TODO: Test goes here...
    }

    /**
     * Method: setLowerNibble(int i)
     */
    @Test
    public void testSetLowerNibble() throws Exception {
        //TODO: Test goes here...
    }

    /**
     * Method: getHigherNibble()
     */
    @Test
    public void testGetHigherNibble() throws Exception {
        //TODO: Test goes here...
    }

    /**
     * Method: setHigherNibble(int i)
     */
    @Test
    public void testSetHigherNibble() throws Exception {
        //TODO: Test goes here...
    }

    /**
     * Method: swap()
     */
    @Test
    public void testSwap() throws Exception {
        //TODO: Test goes here...
    }

    /**
     * Method: getBit(int index)
     */
    @Test
    public void testGetBit() throws Exception {
        //TODO: Test goes here...
    }

    /**
     * Method: setBit(int index)
     */
    @Test
    public void testSetBit() throws Exception {
        //TODO: Test goes here...
    }


} 
