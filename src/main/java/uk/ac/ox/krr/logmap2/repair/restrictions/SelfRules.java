package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;

import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * The conditional memberships a self-edge gives, design spec §7.8: HS1, `hasSelf(R) ∧
 * ∀R′.Z → Z` when R ⊑p R′ (an individual R-related to itself is R′-related to itself, so
 * it is its own R′-successor); HS2, `hasSelf(R) ∧ ≥n P.Z → Z` (n ≥ 1) when R and P lie
 * below one functional property (the self-edge and the Z-successor are one individual).
 * Neither is a clash: the head is a class, and whatever excludes it closes the conflict.
* Each membership is emitted once for every minimal support ({@link AlternativeSupports}).
 * A TOP head says nothing and is skipped. A self-edge holds in both orientations, so the
 * self-edge's property is tried both ways (§7.3a). Irreflexivity needs no rule:
 * `Irreflexive(R)` is the clause `hasSelf(R) → FALSE`, and self-edges link along the
 * property hierarchy like any restriction (HS3). Object properties only.
 */
final class SelfRules {

    private final RestrictionStore store;
    private final SupportedClosure properties;
    private final Functionality functionality;

    SelfRules(RestrictionStore store, SupportedClosure properties, Functionality functionality) {
        this.store = store;
        this.properties = properties;
        this.functionality = functionality;
    }

    /** Every membership `self ∧ other → filler`, in identifier order. */
    List<HornInclusion> memberships() {
        List<HornInclusion> memberships = new ArrayList<>();

        for (Restriction self : store.restrictions(PropertyKind.OBJECT)) {
            if (self.kind() != RestrictionKind.SELF) {
                continue;
            }
            for (Restriction other : store.restrictions(PropertyKind.OBJECT)) {
                if (store.isTop(other.filler())) {
                    continue;
                }
                List<Integer> body = List.of(store.identifierOf(self), store.identifierOf(other));
                for (Support support : membershipSupports(self.propertyToken(), other).supports()) {
                    memberships.add(HornInclusion.of(body, other.filler(), support));
                }
            }
        }

        return memberships;
    }


    /**
     * HS1 against a universal, HS2 against an existential, each with the self-edge in both
     * orientations.
     */
    private AlternativeSupports membershipSupports(int selfEdge, Restriction other) {
        int flippedSelfEdge = SignedProperties.flip(selfEdge);
        if (other.isUniversal()) {
            return properties.supportsOf(selfEdge, other.propertyToken())
                    .or(properties.supportsOf(flippedSelfEdge, other.propertyToken()));
        }
        if (other.isExistential()) {
            return functionality.mergeSupportsOf(selfEdge, other.propertyToken())
                    .or(functionality.mergeSupportsOf(flippedSelfEdge, other.propertyToken()));
        }
        return AlternativeSupports.NONE;
    }

}