package com.gtnewhorizons.galaxia.registry.dimension.provider;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import com.gtnewhorizons.galaxia.utility.Noise;

/**
 * A specific implementation of the WorldChunkManager to be used on Galaxia planets
 */
public class WorldChunkManagerSpace extends WorldChunkManager {

    private BiomeGenBase[][] biomeGeneratorMatrix;
    private Noise biomeNoise;

    private boolean cacheCreated = false;
    private int cacheX = 0;
    private int cacheZ = 0;
    private int cacheBiomeIndexX = 0;
    private int cacheBiomeIndexZ = 0;
    private double cacheNoiseX = 0;
    private double cacheNoiseZ = 0;

    /**
     * Assigns the seed to generate specific noise outputs
     *
     * @param seed The seed with which to generate
     */
    public void assignSeed(long seed) {
        // Ignore if no required noise
        if (biomeNoise != null) {
            return;
        }
        biomeNoise = new Noise(2048, seed);
    }

    /**
     * Provides the matrix of biomes to the manager
     *
     * @param biomes The matrix of biome gen bases to be used
     */
    public void provideBiomes(BiomeGenBase[][] biomes) {
        if (biomeGeneratorMatrix != null) {
            return;
        }
        biomeGeneratorMatrix = biomes;
    }

    /**
     * Returns the BiomeGenBase related to the given x, z coordinates in world
     *
     * @param x The checked x coordinate
     * @param z The checked z coordinate
     * @return The BiomeGenBase at that coordinate point on planet
     */
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        if (!(cacheCreated && x == cacheX && z == cacheZ)) {
            if (biomeGeneratorMatrix.length == 1 && biomeGeneratorMatrix[0].length == 1) {
                cacheX = x;
                cacheZ = z;
                cacheBiomeIndexX = 0;
                cacheBiomeIndexZ = 0;
                cacheCreated = true;
                return biomeGeneratorMatrix[cacheBiomeIndexX][cacheBiomeIndexZ];
            }
            int[] cacheBiomeIndex = getBiomeIndex(
                x,
                z,
                biomeGeneratorMatrix.length,
                biomeGeneratorMatrix[0].length,
                biomeNoise);
            cacheX = x;
            cacheZ = z;
            cacheBiomeIndexX = cacheBiomeIndex[0];
            cacheBiomeIndexZ = cacheBiomeIndex[1];
            cacheCreated = true;
        }
        return biomeGeneratorMatrix[cacheBiomeIndexX][cacheBiomeIndexZ];
    }

    /**
     * Gets the index of the biome in the matrix given indices to check
     *
     * @param x              The x index of the matrix to check
     * @param z              The z index of the matrix to check
     * @param matrixLengthX  The size of the matrix (i.e. 3 for a 3x3)
     * @param matrixLengthZ  The size of the matrix (i.e. 3 for a 3x3)
     * @param noiseGenerator The noise generator used for biome distribution
     * @return The index of the biome in the matrix
     */
    private int[] getBiomeIndex(int x, int z, int matrixLengthX, int matrixLengthZ, Noise noiseGenerator) {
        double[][] noise = Noise.simplexOctaves2D(x, z, 1, 1, 4, noiseGenerator);
        double noiseX = noise[1][0];
        double noiseZ = noise[2][0];
        noiseX = (noiseX + 1.D) / 2.D;
        noiseZ = (noiseZ + 1.D) / 2.D;
        noiseX *= matrixLengthX;
        noiseZ *= matrixLengthZ;
        cacheNoiseX = noiseX;
        cacheNoiseZ = noiseZ;
        return new int[] { (int) noiseX, (int) noiseZ };
    }

    /**
     * Gets all contributing biomes for use in smoothing methods
     *
     * @return An array of BiomeGenBases storing neighbouring biomes
     */
    public BiomeGenBase[] getLocalBiomes(int x, int z) {
        BiomeGenBase[] localBiomes = new BiomeGenBase[4];
        localBiomes[0] = this.getBiomeGenAt(x, z);
        int adjacentIndexX = cacheBiomeIndexX + 1 == biomeGeneratorMatrix.length ? 0 : cacheBiomeIndexX + 1;
        int adjacentIndexZ = cacheBiomeIndexZ + 1 == biomeGeneratorMatrix[0].length ? 0 : cacheBiomeIndexZ + 1;
        localBiomes[1] = biomeGeneratorMatrix[adjacentIndexX][cacheBiomeIndexZ];
        localBiomes[2] = biomeGeneratorMatrix[cacheBiomeIndexX][adjacentIndexZ];
        localBiomes[3] = biomeGeneratorMatrix[adjacentIndexX][adjacentIndexZ];
        return localBiomes;
    }

    public double[] getLocalBiomeSignificance(double divergence) {
        if (divergence == 0) return new double[] { 1, 0, 0, 0 };
        double d1 = Math.max(0, cacheNoiseX - cacheBiomeIndexX - 1 + divergence) / divergence;
        double d2 = Math.max(0, cacheNoiseZ - cacheBiomeIndexZ - 1 + divergence) / divergence;
        // four ways normalized symmetric blending in the corner
        return new double[] { d1 * d2, (1 - d1) * d2, d1 * (1 - d2), (1 - d1) * (1 - d2) };
    }

    public int getBiomeCount() {
        int matrixLength = biomeGeneratorMatrix.length;
        int matrixWidth = biomeGeneratorMatrix[0].length;
        return matrixLength * matrixWidth;
    }
}
