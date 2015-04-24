package enjuicer;

public class UseTool extends AbstractTool {
    public UseTool() {
        super("Use");
    }

    @Override
    public ToolSession startSession(int x, int y) {
        return NullToolSession.INSTANCE;
    }
}
