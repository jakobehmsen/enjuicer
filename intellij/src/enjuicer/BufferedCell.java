package enjuicer;

import java.util.ArrayList;

public abstract class BufferedCell<T> implements Cell<T> {
    private ArrayList<CellConsumer<T>> consumers = new ArrayList();

    @Override
    public Binding consume(Object[] args, CellConsumer<T> consumer) {
        consumers.add(consumer);

        supplyTo(consumer);

        return () -> consumers.remove(consumer);
    }

    protected void supplyToAll() {
        consumers.forEach(x -> supplyTo(x));
    }

    protected void supplyAtEnd() {
        consumers.forEach(x -> x.atEnd());
        // Clear?
    }

    protected abstract void supplyTo(CellConsumer<T> consumer);
}
