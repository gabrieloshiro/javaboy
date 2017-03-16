package javaboy.instruction;

import javaboy.lang.Byte;

public class Instruction {

    private Opcode opcode;

    public Opcode from(int value, boolean extended) {
        if (extended) {
            return ExtendedOpcode.from(value);
        }
        return BaseOpcode.from(value);
    }

    public Byte byteValue() {
        return opcode.byteValue();
    }

    public int intValue() {
        return opcode.intValue();
    }

}
