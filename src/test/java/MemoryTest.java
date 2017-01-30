import javaboy.Memory;
import javaboy.lang.Byte;
import javaboy.lang.Short;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class MemoryTest {

    @Test
    public void basicTest() {
        Memory memory = new Memory(0, 10);

        assertThat(memory.firstAddress(), is(0));
        assertThat(memory.size(), is(10));
        assertThat(memory.lastAddress(), is(9));

        int index = 0;
        for (Byte data : memory) {
            data.setValue(index);
            index++;
        }

        index = 0;
        for (Byte data : memory) {
            assertThat(data.intValue(), is(index));
            index++;
        }
    }

    @Test
    public void shiftedTest() {
        Memory memory = new Memory(20000, 100);

        assertThat(memory.firstAddress(), is(20000));
        assertThat(memory.size(), is(100));
        assertThat(memory.lastAddress(), is(20099));

        int index = 0;
        for (Byte data : memory) {
            data.setValue(index);
            index++;
        }

        index = 0;
        for (Byte data : memory) {
            assertThat(data.intValue(), is(index));
            index++;
        }
    }

    @Test
    public void readWriteTest() {
        Memory memory = new Memory(100, 10);

        assertThat(memory.firstAddress(), is(100));
        assertThat(memory.size(), is(10));
        assertThat(memory.lastAddress(), is(109));

        for (int i = 100; i < 110; i++) {
            memory.write(new Short(i), new Byte(i));
        }

        for (int i = 100; i < 110; i++) {
            assertThat(memory.read(new Short(i)).intValue(), is(i));
        }
    }

}
