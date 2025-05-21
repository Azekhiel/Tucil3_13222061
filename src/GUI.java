import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import javax.swing.Timer;
import java.io.*;
import java.util.*;

public class GUI extends JFrame {
    private JPanel boardPanel;
    private JButton loadButton, solveButton;
    private JComboBox<String> algoBox, heuristicBox;
    private File currentFile;
    private java.util.List<char[][]> solutionStates;
    private Timer animationTimer;
    private int animIndex = 0;

    public GUI() {
        super("Rush Hour Solver");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 700);
        setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel();
        loadButton = new JButton("Load Board");
        solveButton = new JButton("Solve");
        solveButton.setEnabled(false);
        algoBox = new JComboBox<>(new String[]{"Greedy","UCS","A*","Backtracking"});
        heuristicBox = new JComboBox<>(new String[]{"Manhattan","Euclidean"});
        heuristicBox.setEnabled(false);

        controlPanel.add(loadButton);
        controlPanel.add(new JLabel("Algorithm:"));
        controlPanel.add(algoBox);
        controlPanel.add(new JLabel("Heuristic:"));
        controlPanel.add(heuristicBox);
        controlPanel.add(solveButton);
        add(controlPanel, BorderLayout.NORTH);

        boardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (solutionStates != null && animIndex < solutionStates.size()) {
                    drawBoard(g, solutionStates.get(animIndex));
                }
            }
        };
        add(boardPanel, BorderLayout.CENTER);

        loadButton.addActionListener(e -> loadBoardFile());
        algoBox.addActionListener(e -> {
            boolean needHeuristic = algoBox.getSelectedIndex() == 0 || algoBox.getSelectedIndex() == 2;
            heuristicBox.setEnabled(needHeuristic);
        });
        solveButton.addActionListener(e -> startSolve());

        setVisible(true);
    }

    private void loadBoardFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            solveButton.setEnabled(true);
        }
    }

    private void startSolve() {
        try {
            RushHour.Exit exit = new RushHour.Exit();
            char[][] board = RushHour.parseBoard(currentFile.getAbsolutePath(), exit);
            RushHour.State start = new RushHour.State(board);

            String algo = (String) algoBox.getSelectedItem();
            RushHour.Heuristic h = null;
            if (algo.equals("Greedy") || algo.equals("A*")) {
                String hname = (String) heuristicBox.getSelectedItem();
                h = hname.equals("Manhattan") ? new RushHour.Manhattan() : new RushHour.Euclidean();
            }
            
            long startTime = System.nanoTime();
            RushHour.Node goal;
            if (algo.equals("Backtracking")) {
                goal = RushHour.backtrack(new RushHour.Node(start, null, null, 0, 0), exit, new HashSet<>());
            } else {
                goal = RushHour.search(start, exit, h, algo);
            }
            long endTime = System.nanoTime();
            double elapsedMs = (endTime - startTime) / 1e6;

            if (goal == null) {
                JOptionPane.showMessageDialog(this, String.format("No solution found. (%.2f ms)", elapsedMs));
                return;
            }
            JOptionPane.showMessageDialog(this, String.format("Solution found in %.2f ms.", elapsedMs));

            solutionStates = new ArrayList<>();
            RushHour.Node n = goal;
            while (n != null) {
                solutionStates.add(0, n.state.board);
                n = n.parent;
            }
            animIndex = 0;
            if (animationTimer != null && animationTimer.isRunning()) animationTimer.stop();
            animationTimer = new Timer(800, e -> animateStep());
            animationTimer.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error loading board: " + ex.getMessage());
        }
    }

    private void animateStep() {
        if (animIndex >= solutionStates.size()) {
            animationTimer.stop();
            return;
        }
        boardPanel.repaint();
        animIndex++;
    }

    private void drawBoard(Graphics g, char[][] b) {
        int rows = b.length, cols = b[0].length;
        int w = boardPanel.getWidth(), h = boardPanel.getHeight();
        int cellW = w / cols, cellH = h / rows;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(j*cellW, i*cellH, cellW, cellH);
                g.setColor(Color.BLACK);
                g.drawRect(j*cellW, i*cellH, cellW, cellH);
                char ch = b[i][j];
                if (ch != '.' ) {
                    g.setFont(new Font("Arial", Font.BOLD, cellH/2));
                    g.drawString(String.valueOf(ch), j*cellW + cellW/3, i*cellH + cellH*2/3);
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GUI::new);
    }
}
