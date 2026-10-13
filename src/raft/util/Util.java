/**
 * Project   : Distributed Consensus Simulator (Raft-lite)
 * Author    : Shivansh Kumar (AP25110010113)
 * Section   : E | 2nd Year, 3rd Semester | SRM University AP
 * Portfolio : shivanshonline.in
 */
package raft.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Small helper methods. Contains a generic method with a wildcard parameter
 * (Unit 5: generic methods, wildcard arguments).
 */
public final class Util {
    private Util() {
    }

    /** Number of votes / acknowledgements needed for a majority. */
    public static int majority(int clusterSize) {
        return clusterSize / 2 + 1;
    }

    /** Generic method with a wildcard: joins any collection into one string. */
    public static String join(Collection<?> items, String separator) {
        StringBuilder sb = new StringBuilder();
        for (Object item : items) {
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(item);
        }
        return sb.toString();
    }

    /** Generic method: returns the first element of a list or a default value. */
    public static <T> T firstOr(List<? extends T> list, T defaultValue) {
        return list.isEmpty() ? defaultValue : list.get(0);
    }

    public static String shorten(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    /**
     * Parses a partition specification such as "1,2|3,4,5" into groups of node ids.
     *
     * @throws IllegalArgumentException if the text is malformed
     */
    public static List<Set<Integer>> parseGroups(String spec) {
        List<Set<Integer>> groups = new ArrayList<>();
        for (String part : spec.trim().split("\\|")) {
            Set<Integer> group = new HashSet<>();
            for (String token : part.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    group.add(Integer.parseInt(t));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a node id: '" + t + "'");
                }
            }
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        if (groups.size() < 2) {
            throw new IllegalArgumentException("Need at least two groups, e.g. 1,2|3,4,5");
        }
        return groups;
    }
}
