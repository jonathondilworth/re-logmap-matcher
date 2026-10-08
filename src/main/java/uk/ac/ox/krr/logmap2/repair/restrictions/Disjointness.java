package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import uk.ac.ox.krr.logmap2.indexing.IndexManager;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;


/**
 * Answers whether two class propositions are disjoint, and under which correspondence
 * directions: they are when an ancestor of the one and an ancestor of the other are
 * explicitly disjoint, in LogMap's index or as a two-atom clash of the store (two
 * different individuals, a named-only clash the index has not read), ancestors and their
 * supports coming from the class closure of the current build; or when an ancestor of the one is a union every member of which is disjoint from
 * the other (never when only some member is: a successor may be in any member). Each
 * side expands a union at most once, so a union against a union is answered but a member
 * that is itself below a further union is not followed: a sound omission that keeps the
 * search bounded on corpora with many overlapping unions. The answer is every minimal
 * union of supports over all such routes. TOP is disjoint from nothing. Design spec
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
            if (inclusion.isClash() && inclusion.body().size() == 2 && isClassProposition(inclusion.body().get(0)) && isClassProposition(inclusion.body().get(1))) {
                addBothWays(inclusion.body().get(0), inclusion.body().get(1));
            }
        }
    }

    
    /** A named class, a fresh class, a nominal or a union: `X ∧ Y → FALSE` makes two of them disjoint. */
    private boolean isClassProposition(int proposition) {
        return !store.isRestriction(proposition) && !store.isTop(proposition)
                && !store.isDataRange(proposition) && !store.isDataTop(proposition);
    }



    /**
     * The minimal supports under which `first` and `second` are disjoint; none when they are not.
     */
    public AlternativeSupports supportsOf(int first, int second) {
        if (first == top || second == top) {
            return AlternativeSupports.NONE;
        }
        return supportsThroughAncestors(first, second)
                .or(supportsThroughUnionsAbove(first, member -> supportsOfMember(member, second)))
                .or(supportsThroughUnionsAbove(second, member -> supportsOfMember(member, first)));
    }


    /**
     * A member of a union that was expanded on its own side, against `other`, whose side may
     * still expand one union. A member of that second union is compared through ancestors
     * alone: each side expands a union at most once, which bounds the search and still
     * answers a union against a union.
     */
    private AlternativeSupports supportsOfMember(int member, int other) {
        if (member == top || other == top) {
            return AlternativeSupports.NONE;
        }
        return supportsThroughAncestors(member, other)
                .or(supportsThroughUnionsAbove(other,
                otherMember -> supportsThroughAncestors(otherMember, member)));
    }


    /** An ancestor of the one and an ancestor of the other are explicitly disjoint. */
    private AlternativeSupports supportsThroughAncestors(int first, int second) {
        if (first == top || second == top) {
            return AlternativeSupports.NONE;
        }
        Map<Integer, AlternativeSupports> ancestorsOfSecond = classes.ancestorsOf(second);
        AlternativeSupports disjoint = AlternativeSupports.NONE;

        for (Map.Entry<Integer, AlternativeSupports> ancestorOfFirst : classes.ancestorsOf(first).entrySet()) {
            Set<Integer> disjointFromAncestor = explicitlyDisjoint.get(ancestorOfFirst.getKey());
            if (disjointFromAncestor == null) {
                continue;
            }
            for (Map.Entry<Integer, AlternativeSupports> ancestorOfSecond : ancestorsOfSecond.entrySet()) {
                if (disjointFromAncestor.contains(ancestorOfSecond.getKey())) {
                    AlternativeSupports supportsOfBoth = ancestorOfFirst.getValue().and(ancestorOfSecond.getValue());
                    disjoint = disjoint.or(supportsOfBoth);
                }
            }
        }

        return disjoint;
    }
 

    /**
     * The all-members rule: an ancestor of `proposition` is a union every member of which is
     * disjoint from the other side.
     */
    private AlternativeSupports supportsThroughUnionsAbove(int proposition, IntFunction<AlternativeSupports> supportsOfMember) {
        AlternativeSupports disjoint = AlternativeSupports.NONE;

        for (Map.Entry<Integer, AlternativeSupports> ancestor : classes.ancestorsOf(proposition).entrySet()) {
            if (store.isUnion(ancestor.getKey())) {
                AlternativeSupports everyMember = supportsOfEveryMember(ancestor.getKey(), supportsOfMember);
                disjoint = disjoint.or(ancestor.getValue().and(everyMember));
            }
        }
        return disjoint;
    }


    private AlternativeSupports supportsOfEveryMember(int union, IntFunction<AlternativeSupports> supportsOfMember) {
        AlternativeSupports everyMember = AlternativeSupports.FACT;

        for (int member : store.membersOf(union)) {
            everyMember = everyMember.and(supportsOfMember.apply(member));
            if (everyMember.isNone()) {
                return AlternativeSupports.NONE;
            }
        }
        return everyMember;
    }


    private void addBothWays(int first, int second) {
        explicitlyDisjoint.computeIfAbsent(first, owlClass -> new HashSet<>()).add(second);
        explicitlyDisjoint.computeIfAbsent(second, owlClass -> new HashSet<>()).add(first);
    }
}