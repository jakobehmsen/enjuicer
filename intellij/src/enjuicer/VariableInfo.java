package enjuicer;

public class VariableInfo {
    public final Class<?> type;
    public final String name;
    public final int depth;

    public VariableInfo(Class<?> type, String name, int depth) {
        this.type = type;
        this.name = name;
        this.depth = depth;
    }
}
