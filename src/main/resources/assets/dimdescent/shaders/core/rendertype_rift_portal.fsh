#version 150

#moj_import <matrix.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float GameTime;
uniform int EndPortalLayers;

in vec4 texProj0;

// Same technique as vanilla's end portal shader (rendertype_end_portal.fsh), just
// re-tinted from the vanilla teal/blue/purple palette to a warm red/orange/magenta one.
const vec3[] COLORS = vec3[](
    vec3(0.110818, 0.022087, 0.030000),
    vec3(0.089485, 0.011892, 0.026000),
    vec3(0.100326, 0.027636, 0.035000),
    vec3(0.114838, 0.046564, 0.040000),
    vec3(0.117696, 0.064901, 0.028000),
    vec3(0.123646, 0.063761, 0.045000),
    vec3(0.166380, 0.084817, 0.060000),
    vec3(0.154120, 0.097489, 0.040000),
    vec3(0.195191, 0.106152, 0.070000),
    vec3(0.187229, 0.097721, 0.080000),
    vec3(0.148582, 0.133516, 0.090000),
    vec3(0.243332, 0.070006, 0.090000),
    vec3(0.214696, 0.080000, 0.196766),
    vec3(0.321970, 0.100000, 0.080000),
    vec3(0.390010, 0.150000, 0.080000),
    vec3(0.661491, 0.120000, 0.140000)
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 end_portal_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );

    mat2 rotate = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));

    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);

    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

out vec4 fragColor;

void main() {
    vec3 color = textureProj(Sampler0, texProj0).rgb * COLORS[0];
    for (int i = 0; i < EndPortalLayers; i++) {
        color += textureProj(Sampler1, texProj0 * end_portal_layer(float(i + 1))).rgb * COLORS[i];
    }
    fragColor = vec4(color, 1.0);
}
