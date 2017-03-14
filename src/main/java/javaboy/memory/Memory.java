package javaboy.memory;

import javaboy.ReadableWritable;
import javaboy.lang.Byte;
import javaboy.lang.Short;
import org.pmw.tinylog.Logger;

import java.util.ArrayList;
import java.util.Iterator;

public class Memory implements ReadableWritable, Iterable<Byte> {

    private final int firstAddress;
    private final int size;

    private final ArrayList<Byte> memory = new ArrayList<>();

    public Memory(int firstAddress, int size) {
        if (firstAddress < 0) {
            throw new IllegalArgumentException();
        }

        if (size < 1) {
            throw new IllegalArgumentException();
        }

        this.firstAddress = firstAddress;
        this.size = size;

        for (int i = firstAddress; i <= lastAddress(); i++) {
            final Byte data = new Byte();
            memory.add(data);
            Logger.debug("Creating empty memory position [" + i + "]");
        }
    }

    public Memory(int firstAddress, byte[] data) {
        if (firstAddress < 0) {
            throw new IllegalArgumentException();
        }

        if (data == null) {
            throw new IllegalArgumentException();
        }

        if (data.length < 1) {
            throw new IllegalArgumentException();
        }

        this.firstAddress = firstAddress;
        this.size = data.length;

        for (int i = 0; i < size; i++) {
            memory.add(new Byte(data[i]));
        }
    }

    public int firstAddress() {
        return firstAddress;
    }

    public int size() {
        return size;
    }

    public int lastAddress() {
        return firstAddress + size - 1;
    }

    @Override
    public Byte read(Short address) {
        return memory.get(normalizeAddress(address));
    }

    @Override
    public void write(Short address, Byte data) {
        int normalizedAddress = normalizeAddress(address);

        memory.set(normalizedAddress, data);
    }

    private int normalizeAddress(Short address) {
        int normalizedAddress = address.intValue() - firstAddress;

        if (normalizedAddress < 0) {
            throw new IllegalStateException("This memory starts at address " + firstAddress);
        }

        return normalizedAddress;
    }

    @Override
    public Iterator<Byte> iterator() {
        return new MemoryIterator();
    }


    private class MemoryIterator implements Iterator<Byte> {

        private final Short address;

        public MemoryIterator() {
            address = new Short(firstAddress);
        }

        @Override
        public boolean hasNext() {
            return address.intValue() <= lastAddress();
        }

        @Override
        public Byte next() {
            Byte next = read(address);
            address.inc();
            return next;
        }
    }
}
