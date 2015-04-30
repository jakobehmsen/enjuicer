package enjuicer;

import java.util.function.Function;

public class FunctionCellInfo {
    public final int localCount;
    public final Cell<Function<Object[], Object>> body;

    public FunctionCellInfo(int localCount, Cell<Function<Object[], Object>> body) {
        this.localCount = localCount;
        this.body = body;
    }
}
