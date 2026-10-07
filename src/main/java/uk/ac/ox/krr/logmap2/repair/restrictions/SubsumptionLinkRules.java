package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;

import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * The subsumption links between restrictions, rules S1–S3 of design spec §7.2: `some`
 * and `≥n` are monotone in the property and the filler and downward in the number; `only`
 * is monotone in the filler and anti-monotone in the property; `≤n` is anti-monotone in
 * both and upward in the number; a self-edge is monotone in the property alone (§7.8,
 * the link HS3 needs). Each link `r1 → r2` carries the union of the supports of its
 * property and filler conditions. One instance per property kind: the rules are the same
 * over data properties, with data ranges as fillers.
 */
final class SubsumptionLinkRules {

    private final RestrictionStore store;
    private final PropertyKind kind;
    private final SupportedClosure properties;
    private final FillerRelations fillers;

    SubsumptionLinkRules(RestrictionStore store, PropertyKind kind, SupportedClosure properties, FillerRelations fillers) {
        this.store = store;
        this.kind = kind;
        this.properties = properties;
        this.fillers = fillers;
    }

    /** Every link between two distinct restrictions of this kind, in identifier order. */
    List<HornInclusion> links() {
        List<HornInclusion> links = new ArrayList<>();

        for (Restriction from : store.restrictions(kind)) {
            for (Restriction to : store.restrictions(kind)) {
                if (from.equals(to)) {
                    continue;
                }
                Support support = linkSupport(from, to);
                if (support != null) {
                    links.add(HornInclusion.of(List.of(store.identifierOf(from)), store.identifierOf(to), support));
                }
            }
        }

        return links;
    }

    /** The support under which `from ⊑ to`, or null when no rule gives it. */
    private Support linkSupport(Restriction from, Restriction to) {
        if (from.isExistential() && to.isExistential() && from.cardinality() >= to.cardinality()) {
            return s1(from, to);
        }
        if (from.isUniversal() && to.isUniversal()) {
            return s2(from, to);
        }
        if (from.kind() == RestrictionKind.AT_MOST && to.kind() == RestrictionKind.AT_MOST
                && from.cardinality() <= to.cardinality()) {
            return s3(from, to);
        }
        if (from.kind() == RestrictionKind.SELF && to.kind() == RestrictionKind.SELF) {
            return subProperty(from.property(), to.property());
        }
        return null;
    }

    /** S1: `≥n1 P1.F1 ⊑ ≥n2 P2.F2` when n1 ≥ n2, P1 ⊑p P2 and F1 ⊑c F2. */
    private Support s1(Restriction from, Restriction to) {
        return both(subProperty(from.property(), to.property()), subClass(from.filler(), to.filler()));
    }

    /** S2: `∀P1.F1 ⊑ ∀P2.F2` when P2 ⊑p P1 and F1 ⊑c F2 (fewer successors, a wider filler). */
    private Support s2(Restriction from, Restriction to) {
        return both(subProperty(to.property(), from.property()), subClass(from.filler(), to.filler()));
    }

    /** S3: `≤n1 P1.F1 ⊑ ≤n2 P2.F2` when n1 ≤ n2, P2 ⊑p P1 and F2 ⊑c F1 (fewer things to count). */
    private Support s3(Restriction from, Restriction to) {
        return both(subProperty(to.property(), from.property()), subClass(to.filler(), from.filler()));
    }

    private static Support both(Support first, Support second) {
        if (first == null || second == null) {
            return null;
        }
        return first.with(second);
    }

    private Support subProperty(int subProperty, int superProperty) {
        return properties.supportOf(subProperty, superProperty);
    }

    private Support subClass(int subFiller, int superFiller) {
        return fillers.subsumption(subFiller, superFiller);
    }
}