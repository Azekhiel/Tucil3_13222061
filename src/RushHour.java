import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class RushHour {
    enum Direction { UP, DOWN, LEFT, RIGHT }

    static class Move {
        char piece;
        Direction dir;
        Move(char piece, Direction dir) {
            this.piece = piece;
            this.dir = dir;
        }
        @Override
        public String toString() {
            return piece + "-" + dir;
        }
    }

    static class State {
        char[][] board;
        int rows, cols;
        State(char[][] b) {
            rows = b.length;
            cols = b[0].length;
            board = new char[rows][cols];
            for (int i = 0; i < rows; i++)
                System.arraycopy(b[i], 0, board[i], 0, cols);
        }
        @Override
        public boolean equals(Object o) {
            return (o instanceof State) && Arrays.deepEquals(board, ((State)o).board);
        }
        @Override
        public int hashCode() {
            return Arrays.deepHashCode(board);
        }
    }

    static class Node {
        State state;
        Node parent;
        Move move;
        int cost;
        double h;
        Node(State s, Node p, Move m, int cost, double h) {
            this.state = s;
            this.parent = p;
            this.move = m;
            this.cost = cost;
            this.h = h;
        }
        double f() { return cost + h; }
    }

    interface Heuristic { double eval(State s, Exit exit); }

    static class Manhattan implements Heuristic {
        public double eval(State s, Exit exit) {
            for (int i = 0; i < s.rows; i++)
                for (int j = 0; j < s.cols; j++)
                    if (s.board[i][j] == 'P')
                        return Math.abs(i - exit.r) + Math.abs(j - exit.c);
            return Double.MAX_VALUE;
        }
    }

    static class Euclidean implements Heuristic {
        public double eval(State s, Exit exit) {
            for (int i = 0; i < s.rows; i++)
                for (int j = 0; j < s.cols; j++)
                    if (s.board[i][j] == 'P')
                        return Math.hypot(i - exit.r, j - exit.c);
            return Double.MAX_VALUE;
        }
    }

    static class Exit { int r, c; }

    static Map<Character, Boolean> orientation = new HashMap<>();

    static char[][] parseBoard(String filename, Exit exit) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String[] dims = br.readLine().trim().split("\\s+");
            int A = Integer.parseInt(dims[0]), B = Integer.parseInt(dims[1]);
            br.readLine();
            char[][] board = new char[A][B];
            for (int i = 0; i < A; i++) {
                String line = br.readLine();
                if (line.length() == B) {
                    board[i] = line.toCharArray();
                } else if (line.length() == B + 1 && line.indexOf('K') != -1) {
                    int kpos = line.indexOf('K');
                    exit.r = i;
                    if (kpos == 0) {
                        exit.c = -1;
                        for (int j = 0; j < B; j++) board[i][j] = line.charAt(j + 1);
                    } else if (kpos == B) {
                        exit.c = B;
                        for (int j = 0; j < B; j++) board[i][j] = line.charAt(j);
                    } else {
                        throw new IllegalArgumentException("Invalid K position at row " + i);
                    }
                } else {
                    throw new IllegalArgumentException("Row " + i + " length invalid");
                }
            }
            detectOrientation(board);
            return board;
        }
    }

    static void detectOrientation(char[][] board) {
        int R = board.length, C = board[0].length;
        Map<Character, List<int[]>> map = new HashMap<>();
        for (int i = 0; i < R; i++)
            for (int j = 0; j < C; j++) {
                char ch = board[i][j];
                if (ch == '.' || ch == 'K') continue;
                map.computeIfAbsent(ch, k -> new ArrayList<>()).add(new int[]{i, j});
            }
        for (Map.Entry<Character, List<int[]>> e : map.entrySet()) {
            char ch = e.getKey();
            List<int[]> coords = e.getValue();
            boolean sameRow = coords.stream().allMatch(rc -> rc[0] == coords.get(0)[0]);
            boolean sameCol = coords.stream().allMatch(rc -> rc[1] == coords.get(0)[1]);
            if (!(sameRow || sameCol))
                throw new IllegalArgumentException("Piece '" + ch + "' not linear");
            orientation.put(ch, sameRow);
        }
    }

    static boolean isGoal(State s, Exit exit) {
        if (exit.c == -1 || exit.c == s.cols) {
            int j = exit.c == -1 ? 0 : s.cols - 1;
            return s.board[exit.r][j] == 'P';
        } else {
            return s.board[exit.r][exit.c] == 'P';
        }
    }

    static List<Node> expand(Node node, Exit exit, Heuristic hfunc) {
        List<Node> neigh = new ArrayList<>();
        State s = node.state;
        for (int i = 0; i < s.rows; i++) {
            for (int j = 0; j < s.cols; j++) {
                char p = s.board[i][j];
                if (p == '.' || p == 'K') continue;
                for (Direction d : Direction.values()) {
                    boolean horiz = orientation.get(p);
                    if ((horiz && (d == Direction.UP || d == Direction.DOWN)) ||
                        (!horiz && (d == Direction.LEFT || d == Direction.RIGHT)))
                        continue;
                    State nxt = movePiece(s, exit, p, d);
                    if (nxt != null) {
                        double h = (hfunc != null) ? hfunc.eval(nxt, exit) : 0;
                        neigh.add(new Node(nxt, node, new Move(p, d), node.cost + 1, h));
                    }
                }
            }
        }
        return neigh;
    }

    static State movePiece(State s, Exit exit, char p, Direction dir) {
        int di = dir == Direction.DOWN ? 1 : dir == Direction.UP ? -1 : 0;
        int dj = dir == Direction.RIGHT ? 1 : dir == Direction.LEFT ? -1 : 0;
        char[][] b = new char[s.rows][s.cols];
        for (int r = 0; r < s.rows; r++) System.arraycopy(s.board[r], 0, b[r], 0, s.cols);

        List<int[]> coords = new ArrayList<>();
        for (int r = 0; r < s.rows; r++)
            for (int c = 0; c < s.cols; c++)
                if (s.board[r][c] == p) coords.add(new int[]{r, c});

        int br = coords.stream().mapToInt(rc -> rc[0])
                .reduce((a, b1) -> di > 0 ? Math.max(a, b1) : di < 0 ? Math.min(a, b1) : a)
                .getAsInt();
        int bc = coords.stream().mapToInt(rc -> rc[1])
                .reduce((a, b1) -> dj > 0 ? Math.max(a, b1) : dj < 0 ? Math.min(a, b1) : a)
                .getAsInt();

        int nr = br + di, nc = bc + dj;
        boolean toExit = (nr == exit.r && nc == exit.c);
        if (nr < 0 || nr >= s.rows || nc < 0 || nc >= s.cols) {
            if (!(p == 'P' && toExit)) return null;
        } else if (s.board[nr][nc] != '.') {
            return null;
        }

        for (int[] rc : coords) b[rc[0]][rc[1]] = '.';
        for (int[] rc : coords) {
            int r2 = rc[0] + di, c2 = rc[1] + dj;
            if (r2 >= 0 && r2 < s.rows && c2 >= 0 && c2 < s.cols) {
                b[r2][c2] = p;
            }
        }
        return new State(b);
    }

    static Node search(State start, Exit exit, Heuristic hfunc, String algo) {
        Comparator<Node> comp;
        switch (algo) {
            case "UCS":
                comp = Comparator.comparingInt(n -> n.cost);
                break;
            case "Greedy":
                comp = Comparator.comparingDouble(n -> n.h);
                break;
            default:
                comp = Comparator.comparingDouble(Node::f);
                break;
        }
        PriorityQueue<Node> frontier = new PriorityQueue<>(comp);
        frontier.add(new Node(start, null, null, 0, (hfunc != null ? hfunc.eval(start, exit) : 0)));
        Set<State> seen = new HashSet<>();

        while (!frontier.isEmpty()) {
            Node cur = frontier.poll();
            if (seen.contains(cur.state)) continue;
            seen.add(cur.state);
            if (isGoal(cur.state, exit)) return cur;
            frontier.addAll(expand(cur, exit, hfunc));
        }
        return null;
    }

    static Node backtrack(Node node, Exit exit, Set<State> visited) {
        if (isGoal(node.state, exit)) {
            return node;
        }
        visited.add(node.state);
        Node best = null;

        for (Node child : expand(node, exit, null)) {
            if (visited.contains(child.state)) continue;
            Node sol = backtrack(child, exit, visited);
            if (sol != null) {
                if (best == null || sol.cost < best.cost) {
                    best = sol;
                }
            }
        }

        visited.remove(node.state);
        return best;
    }

    static void printSolution(Node goal, PrintWriter pw) {
        List<Node> path = new ArrayList<>();
        for (Node n = goal; n != null; n = n.parent) path.add(n);
        Collections.reverse(path);

        pw.println("Papan Awal:");
        System.out.println("Papan Awal:");
        printBoard(path.get(0).state, pw);
        printBoard(path.get(0).state);
        for (int i = 1; i < path.size(); i++) {
            String header = "\nGerakan " + i + ": " + path.get(i).move;
            pw.println(header);
            System.out.println(header);
            printBoard(path.get(i).state, pw);
            printBoard(path.get(i).state);
        }
    }

    static void printBoard(State s, PrintWriter pw) {
        for (char[] row : s.board) {
            pw.println(new String(row));
        }
    }

    static void printBoard(State s) {
        for (char[] row : s.board) {
            System.out.println(new String(row));
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner in = new Scanner(System.in);
        System.out.print("Masukkan nama file input: ");
        String fn = in.nextLine();

        Exit exit = new Exit();
        char[][] board = parseBoard(fn, exit);
        State start = new State(board);

        System.out.println("Pilih algoritma:");
        System.out.println(" 1. Greedy Best-First Search");
        System.out.println(" 2. Uniform-Cost Search (UCS)");
        System.out.println(" 3. A* Search");
        System.out.println(" 4. Backtracking (recursive)");
        int ch = in.nextInt();

        Heuristic h = null;
        String algo;
        if (ch == 1 || ch == 3) {
            System.out.print("Pilih heuristik: 1. Manhattan  2. Euclidean: ");
            h = (in.nextInt() == 1) ? new Manhattan() : new Euclidean();
        }
        algo = (ch == 1 ? "Greedy" : ch == 2 ? "UCS" : ch == 3 ? "A*" : "Backtracking");

        long startTime = System.nanoTime();
        Node sol;
        if (ch == 4) {
            Set<State> visited = new HashSet<>();
            sol = backtrack(new Node(start, null, null, 0, 0), exit, visited);
        } else {
            sol = search(start, exit, h, algo);
        }
        long endTime = System.nanoTime();

        String outFile = "solusi_" + fn;
        try (PrintWriter pw = new PrintWriter(outFile)) {
            if (sol != null) {
                System.out.println("\nSolusi Ditemukan! (" + algo + ")");
                pw.println("Solusi Ditemukan! (" + algo + ")");
                printSolution(sol, pw);
            } else {
                System.out.println("Tidak ada solusi.");
                pw.println("Tidak ada solusi.");
            }
            double elapsed = (endTime - startTime) / 1e6;
            String timeMsg = String.format("Waktu eksekusi: %.2f ms", elapsed);
            System.out.println(timeMsg);
            pw.println(timeMsg);
        }

        in.close();
    }
}