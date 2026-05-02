package com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketEntityTest;

import java.nio.ByteBuffer;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import com.gtnewhorizons.galaxia.core.ShaderHelper;

public class EntityTest extends Entity {

    public static int program = 0;
    public static int cellProgram = 0;
    public static int uProgram = 0;
    public static int vProgram = 0;
    public static int wProgram = 0;
    public static int projectionProgram = 0;
    public static int scalarProgram = 0;
    public static int toCellUProgram = 0;
    public static int toCellVProgram = 0;
    public static int toCellWProgram = 0;
    public static int cellNormUProgram = 0;
    public static int cellNormVProgram = 0;
    public static int cellNormWProgram = 0;

    public static int moveProgram = 0;
    public static int exhaustProgram = 0;

    public int ssboOUT;
    public int ssboU;
    public int ssboV;
    public int ssboW;
    public int ssboS;
    public int ssboScalar;
    public int ssboParticle;
    public int ssboParticleCount;
    public int ssboUW;
    public int ssboVW;
    public int ssboWW;
    public int ssboOUTCount;

    final int width = 8 * 10;
    final int height = 8 * 12;
    final float spacing = 0.5f;
    final int cubeLength = (int) (1.0f / spacing);
    final int perCube = cubeLength * cubeLength * cubeLength;

    public static final ByteBuffer zero = BufferUtils.createByteBuffer(4);

    float time = 0;

    public EntityTest(World world) {
        super(world);
        this.noClip = true;
        this.setSize(1.0F, 1.0F);
    }

