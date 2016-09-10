package javaboy;

import javaboy.lang.Byte;
import javaboy.lang.Short;

import java.util.ArrayList;

public class MemoryBank implements Readable, Writable {

    private final int numberOfBanks;
    private final int firstAddress;
    private final int memorySize;

    private ArrayList<Memory> memoryBank = new ArrayList<>();

    public MemoryBank(int firstAddress, int memorySize, int numberOfBanks) {
        if (numberOfBanks < 1) {
            throw new IllegalArgumentException();
        }

        this.firstAddress = firstAddress;
        this.memorySize = memorySize;
        this.numberOfBanks = numberOfBanks;

        for (int i = 0; i < numberOfBanks; i++) {
            Memory memory = new Memory(firstAddress, memorySize);
            memoryBank.add(memory);
        }
    }

    public int getFirstAddress() {
        return firstAddress;
    }

    public int getMemorySize() {
        return memorySize;
    }

    public int getNumberOfBanks() {
        return numberOfBanks;
    }

    @Override
    public Byte read(Short address) {
        return null;
    }

    @Override
    public void write(Short address, Byte data) {
    }
}
