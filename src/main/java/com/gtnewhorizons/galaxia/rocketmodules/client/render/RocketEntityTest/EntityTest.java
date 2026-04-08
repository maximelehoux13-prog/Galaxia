package com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketEntityTest;

import com.gtnewhorizons.galaxia.core.ShaderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.lwjgl.opengl.*;

public class EntityTest extends Entity {
    public static int program = 0;
    public static int cellProgram = 0;
    public static int uProgram = 0;
    public static int vProgram = 0;
    public static int wProgram = 0;
    public static int projectionProgram = 0;

    public int ssboOUT;
    public int ssboU;
    public int ssboV;
    public int ssboW;
    public int ssboS;

    final int width = 4 * 20;
    final int height = 4 * 25;
    final float spacing = 0.5f;
    final int cubeLength = (int)(1.0f / spacing);
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
            if (program == 0) {
                program = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/test.comp");
            }
            if (cellProgram == 0) {
                cellProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/cellinit.comp");
            }
            if (uProgram == 0) {
                uProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/uinit.comp");
            }
            if (vProgram == 0) {
                vProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/vinit.comp");
            }
            if (wProgram == 0) {
                wProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/winit.comp");
            }
            if (projectionProgram == 0) {
                projectionProgram = ShaderHelper.createComputeProgram("/assets/galaxia/shaders/projection.comp");
            }

            // generate SSBO
            genSSBOs();

            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboOUT);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, ssboU);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, ssboV);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, ssboW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 4, ssboS);

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

            // initialise all the UVW and S arrays
            runCellInit();
        }
    }

    public void genSSBOs() {
        ssboOUT = GL15.glGenBuffers();
        ssboU = GL15.glGenBuffers();
        ssboV = GL15.glGenBuffers();
        ssboW = GL15.glGenBuffers();
        ssboS = GL15.glGenBuffers();

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboOUT);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, width * height * width * 3 * 4, GL15.GL_DYNAMIC_DRAW); // max cubes sent to render

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboU);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (width + 1) * width * height * perCube * 4, GL15.GL_DYNAMIC_DRAW); // MAC grid U

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboV);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, width * (width + 1) * height * perCube * 4, GL15.GL_DYNAMIC_DRAW); // MAC grid V

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboW);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, width * width * (height + 1) * perCube * 4, GL15.GL_DYNAMIC_DRAW); // MAC grid W

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboS);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, width * width * height * perCube, GL15.GL_DYNAMIC_DRAW); // cell data
    }

    public void runCellInit() {
        GL20.glUseProgram(cellProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(cellProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(cellProgram, "maxHeight"), cubeLength * height);

        GL43.glDispatchCompute(width, height, width);


        GL20.glUseProgram(uProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(uProgram, "maxHeight"), cubeLength * height);
        GL20.glUniform1i(GL20.glGetUniformLocation(uProgram, "dX"), cubeLength * (width + 1));

        GL43.glDispatchCompute(width + 1, height, width);


        GL20.glUseProgram(vProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(vProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(vProgram, "dY"), cubeLength * (height + 1));

        GL43.glDispatchCompute(width, height + 1, width);


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
        GL20.glUseProgram(projectionProgram);

        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxWidth"), cubeLength * width);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "maxHeight"), cubeLength * height);
        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "wxh"), cubeLength * width * cubeLength * height);

        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "RB"), 0);

        GL43.glDispatchCompute(width / 4, height / 4, width / 4);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        GL20.glUniform1i(GL20.glGetUniformLocation(projectionProgram, "RB"), 1);

        GL43.glDispatchCompute(width / 4, height / 4, width / 4);


        GL20.glUseProgram(program);

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboOUT);

        int timeLoc = GL20.glGetUniformLocation(program, "u_Time");

        GL20.glUniform1f(timeLoc, time);

        GL43.glDispatchCompute(1, 1, 1);

        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);

        GL20.glUseProgram(0);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tagCompund) {

    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tagCompound) {

    }
}
