#include <metal_stdlib>
using namespace metal;

// Vertex Shader Structures
struct VertexIn {
    float4 position [[attribute(0)]];
    float2 texCoords [[attribute(1)]];
};

struct VertexOut {
    float4 position [[position]];
    float2 texCoords;
};

struct Uniforms {
    float4x4 stabilizationMatrix;
    float4x4 aspectMatrix;
    float isoBoost;
    int isRedVision;
    int frameCount;
};

// Passthrough Vertex Shader
vertex VertexOut passthrough_vertex(
    VertexIn in [[stage_in]],
    constant Uniforms &uniforms [[buffer(1)]]
) {
    VertexOut out;
    out.position = in.position;

    // Apply stabilization and aspect ratio transform to texture coordinates
    float4 tex = float4(in.texCoords.x, in.texCoords.y, 0.0, 1.0);
    float4 transformed = uniforms.stabilizationMatrix * tex;
    out.texCoords = transformed.xy;

    return out;
}

// Camera Input Fragment Shader (BGRA to Linear + ISO Boost)
fragment float4 camera_input_fragment(
    VertexOut in [[stage_in]],
    texture2d<float> cameraTexture [[texture(0)]],
    sampler textureSampler [[sampler(0)]],
    constant Uniforms &uniforms [[buffer(1)]]
) {
    float4 color = cameraTexture.sample(textureSampler, in.texCoords);
    // Apply digital ISO boost
    color.rgb *= uniforms.isoBoost;
    return color;
}

// Stars Mode: Cumulative Running Mean Kernel
// S_n = (S_{n-1} * (n - 1) + I_n) / n
kernel void stack_average_kernel(
    texture2d<float, access::read> currentTexture [[texture(0)]],
    texture2d<float, access::read> prevAccumulator [[texture(1)]],
    texture2d<float, access::write> nextAccumulator [[texture(2)]],
    constant int &frameCount [[buffer(0)]],
    uint2 gid [[thread_position_in_grid]]
) {
    if (gid.x >= currentTexture.get_width() || gid.y >= currentTexture.get_height()) {
        return;
    }

    float4 cur = currentTexture.read(gid);

    if (frameCount <= 1) {
        nextAccumulator.write(cur, gid);
        return;
    }

    float4 prev = prevAccumulator.read(gid);
    float n = float(frameCount);
    float4 mean = (prev * (n - 1.0) + cur) / n;
    nextAccumulator.write(mean, gid);
}

// Star Trails & Light Trails: Maximum / Lighten Blending Kernel
// S_n = max(S_{n-1}, I_n)
kernel void stack_max_kernel(
    texture2d<float, access::read> currentTexture [[texture(0)]],
    texture2d<float, access::read> prevAccumulator [[texture(1)]],
    texture2d<float, access::write> nextAccumulator [[texture(2)]],
    constant int &frameCount [[buffer(0)]],
    uint2 gid [[thread_position_in_grid]]
) {
    if (gid.x >= currentTexture.get_width() || gid.y >= currentTexture.get_height()) {
        return;
    }

    float4 cur = currentTexture.read(gid);

    if (frameCount <= 1) {
        nextAccumulator.write(cur, gid);
        return;
    }

    float4 prev = prevAccumulator.read(gid);
    float4 blended = max(prev, cur);
    nextAccumulator.write(blended, gid);
}

// Meteor & ISS Mode: Frame Differencing and Motion Blending
kernel void stack_meteor_kernel(
    texture2d<float, access::read> currentTexture [[texture(0)]],
    texture2d<float, access::read> prevFrameTexture [[texture(1)]],
    texture2d<float, access::read> prevAccumulator [[texture(2)]],
    texture2d<float, access::write> nextAccumulator [[texture(3)]],
    constant int &frameCount [[buffer(0)]],
    uint2 gid [[thread_position_in_grid]]
) {
    if (gid.x >= currentTexture.get_width() || gid.y >= currentTexture.get_height()) {
        return;
    }

    float4 cur = currentTexture.read(gid);

    if (frameCount <= 1) {
        nextAccumulator.write(cur, gid);
        return;
    }

    float4 prevFrame = prevFrameTexture.read(gid);
    float4 prevAcc = prevAccumulator.read(gid);

    float4 diff = abs(cur - prevFrame);
    float diffLuma = dot(diff.rgb, float3(0.299, 0.587, 0.114));

    // If significant motion streak is detected, capture and blend
    float4 result;
    if (diffLuma > 0.04) {
        result = max(prevAcc, cur);
    } else {
        result = prevAcc;
    }

    nextAccumulator.write(result, gid);
}

// Display Fragment Shader: Tone Mapping & Astronomy Red Vision Mode
fragment float4 display_fragment(
    VertexOut in [[stage_in]],
    texture2d<float> displayTexture [[texture(0)]],
    sampler textureSampler [[sampler(0)]],
    constant Uniforms &uniforms [[buffer(1)]]
) {
    float4 color = displayTexture.sample(textureSampler, in.texCoords);

    if (uniforms.isRedVision == 1) {
        // Red Vision: preserve dark adaptation with pure deep red tones
        float luma = dot(color.rgb, float3(0.299, 0.587, 0.114));
        return float4(luma * 1.1, 0.0, 0.0, 1.0);
    }

    // Standard natural tone mapping
    return float4(clamp(color.rgb, 0.0, 1.0), 1.0);
}
