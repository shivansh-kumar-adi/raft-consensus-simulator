/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import raft.core.NodeSnapshot;
import raft.core.NodeState;

/** Draws the nodes on a circle; colour shows the role (custom painting with Java 2D). */
public class ClusterPanel extends JPanel {
    private static final Color LEADER = new Color(46, 160, 67);
    private static final Color FOLLOWER = new Color(66, 133, 244);
    private static final Color CANDIDATE = new Color(251, 140, 0);
    private static final Color CRASHED = new Color(110, 110, 110);

    private List<NodeSnapshot> nodes = new ArrayList<>();
    private int[] groups = new int[0];
    private String headline = "";

    public ClusterPanel() {
        setBackground(Color.WHITE);
    }

    /** @param groupOf partition group per node (index = id-1), -1 when there is no partition */
    public void update(List<NodeSnapshot> snapshots, int[] groupOf, String headline) {
        this.nodes = snapshots;
        this.groups = groupOf;
        this.headline = headline;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        g.setColor(new Color(40, 40, 40));
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(headline, 14, 24);
        int n = nodes.size();
        if (n == 0) {
            return;
        }

        long maxTerm = 0;
        NodeSnapshot leader = null;
        for (NodeSnapshot s : nodes) {
            if (!s.crashed) {
                maxTerm = Math.max(maxTerm, s.term);
            }
        }
        for (NodeSnapshot s : nodes) {
            if (!s.crashed && s.state == NodeState.LEADER && (leader == null || s.term > leader.term)) {
                leader = s; // the leader with the highest term is the "real" one
            }
        }

        int cx = w / 2;
        int cy = h / 2 + 10;
        int radius = (int) (Math.min(w, h) * 0.34);
        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / n;
            px[i] = cx + (int) (radius * Math.cos(angle));
            py[i] = cy + (int) (radius * Math.sin(angle));
        }

        // lines from the leader to the others
        if (leader != null) {
            for (int i = 0; i < n; i++) {
                NodeSnapshot s = nodes.get(i);
                if (s.id == leader.id) {
                    continue;
                }
                boolean cut = s.crashed || (groups.length > i && groups[i] != groups[leader.id - 1]);
                g.setColor(cut ? new Color(220, 80, 80) : new Color(170, 170, 170));
                g.setStroke(cut ? new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                        new float[]{6f, 6f}, 0f) : new BasicStroke(2f));
                g.drawLine(px[leader.id - 1], py[leader.id - 1], px[i], py[i]);
            }
        }
        g.setStroke(new BasicStroke(1f));

        int r = 36;
        for (int i = 0; i < n; i++) {
            NodeSnapshot s = nodes.get(i);
            Color c = s.crashed ? CRASHED
                    : s.state == NodeState.LEADER ? LEADER
                    : s.state == NodeState.CANDIDATE ? CANDIDATE : FOLLOWER;
            g.setColor(c);
            g.fillOval(px[i] - r, py[i] - r, 2 * r, 2 * r);
            if (!s.crashed && s.state == NodeState.LEADER && s.term < maxTerm) {
                g.setColor(Color.RED); // stale leader
                g.setStroke(new BasicStroke(3f));
                g.drawOval(px[i] - r - 3, py[i] - r - 3, 2 * r + 6, 2 * r + 6);
                g.setStroke(new BasicStroke(1f));
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            center(g, "N" + s.id, px[i], py[i] - 6);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            center(g, s.crashed ? "CRASHED" : s.state.getLabel(), px[i], py[i] + 8);
            center(g, "term " + s.term, px[i], py[i] + 22);

            g.setColor(new Color(60, 60, 60));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String info = "log " + s.log.size() + " | commit " + s.commitIndex;
            if (groups.length > i && groups[i] >= 0) {
                info += " | group " + (groups[i] + 1);
            }
            center(g, info, px[i], py[i] + r + 14);
        }

        // legend
        int lx = 14;
        int ly = h - 16;
        legend(g, lx, ly, LEADER, "Leader");
        legend(g, lx + 80, ly, FOLLOWER, "Follower");
        legend(g, lx + 170, ly, CANDIDATE, "Candidate");
        legend(g, lx + 270, ly, CRASHED, "Crashed");
        g.setColor(new Color(220, 80, 80));
        g.drawString("red ring = stale leader", lx + 350, ly + 4);
    }

    private void legend(Graphics2D g, int x, int y, Color c, String text) {
        g.setColor(c);
        g.fillOval(x, y - 6, 12, 12);
        g.setColor(new Color(60, 60, 60));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(text, x + 16, y + 4);
    }

    private void center(Graphics2D g, String text, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x - fm.stringWidth(text) / 2, y + fm.getAscent() / 2);
    }
}
