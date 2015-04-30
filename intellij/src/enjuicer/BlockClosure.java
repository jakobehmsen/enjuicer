package enjuicer;

import java.util.function.Function;

public class BlockClosure {
    //private final Cell cell;
    private final Function<Object[], Object> body;
    private Object[] locals;
    private final int localsStart;
    private final int localsCount;

    public BlockClosure(Function<Object[], Object> body, Object[] locals, int localsStart, int localsCount) {
        this.body = body;
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
        //return cell.value(locals);
        return body.apply(arguments);
    }

    public void setLocals(Object[] locals) {
        this.locals = locals;
    }
}
