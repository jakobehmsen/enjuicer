package enjuicer;

import enjuicer.lang.antlr4.LangBaseVisitor;
import enjuicer.lang.antlr4.LangLexer;
import enjuicer.lang.antlr4.LangParser;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.NotNull;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MainView extends JFrame implements Canvas {
    private java.util.List<Tool> tools;
    private JComponent toolBoxView;
    private JLayeredPane canvasView;
    private JComponent scriptView;
    private Hashtable<String, Cell> environment = new Hashtable<>();

    private static class Selection {
        private final JComponent component;
        private final JComponent marking;
        private final String variableName;
        private final ComponentListener componentListener;

        private Selection(JComponent componentOver, JComponent marking, String variableName, ComponentListener componentListener) {
            this.component = componentOver;
            this.marking = marking;
            this.variableName = variableName;
            this.componentListener = componentListener;
        }
    }

    private ArrayList<Selection> selections = new ArrayList<>();

    private JPanel selectionsOverlay;

    @Override
    public void beginSelect() {
        selectionsOverlay = new JPanel();
        selectionsOverlay.setSize(canvasView.getSize());
        selectionsOverlay.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (MouseListener l : canvasView.getMouseListeners())
                    l.mouseClicked(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                for (MouseListener l : canvasView.getMouseListeners())
                    l.mousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                for (MouseListener l : canvasView.getMouseListeners())
                    l.mouseReleased(e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                for (MouseListener l : canvasView.getMouseListeners())
                    l.mouseEntered(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                for (MouseListener l : canvasView.getMouseListeners())
                    l.mouseExited(e);
            }
        });
        selectionsOverlay.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                for (MouseMotionListener l : canvasView.getMouseMotionListeners())
                    l.mouseDragged(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                for (MouseMotionListener l : canvasView.getMouseMotionListeners())
                    l.mouseMoved(e);
            }
        });
        selectionsOverlay.setOpaque(false);

        canvasView.add(selectionsOverlay);
        canvasView.setLayer(selectionsOverlay, JLayeredPane.DRAG_LAYER + 1);
    }

    @Override
    public void endSelect() {
        canvasView.remove(selectionsOverlay);
        selectionsOverlay = null;
    }

    @Override
    public JComponent findComponent(int x, int y) {
        JComponent componentOver = (JComponent)Arrays.asList(canvasView.getComponentsInLayer(JLayeredPane.DEFAULT_LAYER)).stream().filter(c ->
            c.getBounds().contains(x, y)).findFirst().orElseGet(() -> null);

        return componentOver;
    }

    @Override
    public boolean isSelected(JComponent component) {
        return selections.stream().anyMatch(x -> x.component == component);
    }

    @Override
    public void select(JComponent component) {
        JPanel marking = new JPanel(new BorderLayout());
        String variableName = component.getName();
        marking.setToolTipText(variableName);
        TitledBorder border = new TitledBorder(variableName);
        border.setTitleColor(Color.DARK_GRAY);
        border.setTitleFont(new Font(Font.MONOSPACED, Font.BOLD | Font.ITALIC, 16));
        marking.setBorder(border);
        marking.setOpaque(false);

        int sizeExtra = 6;
        int topExtra = 10;

        canvasView.add(marking, JLayeredPane.DRAG_LAYER);
        canvasView.revalidate();
        canvasView.repaint();

        ComponentListener componentListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                marking.setSize(sizeExtra + component.getWidth() + sizeExtra, topExtra + sizeExtra + component.getHeight() + sizeExtra);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                marking.setLocation(component.getX() - sizeExtra, component.getY() - (topExtra + sizeExtra));
            }
        };

        componentListener.componentResized(new ComponentEvent(component, -1));
        componentListener.componentMoved(new ComponentEvent(component, -1));

        component.addComponentListener(componentListener);

        selections.add(new Selection(component, marking, variableName, componentListener));

        environment.put(variableName, (Cell) component);
    }

    @Override
    public void deselect(JComponent component) {
        Selection selection = selections.stream().filter(s -> s.component == component).findFirst().orElseGet(() -> null);

        selections.remove(selection);

        canvasView.remove(selection.marking);
        environment.remove(selection.variableName);
        canvasView.revalidate();
        canvasView.repaint();
    }

    @Override
    public void clearSelection() {
        selections.forEach(s -> {
            canvasView.remove(s.marking);
            s.component.removeComponentListener(s.componentListener);
            environment.remove(s.variableName);
        });
        selections.clear();
        canvasView.revalidate();
        canvasView.repaint();
    }

    private static class Selector {
        private final String name;
        private final Class<?>[] parameterTypes;

        private Selector(String name, Class<?>[] parameterTypes) {
            this.name = name;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public int hashCode() {
            return name.hashCode() * Arrays.hashCode(parameterTypes);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof Selector) {
                Selector objSelector = (Selector)obj;
                return this.name.equals(objSelector.name) &&
                    Arrays.equals(this.parameterTypes, objSelector.parameterTypes);
            }

            return false;
        }
    }

    private static class GenericSelector {
        private final String name;
        private final int arity;

        private GenericSelector(String name, int arity) {
            this.name = name;
            this.arity = arity;
        }

        @Override
        public int hashCode() {
            return name.hashCode() * arity;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof GenericSelector) {
                GenericSelector objSelector = (GenericSelector)obj;
                return this.name.equals(objSelector.name) &&
                    this.arity == objSelector.arity;
            }

            return false;
        }
    }

    private static class SpecificSelector {
        private final Class<?>[] parameterTypes;

        private SpecificSelector(Class<?>[] parameterTypes) {
            this.parameterTypes = parameterTypes;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(parameterTypes);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof SpecificSelector) {
                SpecificSelector objSelector = (SpecificSelector)obj;
                return Arrays.equals(this.parameterTypes, objSelector.parameterTypes);
            }

            return false;
        }
    }

    private Hashtable<Selector, Binding> functionBindings = new Hashtable<>();
    private Hashtable<GenericSelector, GenericFunction> genericFunctions = new Hashtable<>();

    private static class GenericFunction implements Cell<Map<SpecificSelector, SpecificFunctionInfo>> {
        private Hashtable<SpecificSelector, SpecificFunctionInfo> applicableSpecificFunctions = new Hashtable<>();
        private ArrayList<CellConsumer<Map<SpecificSelector, SpecificFunctionInfo>>> consumers = new ArrayList<>();

        public void define(Class<?>[] parameterTypes, SpecificFunctionInfo genericFunction) {
            SpecificSelector specificSelector = new SpecificSelector(parameterTypes);
            applicableSpecificFunctions.put(specificSelector, genericFunction);
            update();
        }

        private void update() {
            consumers.forEach(x -> x.next((Map<SpecificSelector, SpecificFunctionInfo>) applicableSpecificFunctions.clone()));
        }

        @Override
        public Binding consume(CellConsumer<Map<SpecificSelector, SpecificFunctionInfo>> consumer) {
            consumers.add(consumer);

            consumer.next((Map<SpecificSelector, SpecificFunctionInfo>) applicableSpecificFunctions.clone());

            return () -> consumers.remove(consumer);
        }

        public static SpecificFunctionInfo resolve(Map<SpecificSelector, SpecificFunctionInfo> functions, Class<?>[] parameterTypes) {
            java.util.List<SpecificFunctionInfo> candidates = functions.entrySet().stream()
                .filter(x ->
                    x.getKey().parameterTypes.length == parameterTypes.length)
                .filter(x ->
                    IntStream.range(0, parameterTypes.length).allMatch(i ->
                        x.getKey().parameterTypes[i].isAssignableFrom(parameterTypes[i])))
                .map(x -> x.getValue())
                .collect(Collectors.toList());

            if(candidates.size() > 0) {
                // TODO: Compare candidates; select "most specific".
                return candidates.get(0);
            }

            return null;
        }
    }

    private GenericFunction getFunction(GenericSelector selector) {
        GenericFunction functionBinding = genericFunctions.get(selector);
        if(functionBinding == null) {
            functionBinding = new GenericFunction();
            genericFunctions.put(selector, functionBinding);
        }
        return functionBinding;
    }

    private void define(String name, Class<?>[] parameterTypes, Function<Object[], Object> function) {
        define(name, parameterTypes, parameterTypes.length, function);
    }

    private void define(String name, Class<?>[] parameterTypes, int localCount, Function<Object[], Object> function) {
        GenericFunction genericFunction = getFunction(new GenericSelector(name, parameterTypes.length));
        genericFunction.define(parameterTypes, new SpecificFunctionInfo(localCount, function));
    }

    private <Return> void define(String name, Supplier<Return> function) {
        define(name, new Class<?>[0], args -> function.get());
    }

    private <P0, Return> void define(String name, Class<P0> param1, Function<P0, Return> function) {
        define(name, new Class<?>[]{param1}, args -> function.apply((P0) args[0]));
    }

    private <P0, P1, Return> void define(String name, Class<P0> param1, Class<P1> param2, BiFunction<P0, P1, Return> function) {
        define(name, new Class<?>[]{param1, param2}, args -> function.apply((P0)args[0], (P1)args[1]));
    }

    private <P0, P1, P2, Return> void define(String name, Class<P0> param1, Class<P1> param2, Class<P2> param3, TriFunction<P0, P1, P2, Return> function) {
        define(name, new Class<?>[]{param1, param2, param3}, args -> function.apply((P0)args[0], (P1)args[1], (P2)args[2]));
    }

    /*
    Modify relation usages to be implicitly updated like function calls
    */
    private Hashtable<Selector, Binding> relationBindings = new Hashtable<>();
    private Hashtable<GenericSelector, GenericRelation> genericRelations = new Hashtable<>();

    private static class GenericRelation implements Cell<Map<SpecificSelector, SpecificRelationInfo>> {
        private Hashtable<SpecificSelector, Binding> specificRelationBindings = new Hashtable<>();
        private Hashtable<SpecificSelector, GenericRelationInfo> applicableGenericRelations = new Hashtable<>();
        private Hashtable<SpecificSelector, SpecificRelationInfo> applicableSpecificRelations = new Hashtable<>();
        private ArrayList<CellConsumer<Map<SpecificSelector, SpecificRelationInfo>>> consumers = new ArrayList<>();

        public void define(Class<?>[] parameterTypes, SpecificRelationInfo specificRelation) {
            SpecificSelector specificSelector = new SpecificSelector(parameterTypes);

            applicableSpecificRelations.put(specificSelector, specificRelation);
            update();
        }

        private void update() {
            consumers.forEach(x -> x.next((Map<SpecificSelector, SpecificRelationInfo>) applicableSpecificRelations.clone()));
        }

        @Override
        public Binding consume(CellConsumer<Map<SpecificSelector, SpecificRelationInfo>> consumer) {
            consumers.add(consumer);

            consumer.next((Map<SpecificSelector, SpecificRelationInfo>) applicableSpecificRelations.clone());

            return () -> consumers.remove(consumer);
        }

        public static SpecificRelationInfo resolve(Map<SpecificSelector, SpecificRelationInfo> relations, Class<?>[] parameterTypes) {
            java.util.List<SpecificRelationInfo> candidates = relations.entrySet().stream()
                .filter(x ->
                    x.getKey().parameterTypes.length == parameterTypes.length)
                .filter(x ->
                    IntStream.range(0, parameterTypes.length).allMatch(i ->
                        x.getKey().parameterTypes[i].isAssignableFrom(parameterTypes[i])))
                .map(x -> x.getValue())
                .collect(Collectors.toList());

            if(candidates.size() > 0) {
                // TODO: Compare candidates; select "most specific".
                return candidates.get(0);
            }

            return null;
        }
    }

    private GenericRelation getRelation(GenericSelector selector) {
        GenericRelation functionBinding = genericRelations.get(selector);
        if(functionBinding == null) {
            functionBinding = new GenericRelation();
            genericRelations.put(selector, functionBinding);
        }
        return functionBinding;
    }

    private void defineRelation(String name, Class<?>[] parameterTypes, int localCount, Consumer<Object[]> relation) {
        GenericRelation genericRelation = getRelation(new GenericSelector(name, parameterTypes.length));
        genericRelation.define(parameterTypes, new SpecificRelationInfo(localCount, relation));
    }

    public MainView(java.util.List<Tool> tools) {
        this.tools = tools;

        setTitle("Enjuicer");

        toolBoxView = createToolBoxView();
        canvasView = createCanvasView();
        scriptView = createScriptView();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(toolBoxView, BorderLayout.NORTH);
        getContentPane().add(canvasView, BorderLayout.CENTER);
        getContentPane().add(scriptView, BorderLayout.SOUTH);

        define("toString", Object.class, x -> x.toString());

        define("+", BigDecimal.class, BigDecimal.class, (lhs, rhs) -> lhs.add(rhs));
        define("-", BigDecimal.class, BigDecimal.class, (lhs, rhs) -> lhs.subtract(rhs));
        define("/", BigDecimal.class, BigDecimal.class, (lhs, rhs) -> lhs.divide(rhs, MathContext.DECIMAL128));
        define("*", BigDecimal.class, BigDecimal.class, (lhs, rhs) -> lhs.multiply(rhs));

        define("+", String.class, String.class, (lhs, rhs) -> lhs.concat(rhs));
        define("split", String.class, String.class, (lhs, rhs) -> new Tuple(lhs.split(rhs)));

        define("range", BigDecimal.class, BigDecimal.class, (start, end) ->
            new Tuple(IntStream.range(start.intValue(), end.intValue()).mapToObj(x -> new BigDecimal(x)).toArray()));
        define("size", Tuple.class, tuple -> new BigDecimal(tuple.values.length));
        define("+", Tuple.class, Tuple.class, (lhs, rhs) ->
            new Tuple(Stream.concat(Arrays.asList(lhs.values).stream(), Arrays.asList(rhs.values).stream()).toArray())
        );
        define("*", Tuple.class, BigDecimal.class, (lhs, rhs) -> {
            Object[] newValues = new Object[lhs.values.length * rhs.intValue()];

            for(int i = 0; i < rhs.intValue(); i++)
                System.arraycopy(lhs.values, 0, newValues, i * lhs.values.length, lhs.values.length);

            return new Tuple(newValues);
        });
        define("apply", Tuple.class, BlockClosure.class, (tuple, reduction) ->
            Arrays.asList(tuple.values).stream().reduce((x, y) ->
                    reduction.value(new Object[]{x, y})
            ).get()
        );
        define("map", Tuple.class, BlockClosure.class, (tuple, reduction) ->
            new Tuple(Arrays.asList(tuple.values).stream().map(x -> reduction.value(new Object[]{x})).toArray())
        );
        define("keep", Tuple.class, BigDecimal.class, (tuple, count) ->
            new Tuple(Arrays.asList(tuple.values).stream().limit(count.longValue()).toArray())
        );
        define("skip", Tuple.class, BigDecimal.class, (tuple, count) ->
            new Tuple(Arrays.asList(tuple.values).stream().skip(count.longValue()).toArray())
        );
        define("flatMap", Tuple.class, BlockClosure.class, (tuple, mapper) ->
            new Tuple(Arrays.asList(tuple.values).stream().flatMap(x ->
                Stream.of(((Tuple) mapper.value(new Object[]{x})).values)).toArray())
        );

        define("time", BigDecimal.class, resolution -> new TimeCell(resolution));
    }

    private ButtonGroup toolBoxButtonGroup;

    private JComponent createToolBoxView() {
        JToolBar toolBar = new JToolBar();

        toolBar.setFloatable(false);

        toolBoxButtonGroup = new ButtonGroup();

        for(int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);

            JRadioButton b = new JRadioButton();
            b.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    tool.activate();
                } else if (e.getStateChange() == ItemEvent.DESELECTED) {
                    tool.deactivate();
                }
            });
            b.setActionCommand("" + i);
            if(i == 0) {
                b.setSelected(true);
            }
            b.setText(tool.getText());
            toolBoxButtonGroup.add(b);
            toolBar.add(b);
        }

        return toolBar;
    }

    private Tool getSelectedTool() {
        int indexOfTool = Integer.parseInt(toolBoxButtonGroup.getSelection().getActionCommand());
        return tools.get(indexOfTool);
    }

    private MouseAdapter canvasMouseAdapterProxy = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            canvasMouseAdapter.mouseClicked(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            canvasMouseAdapter.mousePressed(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            canvasMouseAdapter.mouseReleased(e);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            canvasMouseAdapter.mouseEntered(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            canvasMouseAdapter.mouseExited(e);
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            canvasMouseAdapter.mouseWheelMoved(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            canvasMouseAdapter.mouseDragged(e);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            canvasMouseAdapter.mouseMoved(e);
        }
    };
    private MouseAdapter canvasMouseAdapter;

    private JLayeredPane createCanvasView() {
        JLayeredPane view = new JLayeredPane();

        view.setLayout(null);

        switchCanvasMousePending();

        view.addMouseListener(canvasMouseAdapterProxy);
        view.addMouseMotionListener(canvasMouseAdapterProxy);

        tools.forEach(t -> t.setTarget(view));

        return view;
    }

    private void switchCanvasMousePending() {
        canvasMouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Tool tool = getSelectedTool();
                switchCanvasMouseAction(tool.startSession(e.getX(), e.getY()));
            }
        };
    }

    private void switchCanvasMouseAction(ToolSession toolSession) {
        canvasMouseAdapter = new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
               toolSession.update(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                toolSession.end();
                switchCanvasMousePending();
            }
        };
    }

    private Border createScriptViewBorder(Color color) {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, color),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        );
    }

    private JComponent createScriptView() {
        JTextPane view = new JTextPane();

        view.setBorder(createScriptViewBorder(Color.BLACK));
        view.setFont(new Font(Font.MONOSPACED, Font.BOLD | Font.ITALIC, 16));

        view.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String sourceCode = view.getText();

                    try {
                        ANTLRInputStream in = new ANTLRInputStream(new ByteArrayInputStream(sourceCode.getBytes()));
                        LangLexer lexer = new LangLexer(in);
                        LangParser parser = new LangParser(new CommonTokenStream(lexer));

                        LangParser.ProgramContext programCtx = parser.program();

                        Border b = view.getBorder();

                        if (parser.getNumberOfSyntaxErrors() == 0) {
                            evaluateProgram(programCtx);

                            view.setBorder(createScriptViewBorder(Color.GREEN));
                        } else {
                            view.setBorder(createScriptViewBorder(Color.RED));
                        }

                        Timer timer = new Timer(500, e1 -> view.setBorder(b));
                        timer.setRepeats(false);
                        timer.start();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        tools.forEach(t -> t.setEnvironment(environment));
        tools.forEach(t -> t.setCanvas(this));

        return view;
    }

    @Override
    public void setScript(String src) {
        ((JTextPane)scriptView).setText(src);
    }

    private int nextOutX;
    private int nextOutY;

    private void updateOuts(int width, int height) {
        nextOutY += height + 30;
    }

    private SlotValueComponent createSlotNumber(Slot slot, BigDecimal value) {
        return new SlotValueComponent() {
            private JFormattedTextField component;

            {
                NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
                nf.setParseIntegerOnly(false);
                NumberFormatter formatter = new NumberFormatter(nf);
                formatter.setValueClass(BigDecimal.class);
                component = new JFormattedTextField(formatter);

                component.addPropertyChangeListener("value", evt -> {
                    BigDecimal currentValue = (BigDecimal) component.getValue();
                    if (currentValue != null)
                        slot.set(currentValue);
                });

                component.setValue(value);
                component.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
            }

            @Override
            public JComponent getComponent() {
                return component;
            }

            @Override
            public boolean accepts(Object value) {
                return value instanceof BigDecimal;
            }

            @Override
            public void setValue(Object value) {
                component.setValue(value);
            }
        };
    }

    private SlotValueComponent createSlotText(Slot slot, String value) {
        return new SlotValueComponent() {
            private JTextPane component;
            private boolean hasChanges;

            {
                component = new JTextPane();
                component.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        hasChanges = true;
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        hasChanges = true;
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        hasChanges = true;
                    }
                });
                component.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER)
                            update();
                    }
                });
                component.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        update();
                    }
                });

                component.setText(value);
                slot.set(value);
                component.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
            }

            private void update() {
                if(hasChanges) {
                    String currentValue = (String) component.getText();
                    if (currentValue != null)
                        slot.set(currentValue);
                    hasChanges = false;
                }
            }

            @Override
            public JComponent getComponent() {
                return component;
            }

            @Override
            public boolean accepts(Object value) {
                return value instanceof String;
            }

            @Override
            public void setValue(Object value) {
                component.setText((String)value);
                slot.set(value);
            }
        };
    }

    private SlotValueComponent createSlotDefault(Slot slot, Object value) {
        return new SlotValueComponent() {
            private JTextArea component;

            {
                component = new JTextArea();
                component.setLineWrap(true);
                component.setWrapStyleWord(true);
                component.setEditable(false);
                component.setForeground(Color.WHITE);
                component.setBackground(Color.DARK_GRAY);
                component.setText(value.toString());
                component.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
                slot.set(value);
            }

            @Override
            public JComponent getComponent() {
                return component;
            }

            @Override
            public boolean accepts(Object value) {
                return value instanceof Object[];
            }

            @Override
            public void setValue(Object value) {
                component.setText(value.toString());
                slot.set(value);
            }
        };
    }

    private SlotValueComponent createSlotLine(Slot slot, Line value) {
        return new SlotValueComponent() {
            private LineTool.Line component;

            {
                component = new LineTool.Line(value.x1, value.y1, value.x2, value.y2);
            }

            @Override
            public JComponent getComponent() {
                return component;
            }

            @Override
            public boolean accepts(Object value) {
                return value instanceof Line;
            }

            @Override
            public void setValue(Object value) {
                component.setLine(((Line) value).x1, ((Line) value).y1, ((Line) value).x2, ((Line) value).y2);
            }
        };
    }

    private Function<Object[], Cell> parseExpression(ParserRuleContext ctx, Map<String, Cell> idToCellMap, ArrayList<VariableInfo> locals, int depth) {
        return ctx.accept(new LangBaseVisitor<Function<Object[], Cell>>() {
            @Override
            public Function<Object[], Cell> visitAddExpression(@NotNull LangParser.AddExpressionContext ctx) {
                Function<Object[], Cell> lhs = parseExpression(ctx.mulExpression(0), idToCellMap, locals, depth);

                if (ctx.mulExpression().size() > 1) {
                    for (int i = 1; i < ctx.mulExpression().size(); i++) {
                        Function<Object[], Cell> rhsCell = parseExpression(ctx.mulExpression(i), idToCellMap, locals, depth);

                        Function<Object[], Cell> lhsCell = lhs;

                        String operator = ctx.ADD_OP(i - 1).getText();

                        lhs = createExpressionBinaryOperation(operator, lhsCell, rhsCell, depth);
                    }
                }

                return lhs;
            }

            @Override
            public Function<Object[], Cell> visitMulExpression(@NotNull LangParser.MulExpressionContext ctx) {
                Function<Object[], Cell> lhs = parseExpression(ctx.leafExpression(0), idToCellMap, locals, depth);

                if (ctx.leafExpression().size() > 1) {
                    for (int i = 1; i < ctx.leafExpression().size(); i++) {
                        Function<Object[], Cell> rhsCell = parseExpression(ctx.leafExpression(i), idToCellMap, locals, depth);

                        Function<Object[], Cell> lhsCell = lhs;

                        String operator = ctx.MUL_OP(i - 1).getText();

                        lhs = createExpressionBinaryOperation(operator, lhsCell, rhsCell, depth);
                    }
                }

                return lhs;
            }

            @Override
            public Function<Object[], Cell> visitFunctionCall(@NotNull LangParser.FunctionCallContext ctx) {
                String name = ctx.id().ID().getText();

                java.util.List<Function<Object[], Cell>> argumentCells = ctx.expression().stream().map(x -> parseExpression(x, idToCellMap, locals, depth)).collect(Collectors.toList());

                switch(name) {
                    case "proxy":
                        return args -> {
                            Cell cellOfCells = argumentCells.get(0).apply(args);
                            return new ProxyCell(cellOfCells);
                        };
                    default:
                        return createExpressionFunctionCall(name, argumentCells, depth);
                }
            }

            @Override
            public Function<Object[], Cell> visitProperty(@NotNull LangParser.PropertyContext ctx) {
                String propertyName = ctx.name.ID().getText();
                String targetName = ctx.target.ID().getText();

                int ordinal = localOrdinal(locals, targetName, depth);

                boolean isFromSelection = ordinal == -1;

                if (isFromSelection) {
                    Cell cell = environment.get(targetName);

                    idToCellMap.put(targetName, cell);

                    Cell propertyCell = ((SlotComponent) cell).property(propertyName);

                    if(depth == 0)
                        return args -> propertyCell;
                    else
                        return eArgs -> new Cell() {
                            @Override
                            public Binding consume(CellConsumer consumer) {
                                return propertyCell.consume(v -> {
                                    Function<Object[], Object> expression = eArgs2 -> v;
                                    consumer.next(expression);
                                });
                            }
                        };
                }

                if(depth == 0)
                    return args -> {
                        Object cell = args[ordinal];
                        return ((SlotComponent) cell).property(propertyName);
                    };
                else
                    throw new RuntimeException("Cannot access cell properties through block arguments.");
            }

            @Override
            public Function<Object[], Cell> visitId(@NotNull LangParser.IdContext ctx) {
                String parameterName = ctx.ID().getText();

                int ordinal = localOrdinal(locals, parameterName, depth);

                boolean isFromSelection = ordinal == -1;

                if (isFromSelection) {
                    Cell cell = environment.get(parameterName);

                    idToCellMap.put(parameterName, cell);

                    if(depth == 0)
                        return eArgs -> cell;
                    else
                        return eArgs -> new Cell() {
                            @Override
                            public Binding consume(CellConsumer consumer) {
                                return cell.consume(v -> {
                                    Function<Object[], Object> expression = eArgs2 -> v;
                                    consumer.next(expression);
                                });
                            }
                        };
                }

                if(depth == 0)
                    return args -> (Cell)args[ordinal];
                else
                    return args -> new Cell() {
                        @Override
                        public Binding consume(CellConsumer consumer) {
                            Function<Object[], Object> expression = args2 -> args2[ordinal];
                            consumer.next(expression);

                            return () -> { };
                        }
                    };
            }

            @Override
            public Function<Object[], Cell> visitNumber(@NotNull LangParser.NumberContext ctx) {
                BigDecimal value = new BigDecimal(ctx.NUMBER().getText());

                if(depth == 0)
                    return args -> new Singleton<>(value);
                else
                    return args -> new Cell() {
                        @Override
                        public Binding consume(CellConsumer consumer) {
                            Function<Object[], Object> expression = args2 -> value;
                            consumer.next(expression);

                            return () -> { };
                        }
                    };
            }

            @Override
            public Function<Object[], Cell> visitString(@NotNull LangParser.StringContext ctx) {
                String rawValue = ctx.STRING().getText().substring(1, ctx.STRING().getText().length() - 1);
                String value = rawValue.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");

                if(depth == 0)
                    return args -> new Singleton<>(value);
                else
                    return args -> new Cell() {
                        @Override
                        public Binding consume(CellConsumer consumer) {
                            Function<Object[], Object> expression = args2 -> value;
                            consumer.next(expression);

                            return () -> { };
                        }
                    };
            }

            @Override
            public Function<Object[], Cell> visitArray(@NotNull LangParser.ArrayContext ctx) {
                java.util.List<Function<Object[], Cell>> valueExpressions =
                    ctx.expression().stream().map(x -> parseExpression(x, idToCellMap, locals, depth)).collect(Collectors.toList());

                // Could be a usage of a/the list function?

                return args -> {
                    java.util.List<Cell> valueCells = valueExpressions.stream().map(x -> x.apply(args)).collect(Collectors.toList());

                    return new Cell() {
                        @Override
                        public Binding consume(CellConsumer consumer) {
                            return new Binding() {
                                private java.util.List<Object> expressionArguments = new ArrayList<>();
                                private java.util.List<Binding> bindings;

                                {
                                    IntStream.range(0, valueCells.size()).forEach(i -> expressionArguments.add(null));
                                    bindings = IntStream.range(0, valueCells.size()).mapToObj(i -> valueCells.get(i).consume(next -> {
                                        expressionArguments.set(i, next);
                                        update();
                                    })).collect(Collectors.toList());
                                }

                                private void update() {
                                    if (expressionArguments.stream().filter(x -> x == null).count() == 0) {
                                        if(depth == 0) {
                                            Object[] values = expressionArguments.toArray();
                                            Object next = new Tuple(values);

                                            consumer.next(next);
                                        } else {
                                            Function<Object[], Object> next = args -> {
                                                Object[] values = expressionArguments.stream().map(x -> ((Function<Object[], Object>) x).apply(args)).toArray();

                                                return new Tuple(values);
                                            };

                                            consumer.next(next);
                                        }
                                    }
                                }

                                @Override
                                public void remove() {
                                    bindings.forEach(x -> x.remove());
                                    expressionArguments = null;
                                    bindings = null;
                                }
                            };
                        }
                    };
                };
            }

            @Override
            public Function<Object[], Cell> visitBlock(@NotNull LangParser.BlockContext ctx) {
                int localsStart = locals.size();

                int blockDepth = depth + 1;
                if (ctx.parameters() != null)
                    locals.addAll(ctx.parameters().ID().stream().map(x -> new VariableInfo(Object.class, x.getText(), blockDepth)).collect(Collectors.toList()));

                Function<Object[], Cell> bodyExpression = parseExpression(ctx.expression(), idToCellMap, locals, blockDepth);
                int localsCount = locals.size() - localsStart; // localsCount should be relative to parameters within same blockDepth

                return args -> {
                    Cell bodyCell = bodyExpression.apply(args);
                    return new Cell() {
                        @Override
                        public Binding consume(CellConsumer consumer) {
                            return bodyCell.consume(v -> {
                                Function<Object[], Object> body = (Function<Object[], Object>)v;
                                Object next = new BlockClosure(body, args, localsStart, localsCount);

                                if(depth > 0) {
                                    Object val = next;
                                    next = (Function<Object[], Object>) eArgs -> val;
                                }
                                consumer.next(next);
                            });
                        }
                    };
                };
            }
        });
    }

    // TODO: Pass a bindings collector, where these bindings are to used in context with relation call/relation updates.
    // I.e., when a relation is updated, then each of its relation usages should remove its previous bindings and reapply
    // the relation.
    // Can the bindings removal be implicit?
    // What about contradicting relations?
    // Should they cancel out each other?
    // Should errors occur?
    // Should they implicitly "merge" somehow? It's just OK?

    // Could return Function<Object[], Binding> such that the execution of a statement yields a binding for that
    // respective statement

    // Cell<Statement>; should produce new statements if the body of the statements changes (not the provoked side-effects when executed)
    private Cell<Consumer<Object[]>> parseStatement(ParserRuleContext statementCtx, ArrayList<VariableInfo> locals, int depth, boolean atRoot) {
        return statementCtx.accept(new LangBaseVisitor<Cell<Consumer<Object[]>>>() {
            @Override
            public Cell<Consumer<Object[]>> visitPropertyAssign(@NotNull LangParser.PropertyAssignContext ctx) {
                String targetName = ctx.target.ID().getText();
                String propertyName = ctx.name.ID().getText();

                int ordinal = localOrdinal(locals, targetName, depth);

                Map<String, Cell> idToCellMap = new Hashtable<>();
                Function<Object[], Cell> valueExpression = parseExpression(ctx.expression(), idToCellMap, locals, 0);

                boolean isFromSelection = ordinal == -1;

                if (isFromSelection) {
                    SlotComponent currentTarget = (SlotComponent) environment.get(targetName);

                    return new Singleton<>(args -> {
                        if (atRoot) {
                            // If there already is a binding for this property, then remove this binding
                            // Binding is implicitly removed within propertyAssign
                        }
                        Cell value = valueExpression.apply(args);
                        currentTarget.propertyAssign(propertyName, value);
                    });
                }

                return new Singleton<>(args -> {
                    Object cell = args[ordinal];
                    Cell value = valueExpression.apply(args);
                    ((SlotComponent) cell).propertyAssign(propertyName, value);
                });
            }

            @Override
            public Cell<Consumer<Object[]>> visitAssign(@NotNull LangParser.AssignContext ctx) {
                Map<String, Cell> idToCellMap = new Hashtable<>();

                String variableName = ctx.ID().getText();

                Function<Object[], Cell> valueExpression = parseExpression(ctx.expression(), idToCellMap, locals, 0);
                String srcCode = ctx.getText();

                if (atRoot || environment.containsKey(variableName)) {
                    Consumer<Object[]> statement = sArgs -> {
                        CellConsumer<Object> environmentCell = (CellConsumer<Object>) environment.get(variableName);

                        SlotComponent newElement;

                        if (environmentCell != null) {
                            environmentCell.getBinding().remove();
                            idToCellMap.put(variableName, (Cell) environmentCell);
                            newElement = null;
                        } else {
                            newElement = new SlotComponent(new SlotValueComponentFactory() {
                                boolean atFirst = true;

                                @Override
                                public SlotValueComponent createSlotComponentValue(JPanel wrapper, Slot slot, Object value) {
                                    if (value instanceof BigDecimal) {
                                        SlotValueComponent svc = createSlotNumber(slot, (BigDecimal) value);
                                        if (atFirst) {
                                            svc.getComponent().setSize(60, 20);
                                            svc.getComponent().setLocation(nextOutX, nextOutY);

                                            updateOuts(svc.getComponent().getWidth(), svc.getComponent().getHeight());
                                        }
                                        atFirst = false;
                                        return svc;
                                    } else if (value instanceof String) {
                                        SlotValueComponent svc = createSlotText(slot, (String) value);
                                        if (atFirst) {
                                            svc.getComponent().setSize(60, 20);
                                            svc.getComponent().setLocation(nextOutX, nextOutY);

                                            updateOuts(svc.getComponent().getWidth(), svc.getComponent().getHeight());
                                        }
                                        atFirst = false;
                                        return svc;
                                    } else if (value instanceof Line) {
                                        atFirst = false;
                                        return createSlotLine(slot, (Line) value);
                                    } else if (value instanceof Function) {
                                        return ((Function<Function<Object, SlotValueComponent>, SlotValueComponent>) value).apply(v ->
                                            createSlotComponentValue(wrapper, slot, v));
                                    } else {
                                        SlotValueComponent svc = createSlotDefault(slot, value);
                                        if (atFirst) {
                                            svc.getComponent().setSize(60, 20);
                                            svc.getComponent().setLocation(nextOutX, nextOutY);

                                            updateOuts(svc.getComponent().getWidth(), svc.getComponent().getHeight());
                                        }
                                        atFirst = false;
                                        return svc;
                                    }
                                }
                            });

                            newElement.setName(variableName);
                            environmentCell = newElement;
                            idToCellMap.put(variableName, newElement);
                            canvasView.add(newElement);
                            canvasView.moveToFront(newElement);

                            select(newElement);
                        }

                        CellConsumer<Object> currentTarget = environmentCell;

                        Cell<Object> value = valueExpression.apply(sArgs);
                        Binding binding = value.consume(currentTarget);
                        currentTarget.setBinding(binding);
                        currentTarget.setDescription(new Description(idToCellMap, srcCode));

                    };

                    return new Singleton<>(statement);
                } else {
                    int ordinal = localOrdinal(locals, variableName, depth);
                    return new Singleton<>(sArgs -> {
                        Cell<Object> value = valueExpression.apply(sArgs);
                        sArgs[ordinal] = value;
                    });
                }
            }

            @Override
            public Cell<Consumer<Object[]>> visitFunction(@NotNull LangParser.FunctionContext ctx) {
                String functionName = ctx.ID().getText();
                int functionDepth = depth + 1;

                ArrayList<VariableInfo> functionLocals = new ArrayList<>();
                if (ctx.parameters() != null)
                    functionLocals.addAll(ctx.parameters().ID().stream().map(x -> new VariableInfo(Object.class, x.getText(), 0)).collect(Collectors.toList()));

                ParserRuleContext bodyTree = ctx.expression();

                Function<Object[], Cell> cellBodyConstructor = parseExpression(bodyTree, new Hashtable<>(), functionLocals, functionDepth);

                // Compare in relation to the given function depth
                Stream<VariableInfo> parameters = functionLocals.stream().filter(x -> x.depth == functionDepth);
                Class<?>[] parameterTypes = parameters.map(x -> x.type).toArray(s -> new Class<?>[s]);

                return new Singleton<>(args -> {
                    if (atRoot) {
                        Binding existingBinding = functionBindings.get(new Selector(functionName, parameterTypes));
                        if (existingBinding != null)
                            existingBinding.remove();
                    }

                    Cell<Function<Object[], Object>> cellBody = (Cell<Function<Object[], Object>>)cellBodyConstructor.apply(args);
                    Binding binding = cellBody.consume(cellBodyExpression -> {
                        define(functionName, parameterTypes, functionLocals.size(), cellBodyExpression);
                    });

                    if (atRoot)
                        functionBindings.put(new Selector(functionName, parameterTypes), binding);
                });
            }

            @Override
            public Cell<Consumer<Object[]>> visitRelation(@NotNull LangParser.RelationContext ctx) {
                String relationName = ctx.ID().getText();

                ArrayList<VariableInfo> relationLocals = new ArrayList<>();
                if (ctx.parameters() != null)
                    relationLocals.addAll(ctx.parameters().ID().stream().map(x -> new VariableInfo(Object.class, x.getText(), 0)).collect(Collectors.toList()));

                List<Cell<Consumer<Object[]>>> statementCells = ctx.statement().stream().map(x -> parseStatement(x, relationLocals, 0, false)).collect(Collectors.toList());
                Stream<VariableInfo> parameters = relationLocals.stream().filter(x -> x.depth == 0);
                Class<?>[] parameterTypes = parameters.map(x -> x.type).toArray(s -> new Class<?>[s]);

                return new Cell<Consumer<Object[]>>() {
                    private Consumer<Object[]>[] statements = new Consumer[statementCells.size()];

                    @Override
                    public Binding consume(CellConsumer<Consumer<Object[]>> consumer) {
                        if (atRoot) {
                            Binding existingBinding = relationBindings.get(new Selector(relationName, parameterTypes));
                            if (existingBinding != null)
                                existingBinding.remove();
                        }

                        // Have a binding for each statement
                        List<Binding> bindings = IntStream.range(0, statements.length).mapToObj(i -> {
                            Cell<Consumer<Object[]>> statementCell = statementCells.get(i);
                            return statementCell.consume(statement -> {
                                statements[i] = statement;
                                update();
                            });
                        }).collect(Collectors.toList());
                        Binding binding = () -> bindings.forEach(x -> x.remove());

                        if (atRoot)
                            relationBindings.put(new Selector(relationName, parameterTypes), binding);

                        return binding;
                    }

                    private void update() {
                        if (Arrays.asList(statements).stream().allMatch(x -> x != null)) {
                            Consumer<Object[]> body = args ->
                                Arrays.asList(statements).forEach(x ->
                                    x.accept(args));
                            defineRelation(relationName, parameterTypes, relationLocals.size(), body);
                        }
                    }
                };
            }

            @Override
            public Cell<Consumer<Object[]>> visitRelationCall(@NotNull LangParser.RelationCallContext ctx) {
                String relationName = ctx.name.getText();

                java.util.List<Cell<Function<Object[], Object>>> argumentCells = ctx.id().stream().skip(1).map(x -> {
                    String argumentName = x.ID().getText();
                    int ordinal = localOrdinal(locals, argumentName, depth);

                    boolean isFromSelection = ordinal == -1;

                    if (isFromSelection) {
                        SlotComponent currentTarget = (SlotComponent) environment.get(argumentName);

                        Function<Object[], Object> expression = args -> currentTarget;
                        return new Singleton<>(expression);
                    } else {
                        Function<Object[], Object> expression = args -> args[ordinal];
                        return new Singleton<>(expression);
                    }
                }).collect(Collectors.toList());

                return createStatementRelationCall(relationName, argumentCells);
            }
        });
    }

    private void evaluateProgram(LangParser.ProgramContext programCtx) {
        nextOutX = 30;
        nextOutY = 30;

        programCtx.statement().stream().forEach(x -> {
            ArrayList<VariableInfo> locals = new ArrayList<VariableInfo>();
            Cell<Consumer<Object[]>> statementCell = parseStatement(x, locals, 0, true);
            statementCell.consume(statement -> {
                Object[] args = new Object[locals.size()];
                statement.accept(args);
            });
        });
    }

    private Function<Object[], Cell> createExpressionFunctionCall(String name, java.util.List<Function<Object[], Cell>> argumentExpressions, int depth) {
        return args -> {
            List<Cell> argumentCells = argumentExpressions.stream().map(x -> x.apply(args)).collect(Collectors.toList());

            return new Cell() {
                @Override
                public Binding consume(CellConsumer consumer) {
                    return new Binding() {
                        private GenericFunction functionBinding = getFunction(new GenericSelector(name, argumentCells.size()));
                        private Object[] expressionArguments = new Object[argumentCells.size()];
                        private Binding functionBindingBinding;
                        private java.util.List<Binding> bindings;
                        private Map<SpecificSelector, SpecificFunctionInfo> functions;

                        {
                            functionBindingBinding = functionBinding.consume(f -> {
                                this.functions = f;
                                update();
                            });
                            bindings = IntStream.range(0, argumentCells.size()).mapToObj(i -> argumentCells.get(i).consume(next -> {
                                expressionArguments[i] = next;
                                update();
                            })).collect(Collectors.toList());
                        }

                        private void update() {
                            if(Arrays.asList(expressionArguments).stream().filter(x -> x == null).count() == 0) {
                                if(depth == 0) {
                                    Object[] callArgs = expressionArguments;
                                    Class<?>[] parameterTypes = Arrays.asList(callArgs).stream().map(x -> x.getClass()).toArray(s -> new Class<?>[callArgs.length]);

                                    if (functions != null) {
                                        SpecificFunctionInfo function = GenericFunction.resolve(functions, parameterTypes);

                                        if (function != null) {
                                            Object[] locals = new Object[function.localCount];
                                            for (int i = 0; i < callArgs.length; i++) {
                                                if (callArgs[i] instanceof BlockClosure)
                                                    ((BlockClosure) callArgs[i]).setLocals(locals);
                                            }
                                            System.arraycopy(callArgs, 0, locals, 0, callArgs.length);

                                            Object next = function.body.apply(locals);
                                            consumer.next(next);
                                        }
                                    }
                                } else {
                                    Function<Object[], Object> next = args -> {
                                        Object[] callArgs = Arrays.asList(expressionArguments).stream().map(x -> ((Function<Object[], Object>) x).apply(args)).toArray();
                                        Class<?>[] parameterTypes = Arrays.asList(callArgs).stream().map(x -> x.getClass()).toArray(s -> new Class<?>[callArgs.length]);

                                        if(functions != null) {
                                            SpecificFunctionInfo function = GenericFunction.resolve(functions, parameterTypes);

                                            if(function != null) {
                                                Object[] locals = new Object[function.localCount];
                                                for(int i = 0; i < callArgs.length; i++) {
                                                    if(callArgs[i] instanceof BlockClosure)
                                                        ((BlockClosure) callArgs[i]).setLocals(locals);
                                                }
                                                System.arraycopy(callArgs, 0, locals, 0, callArgs.length);

                                                return function.body.apply(locals);
                                            }
                                        }

                                        return null;
                                    };

                                    consumer.next(next);
                                }
                            }
                        }

                        @Override
                        public void remove() {
                            functionBindingBinding.remove();
                            functionBindingBinding = null;
                            bindings.forEach(x -> x.remove());
                            expressionArguments = null;
                            bindings = null;
                        }
                    };
                }
            };
        };
    }

    private Cell<Consumer<Object[]>> createStatementRelationCall(String name, java.util.List<Cell<Function<Object[], Object>>> argumentCells) {
        return new Cell<Consumer<Object[]>>() {
            @Override
            public Binding consume(CellConsumer<Consumer<Object[]>> consumer) {
                return new Binding() {
                    private GenericRelation genericRelation = getRelation(new GenericSelector(name, argumentCells.size()));
                    private Function<Object[], Object>[] expressionArguments = (Function<Object[], Object>[])new Function[argumentCells.size()];
                    private Binding genericRelationBinding;
                    private java.util.List<Binding> bindings;
                    private Map<SpecificSelector, SpecificRelationInfo> specificRelations;

                    {
                        genericRelationBinding = genericRelation.consume(f -> {
                            this.specificRelations = f;
                            update();
                        });
                        bindings = IntStream.range(0, argumentCells.size()).mapToObj(i -> argumentCells.get(i).consume(next -> {
                            expressionArguments[i] = next;
                            update();
                        })).collect(Collectors.toList());
                    }

                    private void update() {
                        if(Arrays.asList(expressionArguments).stream().filter(x -> x == null).count() == 0) {
                            Consumer<Object[]> next = args -> {
                                Object[] callArgs = Arrays.asList(expressionArguments).stream().map(x -> x.apply(args)).toArray();
                                Class<?>[] parameterTypes = Arrays.asList(callArgs).stream().map(x -> x.getClass()).toArray(s -> new Class<?>[callArgs.length]);

                                if(specificRelations != null) {
                                    SpecificRelationInfo relation = GenericRelation.resolve(specificRelations, parameterTypes);

                                    if(relation != null) {
                                        Object[] locals = new Object[relation.localCount];
                                        for(int i = 0; i < callArgs.length; i++) {
                                            if(callArgs[i] instanceof BlockClosure)
                                                ((BlockClosure) callArgs[i]).setLocals(locals);
                                        }
                                        System.arraycopy(callArgs, 0, locals, 0, callArgs.length);

                                        relation.body.accept(locals);
                                    }
                                }
                            };

                            consumer.next(next);
                        }
                    }

                    @Override
                    public void remove() {
                        genericRelationBinding.remove();
                        genericRelationBinding = null;
                        bindings.forEach(x -> x.remove());
                        expressionArguments = null;
                        bindings = null;
                    }
                };
            }
        };
    }

    private Function<Object[], Cell> createExpressionBinaryOperation(String operator, Function<Object[], Cell> lhsCell, Function<Object[], Cell> rhsCell, int depth) {
        return createExpressionFunctionCall(operator, Arrays.asList(lhsCell, rhsCell), depth);
    }

    private int localOrdinal(ArrayList<VariableInfo> locals, String name, int depth) {
        // Find the last local with the parameterName
        return IntStream.range(0, locals.size()).boxed().sorted(((Comparator<Integer>)Integer::compare).reversed())
            .filter(i -> locals.get(i).name.equals(name))
            .findFirst()
            .orElseGet(() -> {
                // Check whether is selected
                if (selections.stream().anyMatch(x -> x.variableName.equals(name)))
                    return -1;

                locals.add(new VariableInfo(Object.class, name, depth));
                return locals.size() - 1;
            });
    }
}
