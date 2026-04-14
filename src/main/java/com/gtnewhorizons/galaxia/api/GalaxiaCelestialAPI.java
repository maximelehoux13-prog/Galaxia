package com.gtnewhorizons.galaxia.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialHierarchy;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public final class GalaxiaCelestialAPI {

    private GalaxiaCelestialAPI() {}

    public static void register(CelestialObject registration) {
        CelestialRegistry.register(registration);
    }

    public static boolean isFrozen() {
        return CelestialRegistry.isFrozen();
    }

    public static Optional<CelestialObject> get(CelestialObjectId id) {
        return CelestialRegistry.get(id);
    }

    public static Optional<CelestialObject> get(String id) {
        CelestialObjectId enumId = CelestialObjectId.fromString(id);
        return enumId != null ? CelestialRegistry.get(enumId) : Optional.empty();
    }

    public static List<CelestialObject> getAll() {
        return CelestialRegistry.getAll();
    }

    public static List<CelestialObject> getRoots() {
        return CelestialRegistry.getRoots();
    }

    public static CelestialObject getPrimaryRoot() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<CelestialObject> findByDimension(DimensionEnum dimension) {
        return CelestialRegistry.findByDimension(dimension);
    }

    public static CelestialHierarchy getHierarchy() {
        return CelestialRegistry.hierarchy;
    }

    public static Optional<CelestialObject> findBodyById(CelestialObjectId id) {
        return CelestialRegistry.findById(id);
    }

    public static List<CelestialObject> getChildren(CelestialObject parent) {
        return CelestialRegistry.hierarchy.childrenByParentId()
            .getOrDefault(parent.id(), Collections.emptyList());
    }

    public static List<CelestialObject> getChildren(CelestialObjectId parentId) {
        return CelestialRegistry.hierarchy.childrenByParentId()
            .getOrDefault(parentId, Collections.emptyList());
    }

    public static Map<CelestialObjectId, CelestialObject> getAllBodies() {
        return CelestialRegistry.hierarchy.bodiesById();
    }

    public static CelestialObject root() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<CelestialObject> findCurrentStar(int dimensionId) {
        for (DimensionEnum dim : DimensionEnum.values()) {
            if (dim.getId() == dimensionId) {
                return findCurrentStar(dim);
            }
        }
        return getPrimaryStar();
    }

    public static Optional<CelestialObject> findCurrentStar(DimensionEnum dim) {
        return findByDimension(dim).flatMap(body -> findAncestorOfClass(root(), body, CelestialObjectClass.STAR));
    }

    public static Optional<CelestialObject> findCelestialAnchor(DimensionEnum dim) {
        return findByDimension(dim).flatMap(body -> findAncestorOfClass(root(), body, CelestialObjectClass.STAR));
    }

    public static Optional<CelestialObject> getPrimaryStar() {
        return findFirstByClass(root(), CelestialObjectClass.STAR);
    }

    private static Optional<CelestialObject> findAncestorOfClass(CelestialObject current, CelestialObject target,
        CelestialObjectClass objectClass) {
        return findAncestorOfClass(current, target, objectClass, new ArrayList<>());
    }

    private static Optional<CelestialObject> findAncestorOfClass(CelestialObject current, CelestialObject target,
        CelestialObjectClass objectClass, List<CelestialObject> ancestors) {
        if (current == target) {
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                CelestialObject ancestor = ancestors.get(i);
                if (ancestor.objectClass() == objectClass) {
                    return Optional.of(ancestor);
                }
            }
            return Optional.empty();
        }

        for (CelestialObject child : getChildren(current)) {
            ArrayList<CelestialObject> nextAncestors = new ArrayList<>(ancestors);
            nextAncestors.add(current);
            Optional<CelestialObject> found = findAncestorOfClass(child, target, objectClass, nextAncestors);
            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    private static Optional<CelestialObject> findFirstByClass(CelestialObject current,
        CelestialObjectClass objectClass) {
        if (current.objectClass() == objectClass) {
            return Optional.of(current);
        }
        for (CelestialObject child : getChildren(current)) {
            Optional<CelestialObject> found = findFirstByClass(child, objectClass);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public static CelestialObject findBodyById(CelestialObject root, CelestialObjectId id) {
        if (root == null || id == null) return null;
        return findBodyByIdRec(root, id);
    }

    private static CelestialObject findBodyByIdRec(CelestialObject current, CelestialObjectId id) {
        for (CelestialObject child : getChildren(current)) {
            CelestialObject found = findBodyByIdRec(child, id);
            if (found != null) return found;
        }
        return null;
    }

    public static CelestialObject findStar(CelestialObject root, CelestialObject target) {
        if (root == null || target == null) return null;
        return findStarRec(root, target, null);
    }

    private static CelestialObject findStarRec(CelestialObject current, CelestialObject target,
        CelestialObject currentStar) {
        CelestialObject nextStar = current.objectClass() == CelestialObjectClass.STAR ? current : currentStar;
        if (current == target) return nextStar;
        for (CelestialObject child : getChildren(current)) {
            CelestialObject found = findStarRec(child, target, nextStar);
            if (found != null) return found;
        }
        return null;
    }

    public static CelestialObject findPlanetaryAnchor(CelestialObject root, CelestialObject target) {
        if (root == null || target == null) return target;
        CelestialObject anchor = findPlanetaryAnchorRec(root, target, null);
        return anchor != null ? anchor : target;
    }

    private static CelestialObject findPlanetaryAnchorRec(CelestialObject current, CelestialObject target,
        CelestialObject currentPlanet) {
        CelestialObjectClass cls = current.objectClass();
        CelestialObject nextPlanet = (cls == CelestialObjectClass.PLANET || cls == CelestialObjectClass.GAS_GIANT)
            ? current
            : currentPlanet;
        if (current == target) return nextPlanet != null ? nextPlanet : current;
        for (CelestialObject child : getChildren(current)) {
            CelestialObject found = findPlanetaryAnchorRec(child, target, nextPlanet);
            if (found != null) return found;
        }
        return null;
    }
}
