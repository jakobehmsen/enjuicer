package enjuicer;

import javax.swing.*;

public interface SlotValueComponent {
    JComponent getComponent();
    boolean accepts(Object value);
    void setValue(Object value);
}
