package enjuicer;

import java.util.function.Consumer;

public class GenericRelationInfo {
    public final int localCount;
    public final Cell<Consumer<Object[]>> body;

    public GenericRelationInfo(int localCount, Cell<Consumer<Object[]>> body) {
        this.localCount = localCount;
        this.body = body;
    }
}
