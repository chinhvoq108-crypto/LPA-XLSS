package lpa.sequential;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Single-threaded synchronous LPA, matching the project's deterministic Reducer. */
public final class SequentialLPA {
    private SequentialLPA() { }

    private static final class RawNode {
        final int id;
        final int label;
        final String neighborText;
        final int[] neighbors;
        final Set<Integer> neighborSet;

        RawNode(int id, int label, String neighborText, int[] neighbors,
                Set<Integer> neighborSet) {
            this.id = id;
            this.label = label;
            this.neighborText = neighborText;
            this.neighbors = neighbors;
            this.neighborSet = neighborSet;
        }
    }

    private static final class Graph {
        final int[] ids;
        final int[] initialLabels;
        final int[][] neighborIndexes;
        final String[] neighborText;
        final long edges;

        Graph(int[] ids, int[] labels, int[][] neighbors, String[] text, long edges) {
            this.ids = ids;
            this.initialLabels = labels;
            this.neighborIndexes = neighbors;
            this.neighborText = text;
            this.edges = edges;
        }

        static Graph read(Path input) throws IOException {
            TreeMap<Integer, RawNode> raw = new TreeMap<Integer, RawNode>();
            long endpoints = 0;
            try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = stripBom(line, lineNumber == 1);
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 4) {
                        throw new IOException("Line " + lineNumber
                                + ": expected id TAB label TAB status TAB neighbors");
                    }
                    int id = integer(fields[0], "node id at line " + lineNumber);
                    int label = integer(fields[1], "label at line " + lineNumber);
                    if (raw.containsKey(id)) {
                        throw new IOException("Duplicate node id " + id);
                    }
                    String[] parts = fields[3].isEmpty()
                            ? new String[0] : fields[3].split(",", -1);
                    int[] neighbors = new int[parts.length];
                    Set<Integer> seen = new HashSet<Integer>();
                    for (int j = 0; j < parts.length; j++) {
                        int neighbor = integer(parts[j], "neighbor at line " + lineNumber);
                        if (neighbor == id || !seen.add(neighbor)) {
                            throw new IOException("Self-loop or duplicate neighbor at node " + id);
                        }
                        neighbors[j] = neighbor;
                    }
                    raw.put(id, new RawNode(id, label, fields[3], neighbors, seen));
                    endpoints += neighbors.length;
                }
            }
            if (raw.isEmpty()) {
                throw new IOException("The graph is empty");
            }
            for (RawNode node : raw.values()) {
                for (int neighbor : node.neighbors) {
                    RawNode other = raw.get(neighbor);
                    if (other == null) {
                        throw new IOException("Node " + node.id + " refers to missing node " + neighbor);
                    }
                    if (!other.neighborSet.contains(node.id)) {
                        throw new IOException("Asymmetric edge " + node.id + "-" + neighbor);
                    }
                }
            }
            int size = raw.size();
            int[] ids = new int[size];
            int[] labels = new int[size];
            String[] text = new String[size];
            Map<Integer, Integer> positions = new HashMap<Integer, Integer>();
            int index = 0;
            for (RawNode node : raw.values()) {
                ids[index] = node.id;
                labels[index] = node.label;
                text[index] = node.neighborText;
                positions.put(node.id, index);
                index++;
            }
            int[][] neighbors = new int[size][];
            index = 0;
            for (RawNode node : raw.values()) {
                neighbors[index] = new int[node.neighbors.length];
                for (int j = 0; j < node.neighbors.length; j++) {
                    neighbors[index][j] = positions.get(node.neighbors[j]);
                }
                index++;
            }
            return new Graph(ids, labels, neighbors, text, endpoints / 2);
        }

        String canonicalHash() throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < ids.length; i++) {
                int[] neighborIds = new int[neighborIndexes[i].length];
                for (int j = 0; j < neighborIds.length; j++) {
                    neighborIds[j] = ids[neighborIndexes[i][j]];
                }
                Arrays.sort(neighborIds);
                StringBuilder line = new StringBuilder();
                line.append(ids[i]).append('\t').append(initialLabels[i]).append('\t');
                for (int j = 0; j < neighborIds.length; j++) {
                    if (j > 0) line.append(',');
                    line.append(neighborIds[j]);
                }
                line.append('\n');
                digest.update(line.toString().getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            return hex.toString();
        }
    }

    private static final class Observation {
        final int iteration;
        final int nodes;
        final long edges;
        final int changed;
        final String reason;

        Observation(int iteration, int nodes, long edges, int changed, String reason) {
            this.iteration = iteration;
            this.nodes = nodes;
            this.edges = edges;
            this.changed = changed;
            this.reason = reason;
        }

        boolean sameState(Observation other) {
            return iteration == other.iteration && nodes == other.nodes
                    && edges == other.edges && changed == other.changed
                    && reason.equals(other.reason);
        }
    }

    private static PrintWriter writer(Path file) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
    }

    private static String stripBom(String text, boolean firstLine) {
        return firstLine && text.startsWith("\ufeff") ? text.substring(1) : text;
    }

    private static int integer(String value, String description) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IOException("Invalid integer for " + description + ": " + value, error);
        }
    }

    private static String quoted(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') out.append('\\').append(c);
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    private static List<Observation> readReferenceMetrics(Path file) throws IOException {
        List<Observation> observations = new ArrayList<Observation>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) throw new IOException("Empty reference metrics");
            header = stripBom(header, true);
            if (!header.equals("iteration,nodes,edges,changed,job_seconds,total_seconds,reason")) {
                throw new IOException("Unexpected MapReduce metrics header");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length != 7) throw new IOException("Invalid reference metrics row");
                observations.add(new Observation(integer(fields[0], "iteration"),
                        integer(fields[1], "nodes"), Long.parseLong(fields[2]),
                        integer(fields[3], "changed"), fields[6]));
            }
        }
        return observations;
    }

    private static TreeMap<Integer, List<Integer>> readReferenceGroups(Path file) throws IOException {
        TreeMap<Integer, List<Integer>> groups = new TreeMap<Integer, List<Integer>>();
        Set<Integer> allNodes = new HashSet<Integer>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !stripBom(header, true).equals("label\tsize\tnodes")) {
                throw new IOException("Unexpected communities.tsv header");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 3) throw new IOException("Invalid community row");
                int label = integer(fields[0], "community label");
                int declaredSize = integer(fields[1], "community size");
                String members = fields[2].trim();
                if (!members.startsWith("[") || !members.endsWith("]")) {
                    throw new IOException("Expected bracketed community member list");
                }
                String inside = members.substring(1, members.length() - 1).trim();
                List<Integer> nodes = new ArrayList<Integer>();
                if (!inside.isEmpty()) {
                    for (String value : inside.split(",", -1)) {
                        int id = integer(value.trim(), "community member");
                        if (!allNodes.add(id)) throw new IOException("Duplicate reference member " + id);
                        nodes.add(id);
                    }
                }
                if (nodes.size() != declaredSize || nodes.isEmpty() || groups.containsKey(label)) {
                    throw new IOException("Invalid reference community size or label");
                }
                java.util.Collections.sort(nodes);
                groups.put(label, nodes);
            }
        }
        return groups;
    }

    private static int execute(String[] args) throws Exception {
        if (args.length != 3 && args.length != 6) {
            System.err.println("Usage: java -jar LPA_Sequential.jar input.tsv NEW_output_dir maxIterations"
                    + " [reference_metrics.csv reference_communities.tsv expected_graph_sha256]");
            return 2;
        }
        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        int limit = integer(args[2], "maxIterations");
        if (limit < 1) throw new IOException("maxIterations must be positive");
        if (!Files.isRegularFile(input)) throw new IOException("Input missing: " + input);
        if (Files.exists(output)) throw new IOException("Output already exists: " + output);
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Files.createDirectory(output);

        // Match the original Driver's practical scope: input reading and all
        // iterations are included, final partition export/validation are excluded.
        long start = System.nanoTime();
        Graph graph;
        int[] current;
        int[] lastChanges;
        List<Observation> observations = new ArrayList<Observation>();
        double inputSeconds;
        double computeSeconds = 0.0;
        double totalSeconds = 0.0;
        String reason = "MAX_ITERATIONS";
        try (PrintWriter metrics = writer(output.resolve("metrics.csv"))) {
            metrics.println("iteration,nodes,edges,changed,compute_seconds,total_seconds,reason");
            long readStart = System.nanoTime();
            graph = Graph.read(input);
            inputSeconds = (System.nanoTime() - readStart) / 1e9;
            current = graph.initialLabels.clone();
            lastChanges = new int[graph.ids.length];
            for (int iteration = 1; iteration <= limit; iteration++) {
                long iterationStart = System.nanoTime();
                int[] next = new int[current.length];
                int changed = 0;
                for (int vertex = 0; vertex < current.length; vertex++) {
                    TreeMap<Integer, Integer> votes = new TreeMap<Integer, Integer>();
                    for (int neighbor : graph.neighborIndexes[vertex]) {
                        int label = current[neighbor];
                        Integer count = votes.get(label);
                        votes.put(label, count == null ? 1 : count + 1);
                    }
                    int oldLabel = current[vertex];
                    int best = oldLabel;
                    int maximum = 0;
                    for (Map.Entry<Integer, Integer> vote : votes.entrySet()) {
                        if (vote.getValue() > maximum) {
                            maximum = vote.getValue();
                            best = vote.getKey();
                        }
                    }
                    Integer oldVotes = votes.get(oldLabel);
                    if (oldVotes != null && oldVotes == maximum) best = oldLabel;
                    next[vertex] = best;
                    lastChanges[vertex] = best == oldLabel ? 0 : 1;
                    changed += lastChanges[vertex];
                }
                current = next;
                long sampled = System.nanoTime();
                double iterationSeconds = (sampled - iterationStart) / 1e9;
                computeSeconds += iterationSeconds;
                totalSeconds = (sampled - start) / 1e9;
                if (changed == 0) reason = "CONVERGED";
                String state = changed == 0 || iteration == limit ? reason : "RUNNING";
                observations.add(new Observation(iteration, graph.ids.length, graph.edges, changed, state));
                metrics.printf(Locale.ROOT, "%d,%d,%d,%d,%.9f,%.9f,%s%n", iteration,
                        graph.ids.length, graph.edges, changed, iterationSeconds, totalSeconds, state);
                metrics.flush();
                if (metrics.checkError()) throw new IOException("Could not write metrics.csv");
                System.out.println("SEQUENTIAL iteration=" + iteration + " changed=" + changed + " state=" + state);
                if (changed == 0) break;
            }
        }

        TreeMap<Integer, List<Integer>> groups = new TreeMap<Integer, List<Integer>>();
        for (int i = 0; i < graph.ids.length; i++) {
            List<Integer> members = groups.get(current[i]);
            if (members == null) {
                members = new ArrayList<Integer>();
                groups.put(current[i], members);
            }
            members.add(graph.ids[i]);
        }
        try (PrintWriter out = writer(output.resolve("communities.tsv"))) {
            out.println("label\tsize\tnodes");
            for (Map.Entry<Integer, List<Integer>> group : groups.entrySet()) {
                out.println(group.getKey() + "\t" + group.getValue().size() + "\t" + group.getValue());
            }
            if (out.checkError()) throw new IOException("Could not write communities.tsv");
        }
        try (PrintWriter out = writer(output.resolve("final_state.tsv"))) {
            for (int i = 0; i < graph.ids.length; i++) {
                out.println(graph.ids[i] + "\t" + current[i] + "\t" + lastChanges[i] + "\t" + graph.neighborText[i]);
            }
            if (out.checkError()) throw new IOException("Could not write final_state.tsv");
        }

        String graphHash = graph.canonicalHash();
        boolean checked = args.length == 6;
        boolean sameIterations = true;
        boolean sameTrajectory = true;
        boolean sameGroups = true;
        boolean sameInput = true;
        if (checked) {
            List<Observation> reference = readReferenceMetrics(Paths.get(args[3]));
            sameIterations = observations.size() == reference.size();
            sameTrajectory = sameIterations;
            for (int i = 0; i < Math.min(observations.size(), reference.size()); i++) {
                if (!observations.get(i).sameState(reference.get(i))) sameTrajectory = false;
            }
            sameGroups = groups.equals(readReferenceGroups(Paths.get(args[4])));
            sameInput = graphHash.equalsIgnoreCase(args[5]);
        }
        boolean matches = checked && sameIterations && sameTrajectory && sameGroups && sameInput;
        try (PrintWriter out = writer(output.resolve("validation.json"))) {
            out.println("{");
            out.println("  \"reference_checked\": " + checked + ",");
            out.println("  \"same_iteration_count\": " + (checked ? Boolean.toString(sameIterations) : "null") + ",");
            out.println("  \"same_changed_trajectory\": " + (checked ? Boolean.toString(sameTrajectory) : "null") + ",");
            out.println("  \"same_labels_and_members\": " + (checked ? Boolean.toString(sameGroups) : "null") + ",");
            out.println("  \"same_input_graph\": " + (checked ? Boolean.toString(sameInput) : "null") + ",");
            out.println("  \"all_match\": " + (checked ? Boolean.toString(matches) : "null"));
            out.println("}");
        }
        Observation last = observations.get(observations.size() - 1);
        try (PrintWriter out = writer(output.resolve("summary.json"))) {
            out.println("{");
            out.println("  \"implementation\": \"sequential_in_memory\",");
            out.println("  \"java_version\": " + quoted(System.getProperty("java.version")) + ",");
            out.println("  \"input\": " + quoted(input.toString()) + ",");
            out.println("  \"nodes\": " + graph.ids.length + ",");
            out.println("  \"edges\": " + graph.edges + ",");
            out.println("  \"max_iterations\": " + limit + ",");
            out.println("  \"iterations\": " + last.iteration + ",");
            out.println("  \"changed\": " + last.changed + ",");
            out.println("  \"reason\": " + quoted(reason) + ",");
            out.println("  \"communities\": " + groups.size() + ",");
            out.printf(Locale.ROOT, "  \"total_seconds\": %.9f,%n", totalSeconds);
            out.printf(Locale.ROOT, "  \"input_load_validate_seconds\": %.9f,%n", inputSeconds);
            out.printf(Locale.ROOT, "  \"sum_compute_seconds\": %.9f,%n", computeSeconds);
            out.println("  \"timing_scope\": \"local_input_read_validate_and_iterations_excluding_final_export\",");
            out.println("  \"input_sha256_canonical\": " + quoted(graphHash) + ",");
            out.println("  \"reference_checked\": " + checked + ",");
            out.println("  \"reference_matches\": " + (checked ? Boolean.toString(matches) : "null"));
            out.println("}");
        }
        if (checked && !matches) {
            System.err.println("VALIDATION FAILED. See validation.json; do not use this run for the comparison.");
            return 3;
        }
        System.out.printf(Locale.ROOT, "FINAL reason=%s iterations=%d communities=%d total_seconds=%.9f reference=%s%n",
                reason, last.iteration, groups.size(), totalSeconds, checked ? "MATCH" : "NOT_CHECKED");
        return 0;
    }

    public static void main(String[] args) {
        try {
            System.exit(execute(args));
        } catch (Exception error) {
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
