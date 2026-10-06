package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;

import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * The clash rules between two restrictions: each conclusion is the
 * clause `r1 ∧ r2 → FALSE`, supported by the union of the supports of its conditions.
 * D1 at this step; the other D-rules join as methods.
 */
final class ClashRules {

    private final RestrictionStore store;
    private final SupportedClosure properties;
    private final Disjointness disjointness;

    ClashRules(RestrictionStore store, SupportedClosure properties, Disjointness disjointness) {
        this.store = store;
        this.properties = properties;
        this.disjointness = disjointness;
    }

    /** Every clash between two restrictions of the store, in identifier order. */
    List<HornInclusion> clashes() {
        List<HornInclusion> clashes = new ArrayList<>();

        for (Restriction existential : store.restrictions()) {
            for (Restriction universal : store.restrictions()) {
                Support support = d1(existential, universal);
                if (support != null) {
                    clashes.add(HornInclusion.of(
                            List.of(store.identifierOf(existential), store.identifierOf(universal)),
                            HornInclusion.FALSE, support));
                }
            }
        }

        return clashes;
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
}