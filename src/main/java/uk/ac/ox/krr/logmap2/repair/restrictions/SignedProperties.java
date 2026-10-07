package uk.ac.ox.krr.logmap2.repair.restrictions;

/**
 * A property expression as one int, the node currency of the property closure: `2R` for
 * a named property R and `2R + 1` for its inverse `R⁻`. Data properties have no inverses
 * and are always even. Design spec §3.1a.
 */
final class SignedProperties {

    private SignedProperties() {
    }

    static int token(int property, boolean incoming) {
        return 2 * property + (incoming ? 1 : 0);
    }

    static int outgoing(int property) {
        return 2 * property;
    }

    static int flip(int token) {
        return token ^ 1;
    }

    static int identifier(int token) {
        return token >> 1;
    }

    static boolean isIncoming(int token) {
        return (token & 1) == 1;
    }
}