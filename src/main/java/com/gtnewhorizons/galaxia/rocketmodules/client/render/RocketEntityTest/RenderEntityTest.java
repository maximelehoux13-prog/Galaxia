package com.gtnewhorizons.galaxia.rocketmodules.client.render.RocketEntityTest;

import com.gtnewhorizons.galaxia.core.ShaderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

public class RenderEntityTest extends Render {
    private static int program = 0;
    int vaoID = 0;
    int vboID = 0;

    int viewID = 0;
    int projectionID = 0;

    public RenderEntityTest() {
        if (program == 0) {
            program = ShaderHelper.createProgram("/assets/galaxia/shaders/test.vert", "/assets/galaxia/shaders/test.geom", "/assets/galaxia/shaders/test.frag");
        }

        GL20.glUseProgram(program);

        viewID = GL20.glGetUniformLocation(program, "view");
        projectionID = GL20.glGetUniformLocation(program, "projection");

        GL20.glUseProgram(0);

        VAOInit();
    }

    public void VAOInit() {
        //FloatBuffer dataBuffer = FloatBuffer.wrap(vertices);
//        FloatBuffer dataBuffer = BufferUtils.createFloatBuffer(vertices.length);
//        dataBuffer.put(vertices).flip();

        vaoID = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoID);

//        vboID = GL15.glGenBuffers();
//        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
//        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, dataBuffer, GL15.GL_DYNAMIC_DRAW);
//
//        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);
//
//        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float ticks) {
        if (!(entity instanceof EntityTest entityTest)) {
            return;
        }

        if (entityTest.ssboOUT == 0) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();

        GL11.glTranslated(x + 0.25, y, z + 0.25);


        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);


        GL20.glUseProgram(program);

        FloatBuffer view = BufferUtils.createFloatBuffer(16);
        FloatBuffer projection = BufferUtils.createFloatBuffer(16);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, view);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);

        GL20.glUniformMatrix4(viewID, false, view);
        GL20.glUniformMatrix4(projectionID, false, projection);

        GL30.glBindVertexArray(vaoID);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, entityTest.ssboOUT);
        GL20.glVertexAttribPointer(0, 1, GL11.GL_FLOAT, false, 4, 0);

        GL11.glDrawArrays(GL11.GL_POINTS, 0, 100);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    float hs = 0.125f;
    float[] vertices = {
        // Front face (z = hs)
        -hs, -hs,  hs,  hs, -hs,  hs,  hs,  hs,  hs,  // triangle 1
        hs,  hs,  hs, -hs,  hs,  hs, -hs, -hs,  hs,  // triangle 2

        // Back face (z = -hs)
        -hs, -hs, -hs, -hs,  hs, -hs,  hs,  hs, -hs,  // triangle 3
        hs,  hs, -hs,  hs, -hs, -hs, -hs, -hs, -hs,  // triangle 4

        // Left face (x = -hs)
        -hs, -hs, -hs, -hs, -hs,  hs, -hs,  hs,  hs,  // triangle 5
        -hs,  hs,  hs, -hs,  hs, -hs, -hs, -hs, -hs,  // triangle 6

        // Right face (x = hs)
        hs, -hs, -hs,  hs,  hs, -hs,  hs,  hs,  hs,  // triangle 7
        hs,  hs,  hs,  hs, -hs,  hs,  hs, -hs, -hs,  // triangle 8

        // Top face (y = hs)
        -hs,  hs, -hs, -hs,  hs,  hs,  hs,  hs,  hs,  // triangle 9
        hs,  hs,  hs,  hs,  hs, -hs, -hs,  hs, -hs,  // triangle 10

        // Bottom face (y = -hs)
        -hs, -hs, -hs,  hs, -hs, -hs,  hs, -hs,  hs,  // triangle 11
        hs, -hs,  hs, -hs, -hs,  hs, -hs, -hs, -hs   // triangle 12
    };

}
