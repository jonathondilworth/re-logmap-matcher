package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.ox.krr.logmap2.repair.hornSAT.CorrespondenceDirection;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * Reachability over a graph whose edges are either facts of the ontologies or hold under
 * correspondence directions, answering "is `from` below `to`, and through which
 * correspondences?" with every minimal support: the directions of a path, unless another
 * path needs only some of them. A fact-only path has the empty support, which is then the
 * only answer, so an empty support means the inclusion holds from the ontologies alone.
 * From each source the alternatives are carried along the edges until no node's answer
 * improves.
 */
public final class SupportedClosure {

    private record Edge(int target, AlternativeSupports support) {
    }

    private final Map<Integer, List<Edge>> edgesFrom = new HashMap<>();
    private final Map<Integer, Map<Integer, AlternativeSupports>> settledFrom = new HashMap<>();

    public void addFact(int from, int to) {
        addSupported(from, to, Support.EMPTY);
    }

    public void addCorrespondence(int from, int to, CorrespondenceDirection direction) {
        addSupported(from, to, Support.of(direction));
    }

    /** `from ⊑ to` under every direction of the support: a fact when it is empty. */
    public void addSupported(int from, int to, Support support) {
        edgesFrom.computeIfAbsent(from, node -> new ArrayList<>()).add(new Edge(to, AlternativeSupports.of(support)));
        settledFrom.clear();
    }

    /** The minimal supports under which `from ⊑ to` holds; none when it does not. */
    public AlternativeSupports supportsOf(int from, int to) {
        return settle(from).getOrDefault(to, AlternativeSupports.NONE);
    }

    /** Everything `from` is below, itself included, each with its minimal supports. */
    public Map<Integer, AlternativeSupports> ancestorsOf(int from) {
        return settle(from);
    }

    private Map<Integer, AlternativeSupports> settle(int source) {
        Map<Integer, AlternativeSupports> known = settledFrom.get(source);
        if (known != null) {
            return known;
        }

        Map<Integer, AlternativeSupports> supportsOf = new HashMap<>();
        Deque<Integer> improved = new ArrayDeque<>();
        supportsOf.put(source, AlternativeSupports.FACT);
        improved.add(source);

        while (!improved.isEmpty()) {
            int node = improved.poll();
            AlternativeSupports supportsOfNode = supportsOf.get(node);

            for (Edge edge : edgesFrom.getOrDefault(node, List.of())) {
                AlternativeSupports before =
                        supportsOf.getOrDefault(edge.target(), AlternativeSupports.NONE);
                AlternativeSupports after = before.or(supportsOfNode.and(edge.support()));
                if (!after.equals(before)) {
                    supportsOf.put(edge.target(), after);
                    improved.add(edge.target());
                }
            }
        }

        Map<Integer, AlternativeSupports> settled = Collections.unmodifiableMap(supportsOf);
        settledFrom.put(source, settled);
        return settled;
    }
}