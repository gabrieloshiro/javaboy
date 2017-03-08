package javaboy;

import javaboy.lang.Byte;
import javaboy.lang.Short;
import javaboy.memory.MemoryBank;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class MemoryBankTest {

    @Test
    public void testMemoryBankCreation() {
        MemoryBank memoryBank = new MemoryBank(0, 0x100, 2);

        memoryBank.setCurrentBank(0);
        for (int i = 0; i < 0x100; i++) {
            memoryBank.write(new Short(i), new Byte(i));
        }

        memoryBank.setCurrentBank(1);
        for (int i = 0x0; i < 0x100; i++) {
            memoryBank.write(new Short(i), new Byte(i + 1));
        }

        for (int i = 0; i < 0x100; i++) {
            memoryBank.setCurrentBank(1);
            assertThat(memoryBank.getCurrentBank(), is(1));
            assertThat(memoryBank.read(new Short(i)).intValue(), is((i + 1) & 0xFF));
            memoryBank.setCurrentBank(0);
            assertThat(memoryBank.getCurrentBank(), is(0));
            assertThat(memoryBank.read(new Short(i)).intValue(), is(i));
        }

    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeNumberOfBanks() {
        new MemoryBank(0, 0x1000, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfBoundsBank() {
        MemoryBank memoryBank = new MemoryBank(0, 0x10, 2);
        memoryBank.setCurrentBank(42);
    }


}