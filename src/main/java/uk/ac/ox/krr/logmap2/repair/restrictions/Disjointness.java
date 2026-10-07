package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import uk.ac.ox.krr.logmap2.indexing.IndexManager;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * Answers whether two class propositions are disjoint, and under which correspondence
 * directions: they are when an ancestor of the one and an ancestor of the other are
 * explicitly disjoint, in LogMap's index or, for two nominals, as different individuals
 * in the store, ancestors and their supports coming from the class closure of the current
 * build. The answer is the smallest union of the two supports over all such pairs. TOP is
 * disjoint from nothing.
 */
public final class Disjointness {

    private final Map<Integer, Set<Integer>> explicitlyDisjoint = new HashMap<>();
    private final SupportedClosure classes;
    private final int top;

    public Disjointness(IndexManager index, RestrictionStore store, SupportedClosure classes) {
        this.classes = classes;
        this.top = store.top();
        for (Map.Entry<Integer, Set<Integer>> classAndDisjoint : index.getDirectDisjointClasses().entrySet()) {
            for (int disjoint : classAndDisjoint.getValue()) {
                addBothWays(classAndDisjoint.getKey(), disjoint);
            }
        }
        for (HornInclusion inclusion : store.inclusions()) {
            if (inclusion.isClash() && inclusion.body().size() == 2 && store.isNominal(inclusion.body().get(0)) && store.isNominal(inclusion.body().get(1))) {
                addBothWays(inclusion.body().get(0), inclusion.body().get(1));
            }
        }
    }

    /** The minimal support under which `first` and `second` are disjoint, or null. */
    public Support supportOf(int first, int second) {
        if (first == top || second == top) {
            return null;
        }
        Map<Integer, Support> ancestorsOfSecond = classes.ancestorsOf(second);
        Support best = null;

        for (Map.Entry<Integer, Support> ancestorOfFirst : classes.ancestorsOf(first).entrySet()) {
            Set<Integer> disjointFromAncestor = explicitlyDisjoint.get(ancestorOfFirst.getKey());
            if (disjointFromAncestor == null) {
                continue;
            }
            for (Map.Entry<Integer, Support> ancestorOfSecond : ancestorsOfSecond.entrySet()) {
                if (!disjointFromAncestor.contains(ancestorOfSecond.getKey())) {
                    continue;
                }
                Support candidate = ancestorOfFirst.getValue().with(ancestorOfSecond.getValue());
                if (best == null || candidate.size() < best.size()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    private void addBothWays(int first, int second) {
        explicitlyDisjoint.computeIfAbsent(first, owlClass -> new HashSet<>()).add(second);
        explicitlyDisjoint.computeIfAbsent(second, owlClass -> new HashSet<>()).add(first);
    }
}