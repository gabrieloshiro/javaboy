package javaboy.instruction;

import javaboy.lang.Byte;

public class Instruction {

    public static Opcode from(int value, boolean isExtendedOpcode) {
        if (isExtendedOpcode) {
            return ExtendedOpcode.from(value);
        }
        return BaseOpcode.from(value);
    }

    public static Opcode from(Byte value, boolean isExtendedOpcode) {
        return from(value.intValue(), isExtendedOpcode);
    }

}
