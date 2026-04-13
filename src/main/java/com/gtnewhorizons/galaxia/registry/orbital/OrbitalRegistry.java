package com.gtnewhorizons.galaxia.registry.orbital;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public final class OrbitalRegistry {

    private OrbitalRegistry() {}

    public static CelestialObject root() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<CelestialObject> findByDimension(DimensionEnum dim) {
        return CelestialRegistry.findByDimension(dim);
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

        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
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
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
            Optional<CelestialObject> found = findFirstByClass(child, objectClass);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
