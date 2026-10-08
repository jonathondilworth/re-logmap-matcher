package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.ox.krr.logmap2.indexing.IndexManager;
import uk.ac.ox.krr.logmap2.repair.hornSAT.CorrespondenceDirection;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornClause;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;

/**
 * The restriction clauses of one Dowling–Gallier build: the store's own clauses plus the
 * links and clashes derived from the current correspondences, each once for every minimal
 * support it holds under, so that a plan masking one support leaves the clause in force
 * through another. Rebuilt at every build, so a correspondence removed by an earlier plan
 * no longer supports anything. Derivation takes two passes: the S-links of both kinds
 * first, then the clashes, whose class disjointness walks the class closure bridged by
 * those links and by the store's attachments. The same bridged closure gives each union
 * the common ancestors of its members (rule U1, {@link UnionRules}). Design spec §6.1 (the
 * edge sets), §6.1b (the bridge), §6.3 (supports), §8.1 (the contract) and §8.5 (once per
 * build).
 */
public final class RestrictionReasoning {

    private final IndexManager index;
    private final RestrictionStore store;

    public RestrictionReasoning(IndexManager index) {
        this.index = index;
        this.store = index.getRestrictionStore();
    }

    /** The proposition every propagation starts from besides the entity, so that clauses on TOP hold everywhere. */
    public int top() {
        return store.top();
    }

    /**
     * @param fixedMappings correspondences already repaired, both directions, never masked
     * @param mappingsUnderRepair correspondences under repair, both directions of an equivalence
     * @param removedDirections the mapping clauses earlier plans removed in this repair
     * @param propertyDirections the surviving directions of the object- and data-property correspondences under repair
     */
    public List<HornInclusion> clausesFor(Map<Integer, Set<Integer>> fixedMappings,
            Map<Integer, Set<Integer>> mappingsUnderRepair, Set<HornClause> removedDirections,
            Collection<CorrespondenceDirection> propertyDirections) {

        AlternativeSupports.refuseASettingBelowOne();
                
        SupportedClosure classes = classClosure(fixedMappings, mappingsUnderRepair, removedDirections);
        SupportedClosure objectProperties = propertyClosure(PropertyKind.OBJECT, CorrespondenceDirection.Kind.OBJECT_PROPERTY, propertyDirections);
        SupportedClosure dataProperties = propertyClosure(PropertyKind.DATA, CorrespondenceDirection.Kind.DATA_PROPERTY, propertyDirections);
        FillerRelations classFillers = new ClassFillers(store, classes, new Disjointness(index, store, classes));
        FillerRelations datatypeFillers = new DatatypeFillers(store);

        List<HornInclusion> objectLinks = new SubsumptionLinkRules(store, PropertyKind.OBJECT, objectProperties, classFillers).links();
        List<HornInclusion> dataLinks = new SubsumptionLinkRules(store, PropertyKind.DATA, dataProperties, datatypeFillers).links();

        
        SupportedClosure freshClasses = classClosure(fixedMappings, mappingsUnderRepair, removedDirections);
        SupportedClosure bridge = bridgedClosure(freshClasses, objectLinks, dataLinks);
        FillerRelations bridgedClassFillers = new ClassFillers(store, classes, new Disjointness(index, store, bridge));


        List<HornInclusion> clauses = new ArrayList<>(store.inclusions());
        clauses.addAll(new UnionRules(store, bridge).inclusions());
        clauses.addAll(objectLinks);
        clauses.addAll(clashesOver(PropertyKind.OBJECT, objectProperties, bridgedClassFillers));
        clauses.addAll(dataLinks);
        clauses.addAll(clashesOver(PropertyKind.DATA, dataProperties, datatypeFillers));
        return clauses;
    }


    /** The clashes, and for object properties the self-edge memberships, among the restrictions of one property kind. */
    private List<HornInclusion> clashesOver(PropertyKind kind, SupportedClosure properties, FillerRelations fillers) {
        Functionality functionality = new Functionality(store, kind, properties);
        List<HornInclusion> clauses = new ArrayList<>();
        if (kind == PropertyKind.OBJECT) {
            clauses.addAll(new SelfRules(store, properties, functionality).memberships());
        }
        return clauses;
    }


    /**
     * A fresh class closure extended with the edges that hold through a restriction: the
     * store's single-atom clauses with a restriction on either side (attachments `A → r`,
     * `r → B`, companions), and this build's S-links, each under its own support. A TOP
     * body, a conjunctive body and FALSE give no edge. Every edge is a genuine inclusion,
     * so a disjointness found through them is one. Design spec §6.1b.
     */
    private SupportedClosure bridgedClosure(SupportedClosure classes, List<HornInclusion> objectLinks, List<HornInclusion> dataLinks) {
        for (HornInclusion inclusion : store.inclusions()) {
            if (isThroughARestriction(inclusion)) {
                classes.addFact(inclusion.body().get(0), inclusion.head());
            }
        }
        for (List<HornInclusion> links : List.of(objectLinks, dataLinks)) {
            for (HornInclusion link : links) {
                classes.addSupported(link.body().get(0), link.head(), link.support());
            }
        }
        return classes;
    }

