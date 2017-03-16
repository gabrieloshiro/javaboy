package javaboy.instruction;

import javaboy.lang.Byte;

public class Opcode {

    Instruction instruction;

    public Instruction from(int value, boolean extended) {
        if (extended) {
            return ExtendedOpcode.from(value);
        }
        return BaseOpcode.from(value);
    }

    public Byte byteValue() {
        return instruction.byteValue();
    }

    public int intValue() {
        return instruction.intValue();
    }

}
