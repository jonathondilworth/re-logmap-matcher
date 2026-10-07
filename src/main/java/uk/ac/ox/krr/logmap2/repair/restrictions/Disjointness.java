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
 * build; or when an ancestor of the one is a union every member of which is disjoint from
 * the other (never when only some member is: a successor may be in any member). The
 * answer is the smallest union of the supports over all such routes. TOP is disjoint from
 * nothing.
 */
public final class Disjointness {

    private final Map<Integer, Set<Integer>> explicitlyDisjoint = new HashMap<>();
    private final RestrictionStore store;
    private final SupportedClosure classes;
    private final int top;

    public Disjointness(IndexManager index, RestrictionStore store, SupportedClosure classes) {
        this.store = store;
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
        return supportOf(first, second, new HashSet<>());
    }

    private Support supportOf(int first, int second, Set<Integer> unionsBeingExpanded) {
        if (first == top || second == top) {
            return null;
        }
        Map<Integer, Support> ancestorsOfFirst = classes.ancestorsOf(first);
        Map<Integer, Support> ancestorsOfSecond = classes.ancestorsOf(second);
        Support best = null;

        for (Map.Entry<Integer, Support> ancestorOfFirst : ancestorsOfFirst.entrySet()) {
            Set<Integer> disjointFromAncestor = explicitlyDisjoint.get(ancestorOfFirst.getKey());
            if (disjointFromAncestor == null) {
                continue;
            }
            for (Map.Entry<Integer, Support> ancestorOfSecond : ancestorsOfSecond.entrySet()) {
                if (disjointFromAncestor.contains(ancestorOfSecond.getKey())) {
                    best = smaller(best, ancestorOfFirst.getValue().with(ancestorOfSecond.getValue()));
                }
            }
        }
        best = smaller(best, throughUnions(ancestorsOfFirst, second, unionsBeingExpanded));
        best = smaller(best, throughUnions(ancestorsOfSecond, first, unionsBeingExpanded));

        return best;
    }

    /**
     * The all-members rule over each union among the ancestors: the union is disjoint
     * from `other` when every member is. A union being expanded is passed over while its
     * members are examined, since every member has it as an ancestor.
     */
    private Support throughUnions(Map<Integer, Support> ancestors, int other, Set<Integer> unionsBeingExpanded) {
        Support best = null;

        for (Map.Entry<Integer, Support> ancestor : ancestors.entrySet()) {
            int union = ancestor.getKey();
            if (!store.isUnion(union) || unionsBeingExpanded.contains(union)) {
                continue;
            }
            unionsBeingExpanded.add(union);
            Support members = allMembersSupport(union, other, unionsBeingExpanded);
            unionsBeingExpanded.remove(union);
            if (members != null) {
                best = smaller(best, ancestor.getValue().with(members));
            }
        }

        return best;
    }
 
    private Support allMembersSupport(int union, int other, Set<Integer> unionsBeingExpanded) {
        Support total = Support.EMPTY;
        for (int member : store.membersOf(union)) {
            Support memberSupport = supportOf(member, other, unionsBeingExpanded);
            if (memberSupport == null) {
                return null;
            }
            total = total.with(memberSupport);
        }
        return total;
    }

    private static Support smaller(Support best, Support candidate) {
        if (candidate == null) {
            return best;
        }
        if (best == null || candidate.size() < best.size()) {
            return candidate;
        }
        return best;
    }

    private void addBothWays(int first, int second) {
        explicitlyDisjoint.computeIfAbsent(first, owlClass -> new HashSet<>()).add(second);
        explicitlyDisjoint.computeIfAbsent(second, owlClass -> new HashSet<>()).add(first);
    }
}