    private boolean isThroughARestriction(HornInclusion inclusion) {
        if (inclusion.body().size() != 1 || inclusion.isClash() || store.isTop(inclusion.body().get(0))) {
            return false;
        }
        return store.isRestriction(inclusion.body().get(0)) || store.isRestriction(inclusion.head());
    }


    private SupportedClosure classClosure(Map<Integer, Set<Integer>> fixedMappings,
            Map<Integer, Set<Integer>> mappingsUnderRepair, Set<HornClause> removedDirections) {
        SupportedClosure classes = new SupportedClosure();

        for (Map.Entry<Integer, Set<Integer>> parentAndChildren : index.getDirectSubClasses(false).entrySet()) {
            for (int child : parentAndChildren.getValue()) {
                classes.addFact(child, parentAndChildren.getKey());
            }
        }
        for (Map.Entry<Integer, Set<Integer>> classAndEquivalents : index.getEquivalentClasses().entrySet()) {
            for (int equivalent : classAndEquivalents.getValue()) {
                classes.addFact(classAndEquivalents.getKey(), equivalent);
            }
        }
        for (HornInclusion inclusion : store.inclusions()) {
            if (isClassInclusion(inclusion)) {
                classes.addFact(inclusion.body().get(0), inclusion.head());
            }
        }
        for (Map.Entry<Integer, Set<Integer>> originAndTargets : fixedMappings.entrySet()) {
            for (int target : originAndTargets.getValue()) {
                classes.addFact(originAndTargets.getKey(), target);
            }
        }
        for (Map.Entry<Integer, Set<Integer>> originAndTargets : mappingsUnderRepair.entrySet()) {
            int origin = originAndTargets.getKey();
            for (int target : originAndTargets.getValue()) {
                if (!removedDirections.contains(mappingClauseOf(origin, target))) {
                    classes.addCorrespondence(origin, target, CorrespondenceDirection.ofClasses(origin, target));
                }
            }
        }

        return classes;
    }

    /**
     * The sub-property facts of one kind, and each surviving direction of that kind as a
     * supported edge, over signed properties. Every object edge `S ⊑ T` also holds as
     * `S⁻ ⊑ T⁻` (the inverse is monotone), so each fact and each direction enters with
     * its flipped dual, the direction's dual under the same support: the mirrored link
     * of design spec §6.1a.
     */
    private SupportedClosure propertyClosure(PropertyKind kind, CorrespondenceDirection.Kind directionKind,
            Collection<CorrespondenceDirection> propertyDirections) {
        SupportedClosure properties = subPropertyFacts(kind);
        for (CorrespondenceDirection direction : propertyDirections) {
            if (direction.kind() != directionKind) {
                continue;
            }
            int origin = SignedProperties.outgoing(direction.origin());
            int target = SignedProperties.outgoing(direction.target());
            properties.addCorrespondence(origin, target, direction);
            if (kind == PropertyKind.OBJECT) {
                properties.addCorrespondence(SignedProperties.flip(origin), SignedProperties.flip(target), direction);
            }
        }
        return properties;
    }


    private SupportedClosure subPropertyFacts(PropertyKind kind) {
        SupportedClosure properties = new SupportedClosure();
        for (int subProperty : store.propertiesWithSuperProperties(kind)) {
            for (int superProperty : store.superPropertiesOf(kind, subProperty)) {
                properties.addFact(subProperty, superProperty);
                if (kind == PropertyKind.OBJECT) {
                    properties.addFact(SignedProperties.flip(subProperty), SignedProperties.flip(superProperty));
                }
            }
        }
        return properties;
    }

    /** A clause `X → Y` between two class propositions (a fresh-class definition, mostly). */
    private boolean isClassInclusion(HornInclusion inclusion) {
        if (inclusion.body().size() != 1 || inclusion.isClash()) {
            return false;
        }
        int body = inclusion.body().get(0);
        return !store.isRestriction(body) && !store.isRestriction(inclusion.head()) && !store.isTop(body);
    }

    /**
     * The mapping clause Dowling–Gallier builds for a direction (addMappingClauses1N): L2R
     * from the smaller identifier, R2L otherwise; equality ignores the label.
     */
    private static HornClause mappingClauseOf(int origin, int target) {
        if (origin < target) {
            return new HornClause(origin, target, 0, HornClause.MAP, HornClause.L2R);
        }
        return new HornClause(target, origin, 0, HornClause.MAP, HornClause.R2L);
    }
}