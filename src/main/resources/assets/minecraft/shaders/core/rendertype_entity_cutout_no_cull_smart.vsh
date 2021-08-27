#version 430

#moj_import <light.glsl>

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in uint PartId;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ProjMat;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

uniform int InstanceOffset; // minecraft doesn't have a way to set uints

struct ModelPart {
    mat4 modelViewMat;
};

layout(std430, binding = 1) buffer modelPartsLayout {
    ModelPart[] modelParts;
} modelPartsSsbo;

struct Model {
    vec4 Color;
    ivec2 UV1;
    ivec2 UV2;
    vec3 padding;
    uint partOffset;
};

layout(std430, binding = 2) buffer modelsLayout {
    Model[] models;
} modelsSsbo;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec4 normal;

void main() {
    #ifdef VULKAN
        Model model = modelsSsbo.models[gl_InstanceIndex];
    #else
        Model model = modelsSsbo.models[InstanceOffset + gl_InstanceID];
    #endif
    ModelPart modelPart = modelPartsSsbo.modelParts[model.partOffset + PartId];

    gl_Position = ProjMat * modelPart.modelViewMat * vec4(Position, 1.0);

    vertexDistance = length((modelPart.modelViewMat * vec4(Position, 1.0)).xyz);
    normal = vec4(mat3(modelPart.modelViewMat) * Normal, 0);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal.xyz, model.Color);
    lightMapColor = texelFetch(Sampler2, model.UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, model.UV1, 0);
    texCoord0 = UV0;
}
