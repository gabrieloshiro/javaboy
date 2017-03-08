package javaboy.memory;

import javaboy.Readable;
import javaboy.Writable;
import javaboy.lang.Byte;
import javaboy.lang.Short;

import java.util.ArrayList;

public class MemoryBank implements Readable, Writable {

    private final int numberOfBanks;
    private int currentBank;

    private ArrayList<Memory> memoryBank = new ArrayList<>();

    public MemoryBank(int firstAddress, int memorySize, int numberOfBanks) {
        if (numberOfBanks < 1) {
            throw new IllegalArgumentException();
        }

        this.numberOfBanks = numberOfBanks;

        for (int i = 0; i < numberOfBanks; i++) {
            Memory memory = new Memory(firstAddress, memorySize);
            memoryBank.add(memory);
        }
    }

    public int getCurrentBank() {
        return currentBank;
    }

    public void setCurrentBank(int currentBank) {
        if (currentBank > numberOfBanks || currentBank < 0) {
            throw new IllegalArgumentException("");
        }

        this.currentBank = currentBank;
    }

    @Override
    public Byte read(Short address) {
        return memoryBank.get(currentBank).read(address);
    }

    @Override
    public void write(Short address, Byte data) {
        memoryBank.get(currentBank).write(address, data);
    }
}
