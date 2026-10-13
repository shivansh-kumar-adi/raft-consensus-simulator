/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft;

import java.awt.GraphicsEnvironment;
import raft.core.RaftConfig;
import raft.sim.Cluster;
import raft.sim.DemoScenario;
import raft.storage.Storage;
import raft.storage.StorageFactory;
import raft.ui.ConsoleUI;
import raft.ui.SwingDashboard;
import raft.util.EventLog;

/**
 * Entry point.
 *
 * Options:
 *   --nodes N            number of nodes (default 5, minimum 1, maximum 9)
 *   --gui | --cli | --demo   choose the interface (default: gui if a screen exists, else cli)
 *   --storage TYPE       auto (default) | sqlite | file | memory
 *   --data DIR           folder for saved data (default: data)
 *   --resume             keep data from the previous run instead of starting fresh
 *   --fast               use very short timeouts
 */
public class Main {
    public static void main(String[] args) throws Exception {
        int nodes = 5;
        String mode = null;
        String storageType = "auto";
        String dataDir = "data";
        boolean resume = false;
        boolean fast = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--nodes":
                    nodes = Integer.parseInt(args[++i]);
                    break;
                case "--gui":
                case "--cli":
                case "--demo":
                    mode = args[i].substring(2);
                    break;
                case "--storage":
                    storageType = args[++i];
                    break;
                case "--data":
                    dataDir = args[++i];
                    break;
                case "--resume":
                    resume = true;
                    break;
                case "--fast":
                    fast = true;
                    break;
                case "--help":
                case "-h":
                    System.out.println("See the header of Main.java or README.md for the options.");
                    return;
                default:
                    System.out.println("Unknown option " + args[i] + " (use --help)");
                    return;
            }
        }
        if (nodes < 1 || nodes > 9) {
            System.out.println("--nodes must be between 1 and 9");
            return;
        }
        if (mode == null) {
            mode = GraphicsEnvironment.isHeadless() ? "cli" : "gui";
        }

        EventLog events = new EventLog();
        events.setConsoleEcho(!mode.equals("gui"));
        Storage storage = StorageFactory.create(storageType, dataDir, events);
        if (!resume) {
            storage.clear();
        }
        Cluster cluster = new Cluster(nodes, fast ? RaftConfig.fast() : RaftConfig.demo(), storage, events);
        cluster.start();

        switch (mode) {
            case "gui":
                SwingDashboard.launch(cluster);
                break; // the Swing window keeps the program alive
            case "demo":
                boolean ok = new DemoScenario(cluster).run();
                cluster.stop();
                System.exit(ok ? 0 : 1);
                break;
            default:
                new ConsoleUI(cluster).run();
                cluster.stop();
                System.exit(0);
        }
    }
}
