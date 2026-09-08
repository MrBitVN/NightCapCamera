#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uAccumTexture;   // Khung hình tích lũy FBO trước đó
uniform sampler2D uCurrentTexture; // Khung hình mới
uniform float uFrameIndex;         // Số khung hình hiện tại (1.0, 2.0, 3.0...)
uniform int uIsExponential;        // 0: Tích lũy trung bình số học chính xác (Cumulative mean), 1: Trung bình động liên tục (EMA)
uniform float uWeight;             // Trọng số nếu dùng EMA

void main() {
    vec4 accum = texture(uAccumTexture, vTexCoord);
    vec4 current = texture(uCurrentTexture, vTexCoord);
    
    vec3 result;
    if (uFrameIndex <= 1.0) {
        // Frame đầu tiên: lấy trực tiếp giá trị hiện tại
        result = current.rgb;
    } else if (uIsExponential == 1) {
        // Exponential Moving Average: phù hợp phơi sáng liên tục mượt mà
        result = mix(accum.rgb, current.rgb, uWeight);
    } else {
        // Cumulative Running Mean (Chuẩn thiên văn học):
        // result = (accum * (N - 1) + current) / N
        // Triệt tiêu nhiễu cảm biến tỉ lệ thuận theo căn bậc 2 số khung hình
        result = (accum.rgb * (uFrameIndex - 1.0) + current.rgb) / uFrameIndex;
    }
    
    fragColor = vec4(result, 1.0);
}
