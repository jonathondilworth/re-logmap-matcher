package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * The clash rules between two restrictions: each conclusion is the
 * clause `r1 ∧ r2 → FALSE`, supported by the union of the supports of its conditions.
 * D1, D2, D3, and D6, whose clause is `r1 ∧ A → FALSE` with a class A; D4 and D5
 * are D2 and D1 on TOP. Property conditions are over signed properties. One instance per
 * property kind: the rules are the same over data properties, with data ranges as
 * fillers, and D6 is for object properties alone, which have inverses.
 */
final class ClashRules {

    private final RestrictionStore store;
    private final PropertyKind kind;
    private final SupportedClosure properties;
    private final FillerRelations fillers;
    private final Functionality functionality;
    private final Map<Integer, List<Integer>> attachedClasses = new HashMap<>();

    ClashRules(RestrictionStore store, PropertyKind kind, SupportedClosure properties, FillerRelations fillers, Functionality functionality) {
        this.store = store;
        this.kind = kind;
        this.properties = properties;
        this.fillers = fillers;
        this.functionality = functionality;
        for (HornInclusion inclusion : store.inclusions()) {
            if (inclusion.body().size() == 1 && store.isRestriction(inclusion.body().get(0))
                    && inclusion.head() != HornInclusion.FALSE && !store.isRestriction(inclusion.head())) {
                attachedClasses.computeIfAbsent(inclusion.body().get(0), restriction -> new ArrayList<>()).add(inclusion.head());
            }
        }
    }

    /** Every clash between two restrictions of this kind, in identifier order. */
    List<HornInclusion> clashes() {
        List<HornInclusion> clashes = new ArrayList<>();

        for (Restriction first : store.restrictions(kind)) {
            for (Restriction second : store.restrictions(kind)) {
                addClash(clashes, first, second, d1(first, second));
                addClash(clashes, first, second, d2(first, second));
                if (store.identifierOf(first) < store.identifierOf(second)) {
                    addClash(clashes, first, second, d3(first, second));
                }
                if (kind == PropertyKind.OBJECT) {
                    d6(clashes, first, second);
                }
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
        Support propertySupport = properties.supportOf(existential.propertyToken(), universal.propertyToken());
        if (propertySupport == null) {
            return null;
        }
        Support fillerSupport = fillers.disjointness(existential.filler(), universal.filler());
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
        Support propertySupport = properties.supportOf(lowerBound.propertyToken(), upperBound.propertyToken());
        if (propertySupport == null) {
            return null;
        }
        Support fillerSupport = subClass(lowerBound.filler(), upperBound.filler());
        if (fillerSupport == null) {
            return null;
        }
        return propertySupport.with(fillerSupport);
    }


    /**
     * D3, functionality fan-in: `≥n1 P1.F1` and `≥n2 P2.F2` clash when both properties lie
     * below one functional property and F1, F2 are disjoint. The two successors are
     * successors through the functional property, hence one individual, which cannot be
     * in both fillers. Evaluated once per unordered pair.
     */
    private Support d3(Restriction first, Restriction second) {
        if (!first.isExistential() || !second.isExistential()) {
            return null;
        }
        Support mergeSupport = functionality.mergeSupportOf(first.propertyToken(), second.propertyToken());
        if (mergeSupport == null) {
            return null;
        }
        Support fillerSupport = fillers.disjointness(first.filler(), second.filler());
        if (fillerSupport == null) {
            return null;
        }
        return mergeSupport.with(fillerSupport);
    }

    /**
     * D6, an incoming existential composed with an attachment: `≥n1 P1.F1` clashes with the
     * class A′ when some `≥n2 P2.A′` (A′ not TOP) has a class C attached to it in the store
     * (`≥n2 P2.A′ → C`), P1⁻ ⊑p P2, and F1, C are disjoint. An individual in both has a
     * P1-successor y in F1; the individual is then a P1⁻-successor of y, so a
     * P2-successor, and it is in A′, so y is in `≥n2 P2.A′` and hence in C; but y is in F1,
     * disjoint from C. The clause is `r1 ∧ A′ → FALSE`; A′ reaches it through the theory's
     * own arcs.
     */
    private void d6(List<HornInclusion> clashes, Restriction first, Restriction second) {
        if (!first.isExistential() || !second.isExistential() || store.isTop(second.filler())) {
            return;
        }
        List<Integer> attached = attachedClasses.get(store.identifierOf(second));
        if (attached == null) {
            return;
        }
        Support propertySupport = properties.supportOf(SignedProperties.flip(first.propertyToken()), second.propertyToken());
        if (propertySupport == null) {
            return;
        }
        for (int attachedClass : attached) {
            Support fillerSupport = fillers.disjointness(first.filler(), attachedClass);
            if (fillerSupport != null) {
                clashes.add(HornInclusion.of(List.of(store.identifierOf(first), second.filler()), HornInclusion.FALSE,
                        propertySupport.with(fillerSupport)));
            }
        }
    }


    private Support subClass(int subFiller, int superFiller) {
        return fillers.subsumption(subFiller, superFiller);
    }

}