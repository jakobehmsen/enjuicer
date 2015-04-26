package enjuicer;

public interface Cell<T> {
    Binding consume(Object[] args, CellConsumer<T> consumer);
    T value(Object[] args);
}
