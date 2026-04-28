#version 430 core

layout(points) in;
layout(triangle_strip, max_vertices = 36) out;

layout(std430, binding = 0) readonly buffer FloatBuffer {
    float coords[];
} ssbo;

uniform mat4 view;
uniform mat4 projection;

const vec3 cubeVerts[8] = vec3[](
vec3(0,0,0), vec3(0.5,0,0), vec3(0.5,0,0.5), vec3(0,0,0.5),
vec3(0,0.5,0), vec3(0.5,0.5,0), vec3(0.5,0.5,0.5), vec3(0,0.5,0.5)
);

const int indices[36] = int[](
0,1,2, 0,2,3,
4,6,5, 4,7,6,
0,5,1, 0,4,5,
2,7,3, 2,6,7,
0,3,7, 0,7,4,
1,5,6, 1,6,2
);

void main() {
    int base = gl_PrimitiveIDIn * 3;

    vec3 origin = vec3(ssbo.coords[base] / 2, ssbo.coords[base+1] / 2, ssbo.coords[base+2] / 2);

    for (int i = 0; i < 36; i++) {
        vec3 pos = origin + cubeVerts[indices[i]];
        gl_Position = projection * view * vec4(pos, 1.0);
        EmitVertex();
    }
    EndPrimitive();
}
