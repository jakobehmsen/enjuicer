package enjuicer;

import java.util.function.Consumer;

public class SpecificRelationInfo {
    public final int localCount;
    public final Consumer<Object[]> body;

    public SpecificRelationInfo(int localCount, Consumer<Object[]> body) {
        this.localCount = localCount;
        this.body = body;
    }
}
