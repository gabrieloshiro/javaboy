package javaboy;

class InstructionCounter {

    /**
     * The number of instructions that have been executed since the
     * last reset
     */
    private int count = 0;

    public InstructionCounter() {
        reset();
    }

    private void reset() {
        count = 0;
    }


    public int getCount() {
        return count;
    }


    public void inc() {
        count++;
    }
}
