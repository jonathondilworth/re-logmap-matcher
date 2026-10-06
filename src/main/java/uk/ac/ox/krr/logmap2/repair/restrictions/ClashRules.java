package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;

import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * The clash rules between two restrictions: each conclusion is the
 * clause `r1 ∧ r2 → FALSE`, supported by the union of the supports of its conditions.
 * D1 and D2 so far; the other D-rules join as methods.
 */
final class ClashRules {

    private final RestrictionStore store;
    private final SupportedClosure classes;
    private final SupportedClosure properties;
    private final Disjointness disjointness;

    ClashRules(RestrictionStore store, SupportedClosure classes, SupportedClosure properties, Disjointness disjointness) {
        this.store = store;
        this.classes = classes;
        this.properties = properties;
        this.disjointness = disjointness;
    }

    /** Every clash between two restrictions of the store, in identifier order. */
    List<HornInclusion> clashes() {
        List<HornInclusion> clashes = new ArrayList<>();

        for (Restriction first : store.restrictions()) {
            for (Restriction second : store.restrictions()) {
                addClash(clashes, first, second, d1(first, second));
                addClash(clashes, first, second, d2(first, second));
            }
        }

        return clashes;
    }

    private void addClash(List<HornInclusion> clashes, Restriction first, Restriction second, Support support) {
        if (support != null) {
            clashes.add(
                HornInclusion.of(
                    List.of(store.identifierOf(first), store.identifierOf(second)
                ), HornInclusion.FALSE, support)
            );
        }
    }

    /**
     * D1, some versus only: `≥n P1.F1` (n ≥ 1) and `∀P2.F2` clash when P1 ⊑p P2 and F1, F2
     * are disjoint. An individual in both has a P1-successor in F1, which is a
     * P2-successor and so in F2, and F1 ⊓ F2 is empty.
     */
    private Support d1(Restriction existential, Restriction universal) {
        if (!existential.isExistential() || !universal.isUniversal()) {
            return null;
        }
        Support propertySupport = properties.supportOf(existential.property(), universal.property());
        if (propertySupport == null) {
            return null;
        }
        Support fillerSupport = disjointness.supportOf(existential.filler(), universal.filler());
        if (fillerSupport == null) {
            return null;
        }
        return propertySupport.with(fillerSupport);
    }


    /**
     * D2, min versus max: `≥n1 P1.F1` and `≤n2 P2.F2` clash when n1 > n2, P1 ⊑p P2 and
     * F1 ⊑c F2. The n1 successors in F1 are n1 P2-successors in F2, more than the n2
     * allowed. An unqualified max has filler TOP, so its filler condition holds trivially;
     * an unqualified min against a qualified max fails it, which is right: the successors
     * need not be in F2.
     */
    private Support d2(Restriction lowerBound, Restriction upperBound) {
        if (!lowerBound.isExistential() || upperBound.kind() != RestrictionKind.AT_MOST) {
            return null;
        }
        if (lowerBound.cardinality() <= upperBound.cardinality()) {
            return null;
        }
        Support propertySupport = properties.supportOf(lowerBound.property(), upperBound.property());
        if (propertySupport == null) {
            return null;
        }
        Support fillerSupport = subClass(lowerBound.filler(), upperBound.filler());
        if (fillerSupport == null) {
            return null;
        }
        return propertySupport.with(fillerSupport);
    }


    private Support subClass(int subClass, int superClass) {
        if (store.isTop(superClass)) {
            return Support.EMPTY;
        }
        return classes.supportOf(subClass, superClass);
    }

}