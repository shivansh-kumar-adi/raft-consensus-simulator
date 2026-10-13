/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import raft.core.NodeSnapshot;
import raft.exception.NodeCrashedException;
import raft.exception.NotLeaderException;
import raft.sim.Cluster;
import raft.util.Util;

/**
 * Graphical dashboard built with Swing/AWT (part of the JDK, no extra
 * libraries). Uses the AWT delegation event model: every button gets an
 * ActionListener, and WindowAdapter handles the window closing (Unit 3).
 */
public class SwingDashboard extends JFrame {
    private final Cluster cluster;
    private final ClusterPanel clusterPanel = new ClusterPanel();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Node", "Role", "Term", "Voted for", "Leader", "Log", "Commit", "Applied", "State machine"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextArea logsArea = new JTextArea();
    private final JTextArea eventsArea = new JTextArea();
    private final JComboBox<Integer> nodeBox = new JComboBox<>();
    private final JTextField commandField = new JTextField(22);
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel lossLabel = new JLabel("Message loss: 0%");
    private String lastLogsText = "";

    public SwingDashboard(Cluster cluster) {
        super("Distributed Consensus Simulator (Raft-lite) - Shivansh Kumar, AP25110010113");
        this.cluster = cluster;
        buildUi();

        cluster.getEvents().addListener(line -> SwingUtilities.invokeLater(() -> appendEvent(line)));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cluster.stop();
                dispose();
                System.exit(0);
            }
        });
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        Timer refresh = new Timer(200, e -> refresh());
        refresh.start();
    }

    private void buildUi() {
        for (int id = 1; id <= cluster.size(); id++) {
            nodeBox.addItem(id);
        }

        // ---------- controls ----------
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row1.add(new JLabel("Node:"));
        row1.add(nodeBox);
        row1.add(button("Crash node", e -> {
            cluster.crash((Integer) nodeBox.getSelectedItem());
        }));
        row1.add(button("Recover node", e -> {
            cluster.recover((Integer) nodeBox.getSelectedItem());
        }));
        row1.add(button("Crash leader", e -> {
            if (cluster.crashLeader() < 0) {
                setStatus("There is no leader to crash.");
            }
        }));
        row1.add(button("Recover all", e -> {
            for (int id = 1; id <= cluster.size(); id++) {
                cluster.recover(id);
            }
        }));
        row1.add(new JLabel("   Partition:"));
        row1.add(button("Split...", e -> askPartition()));
        row1.add(button("Heal", e -> cluster.heal()));

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row2.add(new JLabel("Client command:"));
        row2.add(commandField);
        ActionListener send = e -> sendCommand();
        commandField.addActionListener(send); // Enter key
        row2.add(button("Send to leader", send));
        row2.add(new JLabel("   (examples: SET x 5 | y=7 | DEL x)"));
        JSlider loss = new JSlider(0, 60, 0);
        loss.setPreferredSize(new Dimension(140, 24));
        loss.addChangeListener(e -> {
            cluster.getNetwork().setDropRate(loss.getValue() / 100.0);
            lossLabel.setText("Message loss: " + loss.getValue() + "%");
        });
        row2.add(lossLabel);
        row2.add(loss);

        JPanel controls = new JPanel(new GridLayout(3, 1));
        controls.add(row1);
        controls.add(row2);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        controls.add(statusLabel);
        controls.setBorder(BorderFactory.createTitledBorder("Controls"));

        // ---------- views ----------
        JTable table = new JTable(tableModel);
        table.setRowHeight(22);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Node status"));

        logsArea.setEditable(false);
        logsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logsScroll = new JScrollPane(logsArea);
        logsScroll.setBorder(BorderFactory.createTitledBorder("Replicated logs"));

        eventsArea.setEditable(false);
        eventsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane eventsScroll = new JScrollPane(eventsArea);
        eventsScroll.setBorder(BorderFactory.createTitledBorder("Event log"));

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logsScroll);
        right.setResizeWeight(0.35);
        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, clusterPanel, right);
        top.setResizeWeight(0.45);
        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, eventsScroll);
        main.setResizeWeight(0.68);

        setLayout(new BorderLayout());
        add(controls, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);
        setSize(1280, 820);
        setLocationRelativeTo(null);
    }

    private JButton button(String text, ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        return b;
    }

    private void sendCommand() {
        String command = commandField.getText().trim();
        if (command.isEmpty()) {
            return;
        }
        try {
            int index = cluster.submit(command);
            setStatus("Command accepted at log index " + index + ".");
            commandField.setText("");
        } catch (NotLeaderException | NodeCrashedException ex) {
            setStatus("Rejected: " + ex.getMessage());
        }
    }

    private void askPartition() {
        String spec = JOptionPane.showInputDialog(this,
                "Enter groups separated by | (example for 5 nodes: 1,2|3,4,5)", "1,2|3,4,5");
        if (spec == null) {
            return;
        }
        try {
            List<Set<Integer>> groups = Util.parseGroups(spec);
            cluster.partition(groups);
        } catch (IllegalArgumentException ex) {
            setStatus("Bad partition: " + ex.getMessage());
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void appendEvent(String line) {
        eventsArea.append(line + "\n");
        if (eventsArea.getLineCount() > 600) {
            try {
                eventsArea.getDocument().remove(0, eventsArea.getLineEndOffset(100));
            } catch (BadLocationException ignored) {
                // cannot happen: offsets come from the document itself
            }
        }
        eventsArea.setCaretPosition(eventsArea.getDocument().getLength());
    }

    private void refresh() {
        List<NodeSnapshot> snapshots = cluster.snapshots();
        int[] groups = new int[snapshots.size()];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = cluster.getNetwork().groupOfNode(i + 1);
        }
        String headline = StatusFormatter.leaderLine(cluster);
        if (cluster.getNetwork().isPartitioned()) {
            headline += "   | partition " + cluster.getNetwork().describePartition();
        }
        clusterPanel.update(snapshots, groups, headline);

        tableModel.setRowCount(0);
        for (NodeSnapshot s : snapshots) {
            tableModel.addRow(new Object[]{"N" + s.id, s.roleText(), s.term,
                    s.votedFor < 0 ? "-" : "N" + s.votedFor,
                    s.leaderId < 0 ? "-" : "N" + s.leaderId,
                    s.log.size(), s.commitIndex, s.lastApplied, s.stateMachine});
        }
        String logs = StatusFormatter.logs(cluster);
        if (!logs.equals(lastLogsText)) {
            lastLogsText = logs;
            logsArea.setText(logs);
            logsArea.setCaretPosition(0);
        }
    }

    /** Opens the dashboard on the Swing thread. */
    public static void launch(Cluster cluster) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel is fine
            }
            new SwingDashboard(cluster).setVisible(true);
        });
    }
}
