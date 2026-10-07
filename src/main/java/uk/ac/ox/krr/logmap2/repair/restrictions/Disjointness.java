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
 * the other (never when only some member is: a successor may be in any member). Each
 * side expands a union at most once, so a union against a union is answered but a member
 * that is itself below a further union is not followed: a sound omission that keeps the
 * search bounded on corpora with many overlapping unions. The answer is the smallest
 * union of the supports over all such routes. TOP is disjoint from nothing.
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
        return supportOf(first, second, true, true);
    }

    /**
     * Each side may expand a union among its ancestors once: a member is then compared with
     * the other side without expanding a further union on its own side, so the search is
     * bounded (two expansions at most) and a union against a union is still answered.
     */
    private Support supportOf(int first, int second, boolean expandFirst, boolean expandSecond) {
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
                    best = Support.least(best, ancestorOfFirst.getValue().with(ancestorOfSecond.getValue()));
                }
            }
        }
        if (expandFirst) {
            for (Map.Entry<Integer, Support> ancestor : ancestorsOfFirst.entrySet()) {
                if (store.isUnion(ancestor.getKey())) {
                    Support members = allMembersSupport(ancestor.getKey(), second, expandSecond, true);
                    best = Support.least(best, members == null ? null : ancestor.getValue().with(members));
                }
            }
        }
        if (expandSecond) {
            for (Map.Entry<Integer, Support> ancestor : ancestorsOfSecond.entrySet()) {
                if (store.isUnion(ancestor.getKey())) {
                    Support members = allMembersSupport(ancestor.getKey(), first, expandFirst, false);
                    best = Support.least(best, members == null ? null : ancestor.getValue().with(members));
                }
            }
        }

        return best;
    }
 
    /** The all-members rule: the union is disjoint from `other` when every member is. */
    private Support allMembersSupport(int union, int other, boolean expandOther, boolean unionIsFirst) {
        Support total = Support.EMPTY;
        for (int member : store.membersOf(union)) {
            Support memberSupport = unionIsFirst
                    ? supportOf(member, other, false, expandOther)
                    : supportOf(other, member, expandOther, false);
            if (memberSupport == null) {
                return null;
            }
            total = total.with(memberSupport);
        }
        return total;
    }

    private void addBothWays(int first, int second) {
        explicitlyDisjoint.computeIfAbsent(first, owlClass -> new HashSet<>()).add(second);
        explicitlyDisjoint.computeIfAbsent(second, owlClass -> new HashSet<>()).add(first);
    }
}