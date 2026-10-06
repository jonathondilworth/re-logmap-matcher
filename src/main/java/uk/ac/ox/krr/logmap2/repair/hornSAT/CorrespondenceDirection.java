package uk.ac.ox.krr.logmap2.repair.hornSAT;

/**
 * One direction of a correspondence under repair, `origin ⊑ target` between two class
 * identifiers, which is what a Dowling–Gallier mapping clause stands for and what a plan
 * masks. A derived clause that holds only because of this direction names it in its
 * support. Design spec §8.3.1.
 */
public record CorrespondenceDirection(int origin, int target) implements Comparable<CorrespondenceDirection> {

    @Override
    public int compareTo(CorrespondenceDirection other) {
        if (origin != other.origin) {
            return Integer.compare(origin, other.origin);
        }
        return Integer.compare(target, other.target);
    }

    @Override
    public String toString() {
        return origin + " -> " + target;
    }
}