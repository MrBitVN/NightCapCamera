#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uScreenTexture;
uniform int uRedVisionMode; // 1 = Bật chế độ đèn đỏ thiên văn, 0 = Màu bình thường
uniform float uBrightness;  // -0.5 đến 0.5
uniform float uContrast;    // 0.5 đến 2.0

void main() {
    vec4 color = texture(uScreenTexture, vTexCoord);
    vec3 rgb = color.rgb;
    
    // Áp dụng độ sáng & độ tương phản
    rgb = (rgb - 0.5) * uContrast + 0.5 + uBrightness;
    rgb = clamp(rgb, 0.0, 1.0);
    
    // Chế độ Red Vision thiên văn:
    // Chuyển toàn bộ màu sang phổ đỏ để bảo toàn tế bào hình que của mắt khi ngắm sao
    if (uRedVisionMode == 1) {
        float luminance = dot(rgb, vec3(0.299, 0.587, 0.114));
        rgb = vec3(luminance, 0.0, 0.0);
    }
    
    fragColor = vec4(rgb, 1.0);
}
