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
 * Reachability over a graph whose edges are either facts of the ontologies or
 * correspondence directions, answering "is `from` below `to`, and through which
 * correspondences?" with a support of minimal size: facts cost nothing, correspondences
 * cost one, and a 0-1 breadth-first search from each source settles every node with the
 * cheapest path. A fact-only path therefore always wins over a shorter path through a
 * correspondence, so an empty support means the fact holds from the ontologies alone.
 * An edge that holds under several directions at once (an S-link's) is a chain of
 * correspondence edges through hidden nodes, one direction per edge, so it costs one per
 * direction and the search stays the same; hidden nodes never appear in an answer.
 */
public final class SupportedClosure {

    private record Edge(int target, CorrespondenceDirection correspondence) {

        boolean isFact() {
            return correspondence == null;
        }
    }

    /** Propositions are never negative and FALSE is -2, so hidden nodes count down from -3. */
    private static final int FIRST_HIDDEN_NODE = -3;

    private final Map<Integer, List<Edge>> edgesFrom = new HashMap<>();
    private final Map<Integer, Map<Integer, Support>> settledFrom = new HashMap<>();
    private int nextHiddenNode = FIRST_HIDDEN_NODE;

    public void addFact(int from, int to) {
        addEdge(from, new Edge(to, null));
    }

    public void addCorrespondence(int from, int to, CorrespondenceDirection direction) {
        addEdge(from, new Edge(to, direction));
    }

    /** `from ⊑ to` under every direction of the support: a fact when it is empty. */
    public void addSupported(int from, int to, Support support) {
        List<CorrespondenceDirection> directions = new ArrayList<>(support.directions());
        if (directions.isEmpty()) {
            addFact(from, to);
            return;
        }
        int node = from;
        for (CorrespondenceDirection direction : directions.subList(0, directions.size() - 1)) {
            int hidden = nextHiddenNode--;
            addCorrespondence(node, hidden, direction);
            node = hidden;
        }
        addCorrespondence(node, to, directions.get(directions.size() - 1));
    }


    /** The minimal support under which `from ⊑ to` holds, or null when it does not. */
    public Support supportOf(int from, int to) {
        if (from == to) {
            return Support.EMPTY;
        }
        return settle(from).get(to);
    }

    /** Everything `from` is below, itself included, each with its minimal support. */
    public Map<Integer, Support> ancestorsOf(int from) {
        Map<Integer, Support> ancestors = new HashMap<>(settle(from));
        ancestors.put(from, Support.EMPTY);
        return Collections.unmodifiableMap(ancestors);
    }

    private void addEdge(int from, Edge edge) {
        edgesFrom.computeIfAbsent(from, node -> new ArrayList<>()).add(edge);
        settledFrom.clear();
    }

    private Map<Integer, Support> settle(int source) {
        Map<Integer, Support> known = settledFrom.get(source);
        if (known != null) {
            return known;
        }

        Map<Integer, Integer> costOf = new HashMap<>();
        Map<Integer, Support> supportOf = new HashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        costOf.put(source, 0);
        supportOf.put(source, Support.EMPTY);
        queue.addFirst(source);

        while (!queue.isEmpty()) {
            int node = queue.pollFirst();
            int cost = costOf.get(node);
            Support support = supportOf.get(node);

            for (Edge edge : edgesFrom.getOrDefault(node, List.of())) {
                int newCost = cost + (edge.isFact() ? 0 : 1);
                Integer knownCost = costOf.get(edge.target());
                if (knownCost != null && knownCost <= newCost) {
                    continue;
                }
                costOf.put(edge.target(), newCost);
                supportOf.put(edge.target(), edge.isFact() ? support : support.with(Support.of(edge.correspondence())));
                if (edge.isFact()) {
                    queue.addFirst(edge.target());
                } else {
                    queue.addLast(edge.target());
                }
            }
        }

        supportOf.remove(source);
        supportOf.keySet().removeIf(node -> node <= FIRST_HIDDEN_NODE);
        settledFrom.put(source, supportOf);
        return supportOf;
    }
}