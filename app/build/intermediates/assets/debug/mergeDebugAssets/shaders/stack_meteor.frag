#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uAccumTexture;   // Khung hình tích lũy FBO
uniform sampler2D uCurrentTexture; // Khung hình mới
uniform sampler2D uPrevTexture;    // Khung hình ngay trước đó
uniform float uSensitivity;        // Ngưỡng phát hiện vệt sáng (ví dụ: 0.15)

void main() {
    vec4 accum = texture(uAccumTexture, vTexCoord);
    vec4 current = texture(uCurrentTexture, vTexCoord);
    vec4 prev = texture(uPrevTexture, vTexCoord);
    
    // Tính độ sáng chênh lệch giữa frame hiện tại và frame trước
    float currentLum = dot(current.rgb, vec3(0.299, 0.587, 0.114));
    float prevLum = dot(prev.rgb, vec3(0.299, 0.587, 0.114));
    float diff = currentLum - prevLum;
    
    // Nếu có vệt sáng bất thường xuất hiện đột ngột vượt ngưỡng
    if (diff > uSensitivity) {
        // Tích hợp điểm sáng của vệt sao băng/vệ tinh vào ảnh tích lũy
        fragColor = vec4(max(accum.rgb, current.rgb), 1.0);
    } else {
        // Nếu không có sao băng, giữ nguyên ảnh nền tích lũy
        fragColor = accum;
    }
}
