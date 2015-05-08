package enjuicer;

import javax.swing.*;
import java.math.BigDecimal;
import java.math.MathContext;

public class TimeCell extends BufferedCell<BigDecimal> {
    // Resolution with respect to milliseconds
    private BigDecimal resolution;
    private Timer timer;

    public TimeCell(BigDecimal resolution) {
        this.resolution = resolution;
    }

    @Override
    protected void addedConsumer(CellConsumer<BigDecimal> consumer) {
        if(getConsumerCount() == 1) {
            // Added first consumer
            int delay = resolution.intValue();
            timer = new Timer(delay, e -> {
                long timeInMillis = System.currentTimeMillis();
                BigDecimal value = new BigDecimal(timeInMillis);
                supplyToAll(value);
            });
            timer.setInitialDelay(0);
            timer.setRepeats(true);
            timer.start();
        }
    }

    @Override
    protected void removedConsumer(CellConsumer<BigDecimal> consumer) {
        if(getConsumerCount() == 0) {
            // Removed last consumer
            timer.stop();
            timer = null;
        }
    }
}
