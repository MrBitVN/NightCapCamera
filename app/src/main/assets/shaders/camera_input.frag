    #version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform samplerExternalOES uCameraTexture;
uniform float uIsoBoost; // 1.0 = normal, up to 8.0x digital multiplier
uniform float uNoiseThreshold; // Low-end noise floor gate

void main() {
    vec4 color = texture(uCameraTexture, vTexCoord);
    
    // Áp dụng ISO Boost kỹ thuật số
    vec3 boosted = color.rgb * uIsoBoost;
    
    // Khử nhiễu sàn đen (Dark frame / noise floor suppression)
    if (uNoiseThreshold > 0.001) {
        boosted = max(vec3(0.0), boosted - vec3(uNoiseThreshold));
        boosted = boosted / (1.0 - uNoiseThreshold);
    }
    
    // Giới hạn trong khoảng [0.0, 1.0]
    fragColor = vec4(clamp(boosted, 0.0, 1.0), color.a);
}
