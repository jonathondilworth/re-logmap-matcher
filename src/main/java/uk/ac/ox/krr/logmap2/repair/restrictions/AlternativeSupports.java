package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import uk.ac.ox.krr.logmap2.Parameters;
import uk.ac.ox.krr.logmap2.repair.hornSAT.CorrespondenceDirection;
import uk.ac.ox.krr.logmap2.repair.hornSAT.Support;

/**
 * The supports under which a derived fact holds. Each is enough on its own, and none
 * contains another: masking a direction of the smaller one masks the larger one too, so
 * the larger one adds nothing. No support at all means the fact does not hold; the one
 * empty support means it holds from the ontologies alone. A rule emits its conclusion
 * once per support, so that masking the directions of one leaves the clause of another
 * in force: a plan is then accepted only when it breaks every way the clause is known to
 * hold. Design spec §6.3 ("one clause per distinct support set") and §7 ("once per
 * distinct (conclusion, support) pair").
 *
 * <p>Only the smallest supports are kept, as many as the setting
 * `max_alternative_supports` says (eight in the shipped parameters.txt and in
 * {@link Parameters}). Keeping all of them is not possible: on a Conference candidate
 * alignment one answer has 2,448 minimal supports. Every kept support is a genuine one, so
 * blame stays sound; what the bound gives up is a clause that still holds through a
 * support beyond it (docs/steps/18.md, docs/steps/21.md).
 */
public record AlternativeSupports(List<Support> supports) {

    // /** On the Conference inputs every result is the same with 8 and with 16 (step 18). */
    // static final int MOST_KEPT = 8;

    /** TEST: kept 8 (the Conference results are the same with 8 and with 16). */
    // static final int MOST_KEPT = 1;

    /**
     * Smaller supports first, then by their directions in order, so that equal answers are equal
     * lists.
     */
    private static final Comparator<Support> SMALLEST_FIRST = Comparator
            .comparingInt(Support::size)
            .thenComparing(AlternativeSupports::compareDirections);

    public static final AlternativeSupports NONE = new AlternativeSupports(List.of());
    public static final AlternativeSupports FACT = new AlternativeSupports(List.of(Support.EMPTY));

    public AlternativeSupports {
        supports = minimalOf(supports);
    }

    public static AlternativeSupports of(Support support) {
        return new AlternativeSupports(List.of(support));
    }

    /** The supports of a fact that holds when this one or the other does. */
    public AlternativeSupports or(AlternativeSupports other) {
        if (other.isNone() || holdsWithoutSupport()) {
            return this;
        }
        if (isNone() || other.holdsWithoutSupport()) {
            return other;
        }
        List<Support> either = new ArrayList<>(supports);
        either.addAll(other.supports);
        return new AlternativeSupports(either);
    }

    /** The supports of a fact that needs this one and the other: a support of each, joined. */
    public AlternativeSupports and(AlternativeSupports other) {
        if (isNone() || other.holdsWithoutSupport()) {
            return this;
        }
        if (other.isNone() || holdsWithoutSupport()) {
            return other;
        }
        List<Support> joined = new ArrayList<>();
        for (Support mine : supports) {
            for (Support theirs : other.supports) {
                joined.add(mine.with(theirs));
            }
        }
        return new AlternativeSupports(joined);
    }

    public boolean isNone() {
        return supports.isEmpty();
    }

    private boolean holdsWithoutSupport() {
        return supports.size() == 1 && supports.get(0).isEmpty();
    }

    private static List<Support> minimalOf(List<Support> candidates) {
        if (candidates.size() < 2) {
            // Nothing to choose from, whatever the setting says. NONE and FACT are built this
            // way when the class is loaded, so they must not depend on the setting.
            return List.copyOf(candidates);
        }
        List<Support> smallestFirst = new ArrayList<>(candidates);
        smallestFirst.sort(SMALLEST_FIRST);

        int mostKept = mostKept();
        List<Support> minimal = new ArrayList<>();
        for (Support candidate : smallestFirst) {
            if (minimal.size() == mostKept) {
                break;
            }
            if (!containsOneOf(candidate, minimal)) {
                minimal.add(candidate);
            }
        }
        return List.copyOf(minimal);
    }

    private static int mostKept() {
        refuseASettingBelowOne();
        return Parameters.max_alternative_supports;
    }

    /**
     * With no support kept, every derived fact would read as one that does not hold. Checked at
     * the start of every build and wherever several supports meet.
     */
    static void refuseASettingBelowOne() {
        if (Parameters.max_alternative_supports < 1) {
            throw new IllegalArgumentException("max_alternative_supports is "
                    + Parameters.max_alternative_supports
                    + ": a derived clause needs at least one support; set it to 1 or more in"
                    + " parameters.txt");
        }
    }

    private static boolean containsOneOf(Support candidate, List<Support> smaller) {
        for (Support support : smaller) {
            if (candidate.directions().containsAll(support.directions())) {
                return true;
            }
        }
        return false;
    }

    private static int compareDirections(Support first, Support second) {
        Iterator<CorrespondenceDirection> directionsOfSecond = second.directions().iterator();
        for (CorrespondenceDirection direction : first.directions()) {
            int order = direction.compareTo(directionsOfSecond.next());
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }
}