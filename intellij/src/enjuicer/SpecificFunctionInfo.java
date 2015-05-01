package enjuicer;

import java.util.function.Function;

public class SpecificFunctionInfo {
    public final int localCount;
    public final Function<Object[], Object> body;

    public SpecificFunctionInfo(int localCount, Function<Object[], Object> body) {
        this.localCount = localCount;
        this.body = body;
    }
}
