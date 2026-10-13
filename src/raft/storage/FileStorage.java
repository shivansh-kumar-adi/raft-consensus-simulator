/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import raft.core.LogEntry;
import raft.exception.StorageException;

/**
 * Stores each node in two small text files inside a folder:
 *   node-N.state  -> term and votedFor
 *   node-N.log    -> one line per entry: index TAB term TAB url-encoded-command
 * Uses java.io readers/writers (Unit 3: Input/Output).
 */
public class FileStorage implements Storage {
    private final File dir;

    public FileStorage(String dirPath) {
        this.dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new StorageException("Cannot create folder " + dirPath);
        }
    }

    private File stateFile(int id) {
        return new File(dir, "node-" + id + ".state");
    }

    private File logFile(int id) {
        return new File(dir, "node-" + id + ".log");
    }

    @Override
    public synchronized void saveState(int nodeId, long currentTerm, int votedFor) {
        try (PrintWriter out = new PrintWriter(new FileWriter(stateFile(nodeId)))) {
            out.println(currentTerm);
            out.println(votedFor);
        } catch (IOException e) {
            throw new StorageException("Cannot write state of node " + nodeId, e);
        }
    }

    @Override
    public synchronized PersistentState loadState(int nodeId) {
        File f = stateFile(nodeId);
        if (!f.exists()) {
            return null;
        }
        try (BufferedReader in = new BufferedReader(new FileReader(f))) {
            long term = Long.parseLong(in.readLine().trim());
            int votedFor = Integer.parseInt(in.readLine().trim());
            return new PersistentState(term, votedFor);
        } catch (IOException | NumberFormatException | NullPointerException e) {
            throw new StorageException("Cannot read state of node " + nodeId, e);
        }
    }

    @Override
    public synchronized void saveLog(int nodeId, List<LogEntry<String>> entries) {
        try (PrintWriter out = new PrintWriter(new FileWriter(logFile(nodeId)))) {
            for (LogEntry<String> e : entries) {
                out.println(e.getIndex() + "\t" + e.getTerm() + "\t"
                        + URLEncoder.encode(e.getCommand(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new StorageException("Cannot write log of node " + nodeId, e);
        }
    }

    @Override
    public synchronized List<LogEntry<String>> loadLog(int nodeId) {
        List<LogEntry<String>> result = new ArrayList<>();
        File f = logFile(nodeId);
        if (!f.exists()) {
            return result;
        }
        try (BufferedReader in = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                result.add(new LogEntry<>(Integer.parseInt(parts[0]), Long.parseLong(parts[1]),
                        URLDecoder.decode(parts[2], StandardCharsets.UTF_8)));
            }
        } catch (IOException | NumberFormatException e) {
            throw new StorageException("Cannot read log of node " + nodeId, e);
        }
        return result;
    }

    @Override
    public synchronized void clear() {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.getName().startsWith("node-")) {
                f.delete();
            }
        }
    }

    @Override
    public String name() {
        return "file (" + dir.getPath() + ")";
    }
}
