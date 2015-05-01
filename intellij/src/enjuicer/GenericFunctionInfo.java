package enjuicer;

import java.util.function.Function;

public class GenericFunctionInfo {
    public final int localCount;
    public final Function<Object[], Object> body;

    public GenericFunctionInfo(int localCount, Function<Object[], Object> body) {
        this.localCount = localCount;
        this.body = body;
    }
}
