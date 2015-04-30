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
import java.util.function.Function;

public class SlotComponent extends JPanel implements Cell<Object>, CellConsumer<Object> {
    private Slot<Object> slot;
    private SlotValueComponent slotValue;
    private SlotValueComponentFactory slotValueFactory;

    public SlotComponent(SlotValueComponentFactory slotValueFactory) {
        slot = new Slot<>();
        setLayout(new BorderLayout());
        this.slotValueFactory = slotValueFactory;
    }

    @Override
    public void setBinding(Binding binding) {
        slot.setBinding(binding);
    }

    @Override
    public Binding consume(Object[] args, CellConsumer<Object> consumer) {
        return slot.consume(args, consumer);
    }

    @Override
    public Object value(Object[] args) {
        //return slot.value(args);
        return null;
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

    @Override
    public Binding getBinding() {
        return slot.getBinding();
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

    public void propertyAssign(Object[] args, String name, Cell<Function<Object[], Object>> expressionValueCell) {//Cell<Object> valueCell) {
        Binding binding = propertyBindings.get(name);

        if(binding != null)
            binding.remove();

        Consumer propertyUpdater = propertyUpdater(name);
        //binding = valueCell.consume(args, value -> propertyUpdater.accept(value));
        binding = expressionValueCell.consume(args, expressionValue -> {
            Object value = expressionValue.apply(args);
            propertyUpdater.accept(value);
        });

        propertyBindings.put(name, binding);
    }

    public abstract class PropertyExpressionCell implements Cell<Function<Object[], Object>> {
        ArrayList<CellConsumer<Function<Object[], Object>>> consumers = new ArrayList<>();

        @Override
        public Binding consume(Object[] args, CellConsumer<Function<Object[], Object>> consumer) {
            consumers.add(consumer);
            Function<Object[], Object> expression = eArgs -> getValue();
            consumer.next(expression);
            return () -> {
                consumers.remove(consumer);
                if(consumers.isEmpty())
                    clean();
            };
        }

        protected void post() {
            Function<Object[], Object> expression = args -> getValue();
            consumers.forEach(x -> x.next(expression));
        }

        protected abstract void clean();

        @Override
        public Function<Object[], Object> value(Object[] args) {
            return null;
        }

        protected abstract Object getValue();
    }

    private abstract class ComponentListerPropertyExpressionCell extends PropertyExpressionCell {
        Object lastValue;

        ComponentListener listener = new ComponentAdapter() {
            {
                lastValue = getValue();
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
            if(lastValue == null || !lastValue.equals(getValue()));
            post();
            lastValue = getValue();
        }
    }

    public PropertyExpressionCell propertyExpression(String name) {
        switch(name) {
            case "x":
                return new ComponentListerPropertyExpressionCell() {
                    @Override
                    public Object getValue() {
                        return new BigDecimal(getX());
                    }
                };
            case "y":
                return new ComponentListerPropertyExpressionCell() {
                    @Override
                    public Object getValue() {
                        return new BigDecimal(getY());
                    }
                };
            case "width":
                return new ComponentListerPropertyExpressionCell() {
                    @Override
                    public Object getValue() {
                        return new BigDecimal(getWidth());
                    }
                };
            case "height":
                return new ComponentListerPropertyExpressionCell() {
                    @Override
                    public Object getValue() {
                        return new BigDecimal(getHeight());
                    }
                };
        }

        return null;
    }

    /*private abstract class PropertyCell implements Cell {
        ArrayList<CellConsumer> consumers = new ArrayList<>();

        @Override
        public Binding consume(Object[] args, CellConsumer consumer) {
            consumers.add(consumer);
            consumer.next(value(null));
            return () -> {
                consumers.remove(consumer);
                if(consumers.isEmpty())
                    clean();
            };
        }

        protected void post() {
            consumers.forEach(x -> x.next(value(null)));
        }

        protected abstract void clean();
    }*/

    /*private abstract class ComponentListerPropertyCell extends PropertyCell {
        Object lastValue;

        ComponentListener listener = new ComponentAdapter() {
            {
                lastValue = value(null);
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
            if(lastValue == null || !lastValue.equals(value(null)));
                post();
            lastValue = value(null);
        }
    }*/

    /*public Cell property(String name) {
        switch(name) {
            case "x":
                return new ComponentListerPropertyCell() {
                    @Override
                    public Object value(Object[] args) {
                        return new BigDecimal(getX());
                    }
                };
            case "y":
                return new ComponentListerPropertyCell() {
                    @Override
                    public Object value(Object[] args) {
                        return new BigDecimal(getY());
                    }
                };
            case "width":
                return new ComponentListerPropertyCell() {
                    @Override
                    public Object value(Object[] args) {
                        return new BigDecimal(getWidth());
                    }
                };
            case "height":
                return new ComponentListerPropertyCell() {
                    @Override
                    public Object value(Object[] args) {
                        return new BigDecimal(getHeight());
                    }
                };
        }

        return null;
    }*/
}
