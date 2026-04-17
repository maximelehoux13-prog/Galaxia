package com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketEntityTest;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

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
    public static int particleProgram = 0;

    public int ssboOUT;
    public int ssboU;
    public int ssboV;
    public int ssboW;
    public int ssboS;
    public int ssboScalar;
    public int ssboParticle;

    final int width = 8 * 10;
    final int height = 8 * 12;
    final float spacing = 0.5f;
    final int cubeLength = (int) (1.0f / spacing);
    final int perCube = cubeLength * cubeLength * cubeLength;

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

            GL42.glBindImageTexture(0, RenderEntityTest.rocketmask1, 0, false, 0, GL15.GL_READ_WRITE, GL11.GL_RGBA8);

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
        GL20.glUniform1i(GL20.glGetUniformLocation(uProgram, "dX"), cubeLength * (width + 1));
        GL43.glDispatchCompute(width + 1, height, width); // overall MAC grid is [X+1, Y+1, Z+1]

        // instantiate V velocity data
        GL20.glUseProgram(vProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(vProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(vProgram, "dY"), cubeLength * (height + 1));
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
        GL20.glUseProgram(particleProgram); // add particles
        GL43.glDispatchCompute(width / 4, height / 4, width / 4); // unknown array size (local 4x4x4)

        GL20.glUseProgram(projectionProgram);

        int rb = GL20.glGetUniformLocation(projectionProgram, "RB");

        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxHeight"), cubeLength * height);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "wxh"), cubeLength * width * cubeLength * height);

        GL20.glUniform1i(rb, 0);
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, 1021);
        for (int i = 0; i < 20; i++) {
            GL43.glDispatchCompute(width / 8, height / 4, width / 2);

            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

            GL20.glUniform1i(rb, 1);

            GL43.glDispatchCompute(width / 8, height / 4, width / 2);

            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

            GL20.glUniform1i(rb, 0);
        }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);

        System.out.println(GL33.glGetQueryObjectui64(1021, GL15.GL_QUERY_RESULT));

        // old stuff

        GL20.glUseProgram(program);

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboOUT);

        int timeLoc = GL20.glGetUniformLocation(program, "u_Time");

        GL20.glUniform1f(timeLoc, time);

        GL43.glDispatchCompute(1, 1, 1);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);

        GL20.glUseProgram(0);
    }

    public void genSSBOs() {
        ssboOUT = GL15.glGenBuffers();
        ssboU = GL15.glGenBuffers();
        ssboV = GL15.glGenBuffers();
        ssboW = GL15.glGenBuffers();
        ssboS = GL15.glGenBuffers();
        ssboScalar = GL15.glGenBuffers();
        ssboParticle = GL15.glGenBuffers();

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboOUT);
        // max cubes sent to render
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * height * width * 3 * 4,
            GL15.GL_DYNAMIC_DRAW);

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
        // cell s value
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * height * perCube,
            GL15.GL_STATIC_DRAW);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboScalar);
        // cell data
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * height * perCube * 2 * 4,
            GL15.GL_STATIC_DRAW);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboParticle);
        GL15.glBufferData(
            GL43.GL_SHADER_STORAGE_BUFFER,
            width * width * height * perCube * 2 * 4,
            GL15.GL_DYNAMIC_COPY); // particle data
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
        if (particleProgram == 0) {
            particleProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/particle.comp");
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tagCompund) {

    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tagCompound) {

    }
}
