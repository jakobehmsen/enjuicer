package enjuicer;

import java.util.function.Function;

public class FunctionInfo {
    public final int localCount;
    public final Function<Object[], Object> body;

    public FunctionInfo(int localCount, Function<Object[], Object> body) {
        this.localCount = localCount;
        this.body = body;
    }
}
