package enjuicer;

public class BlockClosure {
    private final Cell cell;
    private Object[] locals;
    private final int localsStart;
    private final int localsCount;

    public BlockClosure(Cell cell, Object[] locals, int localsStart, int localsCount) {
        this.cell = cell;
        this.locals = locals;
        this.localsStart = localsStart;
        this.localsCount = localsCount;
    }

    @Override
    public String toString() {
        return "{...}";
    }

    public Object value(Object[] arguments) {
        System.arraycopy(arguments, 0, locals, localsStart, localsCount);
        return cell.value(locals);
    }

    public void setLocals(Object[] locals) {
        this.locals = locals;
    }
}
