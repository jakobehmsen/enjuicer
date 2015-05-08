package enjuicer;

public class ProxyCell<T> extends BufferedCell<T> {
    private Cell<Cell<T>> cellOfCells; // Could be a function call that returns cells
    private Binding cellOfCellsBinding;
    private Binding currentCellBinding;

    public ProxyCell(Cell<Cell<T>> cellOfCells) {
        this.cellOfCells = cellOfCells;
    }

    @Override
    protected void addedConsumer(CellConsumer<T> consumer) {
        if(getConsumerCount() == 1) {
            // Added first consumer
            cellOfCellsBinding = cellOfCells.consume(nextCell -> {
                if(currentCellBinding != null)
                    currentCellBinding.remove();

                currentCellBinding = nextCell.consume(value -> {
                    supplyToAll(value);
                });
            });
        }
    }

    @Override
    protected void removedConsumer(CellConsumer<T> consumer) {
        if(getConsumerCount() == 0) {
            // Removed last consumer
            if(currentCellBinding != null)
                currentCellBinding.remove();
            cellOfCellsBinding.remove();
        }
    }
}