    @Override
    protected void entityInit() {
        if (worldObj.isRemote) {
            createPrograms();

            // generate SSBO
            genSSBOs();

            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboOUT);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, ssboU);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, ssboV);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, ssboW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, ssboS);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 5, ssboScalar);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 6, ssboParticle);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 7, ssboParticleCount);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 8, ssboUW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 9, ssboVW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 10, ssboWW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 11, ssboOUTCount);

            GL42.glBindImageTexture(2, RenderEntityTest.rocketmask1, 0, false, 0, GL15.GL_READ_ONLY, GL11.GL_RGBA8);

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            // initialise all cells, UVW and S arrays
            runCellInit();
        }
    }

    public void runCellInit() {
        // instantiate base cells
        GL20.glUseProgram(cellProgram);
        // setting input variables, effectively (maxWidth = cubeLength * width)
        GL20.glUniform1i(GL20.glGetUniformLocation(cellProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellProgram, "maxHeight"), cubeLength * height);
        GL43.glDispatchCompute(width, height, width); // dispatch across all cells (2x2x2, because 1/h = 2)

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT); // wait for completion

        // generate full cell struct data
        GL20.glUseProgram(scalarProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(scalarProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(scalarProgram, "maxHeight"), cubeLength * height);
        GL43.glDispatchCompute(width / 2, height / 2, width / 2); // dispatch across all cells (4x4x4)

        // instantiate U velocity data
        GL20.glUseProgram(uProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(uProgram, "maxHeight"), cubeLength * height);
        GL20.glUniform1i(GL20.glGetUniformLocation(uProgram, "dX"), (cubeLength * width) + 1);
        GL43.glDispatchCompute(width + 1, height, width); // overall MAC grid is [X+1, Y+1, Z+1]

        // instantiate V velocity data
        GL20.glUseProgram(vProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(vProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(vProgram, "dY"), (cubeLength * height) + 1);
        GL43.glDispatchCompute(width, height + 1, width);

        // instantiate W velocity data
        GL20.glUseProgram(wProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(wProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(wProgram, "maxHeight"), cubeLength * height);
        GL43.glDispatchCompute(width, height, width + 1);
    }

    @Override
    public void onUpdate() {
        if (worldObj.isRemote) {
            time += 0.05f;

            compute();
        }
    }

    private void compute() {

        runExhaust(); // add particles from exhaust

        moveParticles(); // move particles dT (0.05 because 20tps)

        resetCellVelocities(); // set all velocities and their weights to 0

        // to do: set all cells to either solid or air
        // to do: then for each particle, if its cell is an air cell, make it a fluid cell

        transferToCells();



        /*
         * GL20.glUseProgram(projectionProgram);
         * int rb = GL20.glGetUniformLocation(projectionProgram, "RB");
         * GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxWidth"), cubeLength * width);
         * GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxHeight"), cubeLength * height);
         * GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "wxh"), cubeLength * width * cubeLength *
         * height);
         * GL20.glUniform1i(rb, 0);
         * GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, 1021);
         * for (int i = 0; i < 20; i++) {
         * GL43.glDispatchCompute(width / 8, height / 4, width / 2);
         * GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
         * GL20.glUniform1i(rb, 1);
         * GL43.glDispatchCompute(width / 8, height / 4, width / 2);
         * GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
         * GL20.glUniform1i(rb, 0);
         * }
         * GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
         * System.out.println(GL33.glGetQueryObjectui64(1021, GL15.GL_QUERY_RESULT));
         */

        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, 1021);

        GL20.glUseProgram(program);

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboOUT);

        GL20.glUniform1i(GL20.glGetUniformLocation(program, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "maxHeight"), cubeLength * height);

        //GL43.glDispatchCompute(width / (int)(8.0 * spacing), height / (int)(8.0 * spacing), width / (int)(4.0 * spacing));
        GL43.glDispatchCompute(width / 4, height / 4, width / 2);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);

        GL20.glUseProgram(0);
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        System.out.println(GL33.glGetQueryObjectui64(1021, GL15.GL_QUERY_RESULT));
    }

    private void transferToCells() {
        // 3 different programs for each component
        GL20.glUseProgram(toCellUProgram);

        // same thread width as before for particles
        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellUProgram, "width"), width / 4 * 8);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellUProgram, "height"), height / 4 * 8);

        // various uniforms to be precomputed outside the loop (more details in the shader code)
        GL20.glUniform1i(GL20.glGetUniformLocation(toCellUProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(toCellUProgram, "maxHeight"), cubeLength * height);

        GL20.glUniform1f(GL20.glGetUniformLocation(toCellUProgram, "maxWidthDNeg"), (cubeLength * width) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellUProgram, "maxHeightDNeg"), (cubeLength * height) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellUProgram, "maxWidthDPos"), (cubeLength * width) + 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellUProgram, "wxh"), ((cubeLength * width) + 1) * (cubeLength * height));

        GL43.glDispatchCompute(width / 4, height / 4, width / 4);


        GL20.glUseProgram(toCellVProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellVProgram, "width"), width / 4 * 8);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellVProgram, "height"), height / 4 * 8);

        GL20.glUniform1i(GL20.glGetUniformLocation(toCellVProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(toCellVProgram, "maxHeight"), cubeLength * height);

        GL20.glUniform1f(GL20.glGetUniformLocation(toCellVProgram, "maxWidthDNeg"), (cubeLength * width) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellVProgram, "maxHeightDNeg"), (cubeLength * height) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellVProgram, "maxHeightDPos"), (cubeLength * height) + 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellVProgram, "wxh"), (cubeLength * width) * ((cubeLength * height) + 1));

        GL43.glDispatchCompute(width / 4, height / 4, width / 4);


        GL20.glUseProgram(toCellWProgram);

        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellWProgram, "width"), width / 4 * 8);
        GL30.glUniform1ui(GL20.glGetUniformLocation(toCellWProgram, "height"), height / 4 * 8);

        GL20.glUniform1i(GL20.glGetUniformLocation(toCellWProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(toCellWProgram, "maxHeight"), cubeLength * height);

        GL20.glUniform1f(GL20.glGetUniformLocation(toCellWProgram, "maxWidthDNeg"), (cubeLength * width) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellWProgram, "maxHeightDNeg"), (cubeLength * height) - 1);
        GL20.glUniform1f(GL20.glGetUniformLocation(toCellWProgram, "wxh"), (cubeLength * width) * (cubeLength * height));

        GL43.glDispatchCompute(width / 4, height / 4, width / 4);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);


        // distribute the velocity so that weight = 1 per cell
        GL20.glUseProgram(cellNormUProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormUProgram, "maxWidth"), (cubeLength * width) + 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormUProgram, "maxHeight"), cubeLength * height);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormUProgram, "wxh"), ((cubeLength * width) + 1) * (cubeLength * height));
        GL43.glDispatchCompute((width / (int)(8.0 * spacing)) + 1, height / (int)(8.0 * spacing), width / (int)(4.0 * spacing));


        GL20.glUseProgram(cellNormVProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormVProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormVProgram, "maxHeight"), (cubeLength * height) + 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormVProgram, "wxh"), (cubeLength * width) * ((cubeLength * height) + 1));
        GL43.glDispatchCompute(width / (int)(8.0 * spacing), (height / (int)(8.0 * spacing)) + 1, width / (int)(4.0 * spacing));


        GL20.glUseProgram(cellNormWProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormWProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormWProgram, "maxHeight"), cubeLength * height);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellNormWProgram, "wxh"), cubeLength * width * cubeLength * height);
        GL43.glDispatchCompute(width / (int)(8.0 * spacing), height / (int)(8.0 * spacing), (width / (int)(4.0 * spacing)) + 1);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void moveParticles() {
        GL20.glUseProgram(moveProgram);

        // passing precomputed thread width and height values,
        // I dispatch x = width / 4 and y = height / 4. internally the local size of
        // the shader is 8x8x4, so thread width = (width / 4) * 8
        // and thread height = (height / 4) * 8
        GL30.glUniform1ui(GL20.glGetUniformLocation(moveProgram, "width"), width / 4 * 8);
        GL30.glUniform1ui(GL20.glGetUniformLocation(moveProgram, "height"), height / 4 * 8);

        // unknown array size (local 8x8x4)
        GL43.glDispatchCompute(width / 4, height / 4, width / 4);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void runExhaust() {
        GL20.glUseProgram(exhaustProgram);

        // currently arbitrary 50, when the movement tracking is added based on a start pos, this needs to move with it
        GL20.glUniform1f(GL20.glGetUniformLocation(exhaustProgram, "xPos"), 10);
        GL20.glUniform1f(GL20.glGetUniformLocation(exhaustProgram, "yPos"), 150);
        GL20.glUniform1f(GL20.glGetUniformLocation(exhaustProgram, "zPos"), 10);

        GL20.glUniform1f(GL20.glGetUniformLocation(exhaustProgram, "seed"), (float)Math.random());

        // just a serial program ran on the gpu, could parallelize later if needed
        GL43.glDispatchCompute(1, 1, 1);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    public void genSSBOs() {
        ssboOUT = GL15.glGenBuffers();
        ssboU = GL15.glGenBuffers();
        ssboV = GL15.glGenBuffers();
        ssboW = GL15.glGenBuffers();
        ssboS = GL15.glGenBuffers();
        ssboScalar = GL15.glGenBuffers();
        ssboParticle = GL15.glGenBuffers();
        ssboParticleCount = GL15.glGenBuffers();
        ssboUW = GL15.glGenBuffers();
        ssboVW = GL15.glGenBuffers();
        ssboWW = GL15.glGenBuffers();
        ssboOUTCount = GL15.glGenBuffers();

        // spotless:off

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboOUT);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * height * width * 3 * 4,
            GL15.GL_DYNAMIC_DRAW); // max cubes sent to render


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboU);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            (width + 1) * width * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // MAC grid U


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboV);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * (width + 1) * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // MAC grid V


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboW);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * (height + 1) * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // MAC grid W


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboS);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * height * perCube,
            GL15.GL_STATIC_DRAW); // cell s value


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboScalar);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * height * perCube * 2 * 4,
            GL15.GL_STATIC_DRAW); // cell data


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboParticle);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * height * perCube * 2 * 4,
            GL15.GL_DYNAMIC_COPY); // particle data

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboParticleCount);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            4,
            GL15.GL_DYNAMIC_COPY); // particle count

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboUW);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            (width + 1) * width * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // weights for U


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboVW);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * (width + 1) * height * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // weights for V


        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboWW);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * (height + 1) * perCube * 4,
            GL15.GL_DYNAMIC_COPY); // weights for W

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboOUTCount);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            4,
            GL15.GL_DYNAMIC_COPY); // number of cells to draw

        // spotless:on
    }

    private static void createPrograms() {
        if (program == 0) {
            program = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/test.comp");
        }
        if (cellProgram == 0) {
            cellProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cellinit.comp");
        }
        if (projectionProgram == 0) {
            projectionProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/projection.comp");
        }
        if (uProgram == 0) {
            uProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/uinit.comp");
        }
        if (vProgram == 0) {
            vProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/winit.comp");
        }
        if (wProgram == 0) {
            wProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/winit.comp");
        }
        if (scalarProgram == 0) {
            scalarProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/scalar.comp");
        }
        if (moveProgram == 0) {
            moveProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_move.comp");
        }
        if (exhaustProgram == 0) {
            exhaustProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_exhaust.comp");
        }
        if (toCellUProgram == 0) {
            toCellUProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_to_cell_u.comp");
        }
        if (toCellVProgram == 0) {
            toCellVProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_to_cell_v.comp");
        }
        if (toCellWProgram == 0) {
            toCellWProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle_to_cell_w.comp");
        }
        if (cellNormUProgram == 0) {
            cellNormUProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_normalization_u.comp");
        }
        if (cellNormVProgram == 0) {
            cellNormVProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_normalization_v.comp");
        }
        if (cellNormWProgram == 0) {
            cellNormWProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cell_normalization_w.comp");
        }
    }

    public void resetCellVelocities() {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboU);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboV);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboW);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboUW);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboVW);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboWW);
        GL43.glClearBufferData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, zero);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tagCompund) {

    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tagCompound) {

    }
}
