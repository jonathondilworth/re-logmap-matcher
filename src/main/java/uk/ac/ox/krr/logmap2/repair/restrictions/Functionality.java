package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * Which signed properties are functional, read from the store's clauses attached to TOP
 * (`TOP → atMost(1, F, TOP)` is `Functional(F)`, `TOP → atMost(1, F⁻, TOP)` is
 * `InverseFunctional(F)`; `rdfs:Literal` is the top for a data property), and under
 * which support a signed property lies below a functional one through the property
 * closure of its kind. Functionality reaches down the property hierarchy only.
 */
final class Functionality {

    private final SortedSet<Integer> functionalProperties = new TreeSet<>();
    private final SupportedClosure properties;

    Functionality(RestrictionStore store, PropertyKind kind, SupportedClosure properties) {
        this.properties = properties;
        for (HornInclusion inclusion : store.inclusions()) {
            if (isFunctionalityOfTop(store, kind, inclusion)) {
                functionalProperties.add(store.restriction(inclusion.head()).propertyToken());
            }
        }
    }

    /**
     * The smallest support under which both signed properties lie below one functional
     * property, so that a successor through each is the same individual; null when there
     * is none.
     */
    Support mergeSupportOf(int firstProperty, int secondProperty) {
        Support best = null;

        for (int functional : functionalProperties) {
            Support first = properties.supportOf(firstProperty, functional);
            Support second = properties.supportOf(secondProperty, functional);
            if (first == null || second == null) {
                continue;
            }
            best = Support.least(best, first.with(second));
        }

        return best;
    }

    private static boolean isFunctionalityOfTop(RestrictionStore store, PropertyKind kind, HornInclusion inclusion) {
        if (!inclusion.body().equals(List.of(store.top())) || !store.isRestriction(inclusion.head())) {
            return false;
        }
        Restriction head = store.restriction(inclusion.head());
        return head.propertyKind() == kind && head.kind() == RestrictionKind.AT_MOST && head.cardinality() == 1 && store.isTopFillerFor(kind, head.filler());
    }
}