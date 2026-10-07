package uk.ac.ox.krr.logmap2.repair.hornSAT;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The correspondence directions a derived fact depends on. Empty means the fact holds
 * from the ontologies alone. A clause with a support is disabled while any of its
 * directions is masked, and blames all of them when it fires on the way to FALSE.
 */
public record Support(SortedSet<CorrespondenceDirection> directions) {

    public static final Support EMPTY = new Support(Collections.emptySortedSet());

    public Support {
        directions = Collections.unmodifiableSortedSet(new TreeSet<>(directions));
    }

    public static Support of(CorrespondenceDirection... directions) {
        return new Support(new TreeSet<>(List.of(directions)));
    }

    public Support with(Support other) {
        TreeSet<CorrespondenceDirection> union = new TreeSet<>(directions);
        union.addAll(other.directions);
        return new Support(union);
    }
    
    /** The smaller of two answers, either of which may be null (no answer). */
    public static Support least(Support first, Support second) {
        if (first == null) {
            return second;
        }
        if (second == null || first.size() <= second.size()) {
            return first;
        }
        return second;
    }

    public boolean isEmpty() {
        return directions.isEmpty();
    }

    public int size() {
        return directions.size();
    }
}