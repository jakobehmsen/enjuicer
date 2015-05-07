package enjuicer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.function.Consumer;

public class SlotComponent extends JPanel implements Cell<Object>, CellConsumer<Object> {
    private Slot<Object> slot;
    private SlotValueComponent slotValue;
    private SlotValueComponentFactory slotValueFactory;

    public SlotComponent(SlotValueComponentFactory slotValueFactory) {
        slot = new Slot<>();
        setLayout(new BorderLayout());
        this.slotValueFactory = slotValueFactory;
    }

    private Binding binding;

    @Override
    public void setBinding(Binding binding) {
        this.binding = binding;
    }

    @Override
    public Binding getBinding() {
        return binding;
    }

    @Override
    public Binding consume(CellConsumer<Object> consumer) {
        return slot.consume(consumer);
    }

    @Override
    public void next(Object value) {
        if(slotValue == null) {
            slotValue = slotValueFactory.createSlotComponentValue(this, slot, value);
            setBounds(slotValue.getComponent().getBounds());
            add(slotValue.getComponent(), BorderLayout.CENTER);
            revalidate();
            repaint();
        } else if(!slotValue.accepts(value)) {
            remove(slotValue.getComponent());
            slotValue = slotValueFactory.createSlotComponentValue(this, slot, value);
            add(slotValue.getComponent(), BorderLayout.CENTER);
            revalidate();
            repaint();
        } else
            slotValue.setValue(value);
    }

    @Override
    public void setDescription(Object description) {
        slot.setDescription(description);
    }

    @Override
    public Object getDescription() {
        return slot.getDescription();
    }

    private Hashtable<String, Binding> propertyBindings = new Hashtable<>();

    private Consumer propertyUpdater(String name) {
        switch(name) {
            case "x":
                return value ->
                    setLocation(((BigDecimal) value).intValue(), getY());
            case "y":
                return value ->
                    setLocation(getX(), ((BigDecimal) value).intValue());
            case "width":
                return value -> {
                    setSize(((BigDecimal) value).intValue(), getHeight());
                    revalidate();
                    repaint();
                };
            case "height":
                return value -> {
                    setSize(getWidth(), ((BigDecimal) value).intValue());
                    revalidate();
                    repaint();
                };
        }

        return null;
    }

    public void propertyAssign(String name, Cell expressionValueCell) {
        Binding binding = propertyBindings.get(name);

        if(binding != null)
            binding.remove();

        Consumer propertyUpdater = propertyUpdater(name);
        binding = expressionValueCell.consume(value -> {
            propertyUpdater.accept(value);
        });

        propertyBindings.put(name, binding);
    }

    private abstract class PropertyCell implements Cell {
        ArrayList<CellConsumer> consumers = new ArrayList<>();

        @Override
        public Binding consume(CellConsumer consumer) {
            consumers.add(consumer);
            consumer.next(value());
            return () -> {
                consumers.remove(consumer);
                if(consumers.isEmpty())
                    clean();
            };
        }

        protected void post() {
            consumers.forEach(x -> x.next(value()));
        }

        protected abstract void clean();

        protected abstract Object value();
    }

    private abstract class ComponentListerPropertyCell extends PropertyCell {
        Object lastValue;

        ComponentListener listener = new ComponentAdapter() {
            {
                lastValue = value();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                componentChanged();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                componentChanged();
            }
        };

        {
            addComponentListener(listener);
        }

        @Override
        protected void clean() {
            removeComponentListener(listener);
        }

        protected void componentChanged() {
            if(lastValue == null || !lastValue.equals(value()))
                post();
            lastValue = value();
        }
    }

    public Cell property(String name) {
        switch(name) {
            case "x":
                return new ComponentListerPropertyCell() {
                    @Override
                    protected Object value() {
                        return new BigDecimal(getX());
                    }
                };
            case "y":
                return new ComponentListerPropertyCell() {
                    @Override
                    protected Object value() {
                        return new BigDecimal(getY());
                    }
                };
            case "width":
                return new ComponentListerPropertyCell() {
                    @Override
                    protected Object value() {
                        return new BigDecimal(getWidth());
                    }
                };
            case "height":
                return new ComponentListerPropertyCell() {
                    @Override
                    protected Object value() {
                        return new BigDecimal(getHeight());
                    }
                };
        }

        return null;
    }
}
