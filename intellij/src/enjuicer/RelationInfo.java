package enjuicer;

import java.util.function.Consumer;

public class RelationInfo {
    public final int localCount;
    public final Consumer<Object[]> body;

    public RelationInfo(int localCount, Consumer<Object[]> body) {
        this.localCount = localCount;
        this.body = body;
    }
}
