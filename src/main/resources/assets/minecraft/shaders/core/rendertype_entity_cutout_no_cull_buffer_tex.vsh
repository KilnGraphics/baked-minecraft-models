#version 330 core

#moj_import <light.glsl>

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in int PartId;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ProjMat;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

uniform int InstanceOffset; // minecraft doesn't have a way to set uints

uniform samplerBuffer ModelBuffer;
uniform samplerBuffer PartBuffer;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec4 normal;

void main() {
    #ifdef VULKAN
    int modelLocation = gl_InstanceIndex * 48;
    #else
    int modelLocation = (InstanceOffset + gl_InstanceID) * 48;
    #endif
    vec4 modelColor = texelFetch(ModelBuffer, modelLocation);
    ivec4 convertedUVs = floatBitsToInt(texelFetch(ModelBuffer, modelLocation + 16));
    ivec2 modelUV1 = convertedUVs.rg;
    ivec2 modelUV2 = convertedUVs.ba;
    int modelPartOffset = floatBitsToInt(texelFetch(ModelBuffer, modelLocation + 32).a);

    int partLocation = (modelPartOffset + PartId) * 64;
    mat4 modelViewMat = mat4(texelFetch(PartBuffer, partLocation), texelFetch(PartBuffer, partLocation + 16), texelFetch(PartBuffer, partLocation + 32), texelFetch(PartBuffer, partLocation + 48));

    gl_Position = ProjMat * modelViewMat * vec4(Position, 1.0);

    vertexDistance = length((modelViewMat * vec4(Position, 1.0)).xyz);
    normal = vec4(mat3(modelViewMat) * Normal, 0);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal.xyz, modelColor);
    lightMapColor = texelFetch(Sampler2, modelUV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, modelUV1, 0);
    texCoord0 = UV0;
}
