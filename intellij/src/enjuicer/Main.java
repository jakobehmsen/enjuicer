package enjuicer;


import javax.swing.*;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        MainView view = new MainView(Arrays.asList(
            /*new LineTool(),
            new RectTool(),
            new NumberTool(),
            new TextTool(),*/
            new UseTool(),
            new MarkTool(),
            new InspectTool(),
            new MoveTool()
        ));

        view.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        view.setSize(800, 600);
        view.setLocationRelativeTo(null);
        view.setVisible(true);
    }
}
