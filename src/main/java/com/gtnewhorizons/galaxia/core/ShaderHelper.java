package com.gtnewhorizons.galaxia.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.IntBuffer;

public class ShaderHelper {
    private static final Logger LOG = LogManager.getLogger(ShaderHelper.class);

    public static int createProgram(String vert, String frag) {
        int program = GL20.glCreateProgram();

        int vertShader = createShader(GL20.GL_VERTEX_SHADER, vert);
        int fragShader = createShader(GL20.GL_FRAGMENT_SHADER, frag);

        GL20.glAttachShader(program, vertShader);
        GL20.glAttachShader(program, fragShader);

        GL20.glLinkProgram(program);


        // maybe good practice?
        GL20.glDetachShader(program, vertShader);
        GL20.glDetachShader(program, fragShader);
        GL20.glDeleteShader(vertShader);
        GL20.glDeleteShader(fragShader);


        GL20.glValidateProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            // return 0 to run fallback instead
            return 0;
        }

        return program;
    }

    private static int createShader(int type, String path) {
        // creating shader location
        int shader = GL20.glCreateShader(type);

        // reading the shader file

        String shaderSource = "";

        try (InputStream stream = ShaderHelper.class.getResourceAsStream(path)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            String line;
            StringBuilder source = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                source.append(line).append("\n");
            }

            shaderSource = String.valueOf(source);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        // applying the shader source string
        GL20.glShaderSource(shader, shaderSource);

        // compiling shader from source
        GL20.glCompileShader(shader);

        return shader;
    }
}
