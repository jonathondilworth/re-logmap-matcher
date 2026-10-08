package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * Rule U1, case analysis over a union: a union lies below everything all of its members lie
 * below. If x is in `M1 ⊔ … ⊔ Mk` and every `Mi ⊑ C`, then x is in some Mi and so in C.
 * Each conclusion is the clause `union → C`. It is supported by one support of each
 * member's inclusion, joined, since it needs every member below C; and it is emitted once
 * for each such support that {@link AlternativeSupports} keeps (the smallest, up to the
 * setting). A member's inclusion is read from the bridged class closure, so it may run
 * through restrictions and through correspondences. The conclusions are not fed back into
 * that closure.
 *
 * <p>This is what lets propagation continue from a domain that is a union
 * (`∃R.⊤ → union`): the subject of the property is in the union, and nothing else says
 * what follows from that. The all-members rule of {@link Disjointness} does the
 * corresponding work for fillers. Not in design_spec.md, which has no rule for this case;
 * the derivation is in docs/steps/21.md.
 */
final class UnionRules {

    private final RestrictionStore store;
    private final SupportedClosure classes;

    UnionRules(RestrictionStore store, SupportedClosure classes) {
        this.store = store;
        this.classes = classes;
    }

    /** Every inclusion of a union in a common ancestor of its members, in identifier order. */
    List<HornInclusion> inclusions() {
        List<HornInclusion> inclusions = new ArrayList<>();

        for (int union : store.unions()) {
            for (Map.Entry<Integer, AlternativeSupports> ancestor
                    : commonAncestorsOfTheMembersOf(union).entrySet()) {
                if (saysNothing(union, ancestor.getKey())) {
                    continue;
                }
                for (Support support : ancestor.getValue().supports()) {
                    inclusions.add(HornInclusion.of(List.of(union), ancestor.getKey(), support));
                }
            }
        }

        return inclusions;
    }

    /**
     * The union itself and TOP are above every member and say nothing. Another union above
     * every member is left out as well: that inclusion would be true, and this rule may
     * conclude things from the other union, so leaving it out is an omission, and a sound
     * one.
     */
    private boolean saysNothing(int union, int ancestor) {
        return ancestor == union || store.isUnion(ancestor) || store.isTop(ancestor);
    }

    /** What every member is below, each with the supports under which all members are. */
    private Map<Integer, AlternativeSupports> commonAncestorsOfTheMembersOf(int union) {
        List<Integer> members = store.membersOf(union);
        Map<Integer, AlternativeSupports> common =
                new TreeMap<>(classes.ancestorsOf(members.get(0)));

        for (int member : members.subList(1, members.size())) {
            Map<Integer, AlternativeSupports> ancestorsOfMember = classes.ancestorsOf(member);
            Map<Integer, AlternativeSupports> stillCommon = new TreeMap<>();
            for (Map.Entry<Integer, AlternativeSupports> ancestor : common.entrySet()) {
                AlternativeSupports ofThisMember = ancestorsOfMember.get(ancestor.getKey());
                if (ofThisMember != null) {
                    stillCommon.put(ancestor.getKey(), ancestor.getValue().and(ofThisMember));
                }
            }
            common = stillCommon;
        }

        return common;
    }
}