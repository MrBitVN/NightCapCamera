#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uAccumTexture;   // Khung hình tích lũy FBO trước đó
uniform sampler2D uCurrentTexture; // Khung hình mới vừa chụp
uniform float uDecayFactor;        // 1.0 = không suy giảm (vệt vĩnh cửu), <1.0 = vệt tàn dần

void main() {
    vec4 accum = texture(uAccumTexture, vTexCoord);
    vec4 current = texture(uCurrentTexture, vTexCoord);
    
    // Áp dụng hệ số suy giảm nhẹ nếu muốn vệt sao/đèn xe có đuôi tàn (Trail fade)
    vec3 decayedAccum = accum.rgb * uDecayFactor;
    
    // Thuật toán Lighten / Maximum Blending:
    // Giữ lại điểm ảnh có độ sáng cao nhất giữa khung hình tích lũy và khung hình hiện tại
    vec3 blended = max(decayedAccum, current.rgb);
    
    fragColor = vec4(blended, 1.0);
}
