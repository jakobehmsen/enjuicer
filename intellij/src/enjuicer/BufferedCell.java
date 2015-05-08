package enjuicer;

import java.util.ArrayList;

public abstract class BufferedCell<T> implements Cell<T> {
    private ArrayList<CellConsumer<T>> consumers = new ArrayList();

    @Override
    public Binding consume(CellConsumer<T> consumer) {
        consumers.add(consumer);
        addedConsumer(consumer);

        return () -> {
            consumers.remove(consumer);
            removedConsumer(consumer);
        };
    }

    protected void supplyToAll(T value) {
        consumers.forEach(x -> x.next(value));
    }

    protected void supplyAtEnd() {
        consumers.forEach(x -> x.atEnd());
        // Clear?
    }

    protected void addedConsumer(CellConsumer<T> consumer) { }
    protected void removedConsumer(CellConsumer<T> consumer) { }
    protected int getConsumerCount() {
        return consumers.size();
    }
}
