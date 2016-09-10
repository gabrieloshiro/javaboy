package javaboy;

import javaboy.lang.Byte;
import javaboy.lang.Short;

import java.util.ArrayList;

public class Memory implements Readable, Writable {

    private final int firstAddress;
    private final int size;

    private ArrayList<Byte> memory = new ArrayList<>();

    public Memory(int firstAddress, int size) {
        if (firstAddress < 0) {
            throw new IllegalArgumentException();
        }

        if (size < 1) {
            throw new IllegalArgumentException();
        }

        this.firstAddress = firstAddress;
        this.size = size;

        int lastAddress = getLastAddress();
        for (int i = firstAddress; i < lastAddress; i++) {
            Byte data = new Byte();
            memory.add(data);
        }
    }

    public int getFirstAddress() {
        return firstAddress;
    }

    public int getSize() {
        return size;
    }

    public int getLastAddress() {
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
}
