package enjuicer;

public interface Cell<T> {
    Binding consume(CellConsumer<T> consumer);
    T value(Object[] args);
}
