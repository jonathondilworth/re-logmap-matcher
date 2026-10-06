package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * Which properties are functional, read from the store's clauses attached to TOP
 * (`TOP → atMost(1, F, TOP)` is `Functional(F)`), and under which support a property lies
 * below a functional one through the property closure. Functionality reaches down the
 * property hierarchy only.
 */
final class Functionality {

    private final SortedSet<Integer> functionalProperties = new TreeSet<>();
    private final SupportedClosure properties;

    Functionality(RestrictionStore store, SupportedClosure properties) {
        this.properties = properties;
        for (HornInclusion inclusion : store.inclusions()) {
            if (isFunctionalityOfTop(store, inclusion)) {
                functionalProperties.add(store.restriction(inclusion.head()).property());
            }
        }
    }

    /**
     * The smallest support under which both properties lie below one functional property,
     * so that a successor through each is the same individual; null when there is none.
     */
    Support mergeSupportOf(int firstProperty, int secondProperty) {
        Support best = null;

        for (int functional : functionalProperties) {
            Support first = properties.supportOf(firstProperty, functional);
            Support second = properties.supportOf(secondProperty, functional);
            if (first == null || second == null) {
                continue;
            }
            Support candidate = first.with(second);
            if (best == null || candidate.size() < best.size()) {
                best = candidate;
            }
        }

        return best;
    }

    private static boolean isFunctionalityOfTop(RestrictionStore store, HornInclusion inclusion) {
        if (!inclusion.body().equals(List.of(store.top())) || !store.isRestriction(inclusion.head())) {
            return false;
        }
        Restriction head = store.restriction(inclusion.head());
        return head.kind() == RestrictionKind.AT_MOST && head.cardinality() == 1 && store.isTop(head.filler());
    }
}