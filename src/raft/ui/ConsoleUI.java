/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.ui;

import java.util.List;
import java.util.Scanner;
import java.util.Set;
import raft.exception.NodeCrashedException;
import raft.exception.NotLeaderException;
import raft.sim.Cluster;

/** Command line interface: type commands to control and observe the cluster. */
public class ConsoleUI {
    private final Cluster cluster;

    public ConsoleUI(Cluster cluster) {
        this.cluster = cluster;
    }

    public void run() {
        System.out.println();
        System.out.println("Raft-lite console. Type 'help' for commands, 'quit' to exit.");
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("raft> ");
            if (!in.hasNextLine()) {
                break;
            }
            String line = in.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                break;
            }
            try {
                execute(line);
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void execute(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";
        switch (cmd) {
            case "help":
                printHelp();
                break;
            case "status":
                System.out.println(StatusFormatter.leaderLine(cluster));
                System.out.print(StatusFormatter.table(cluster));
                break;
            case "logs":
                System.out.print(StatusFormatter.logs(cluster));
                break;
            case "send":
                send(arg);
                break;
            case "crash":
                crash(arg);
                break;
            case "recover":
                recover(arg);
                break;
            case "partition":
                List<Set<Integer>> groups = raft.util.Util.parseGroups(arg);
                cluster.partition(groups);
                break;
            case "heal":
                cluster.heal();
                break;
            case "drop":
                cluster.getNetwork().setDropRate(parseNumber(arg) / 100.0);
                System.out.println("Message loss set to " + arg + "%");
                break;
            case "delay":
                String[] d = arg.split("\\s+");
                if (d.length != 2) {
                    throw new IllegalArgumentException("usage: delay <minMs> <maxMs>");
                }
                cluster.getNetwork().setDelay(parseNumber(d[0]), parseNumber(d[1]));
                break;
            case "trace":
                cluster.getNetwork().setTrace(arg.equalsIgnoreCase("on"));
                System.out.println("Message tracing " + (arg.equalsIgnoreCase("on") ? "ON" : "OFF"));
                break;
            default:
                System.out.println("Unknown command '" + cmd + "'. Type 'help'.");
        }
    }

    private void send(String command) {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("usage: send <command>   e.g. send SET x 5");
        }
        try {
            int index = cluster.submit(command);
            System.out.println("Accepted by leader at log index " + index + " (will commit once a majority has it)");
        } catch (NotLeaderException | NodeCrashedException e) {
            System.out.println("Command rejected: " + e.getMessage());
        }
    }

    private void crash(String arg) {
        if (arg.equalsIgnoreCase("leader")) {
            int id = cluster.crashLeader();
            if (id < 0) {
                System.out.println("There is no leader to crash.");
            }
        } else {
            cluster.crash(parseNumber(arg));
        }
    }

    private void recover(String arg) {
        if (arg.equalsIgnoreCase("all")) {
            for (int id = 1; id <= cluster.size(); id++) {
                cluster.recover(id);
            }
        } else {
            cluster.recover(parseNumber(arg));
        }
    }

    private int parseNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a number");
        }
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  status               show every node (role, term, log, commit, state machine)");
        System.out.println("  logs                 show the full log of every node");
        System.out.println("  send <command>       client command to the leader: SET x 5 | x=5 | DEL x");
        System.out.println("  crash <id|leader>    crash a node");
        System.out.println("  recover <id|all>     restart crashed node(s)");
        System.out.println("  partition 1,2|3,4,5  split the network into groups");
        System.out.println("  heal                 repair the network");
        System.out.println("  drop <percent>       random message loss, e.g. drop 20");
        System.out.println("  delay <min> <max>    network delay range in ms");
        System.out.println("  trace on|off         print every message");
        System.out.println("  quit                 stop the simulator");
    }
}
