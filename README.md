# NightCap Camera cho Android (Astrophotography & Long Exposure)

Ứng dụng chụp ảnh thiên văn, phơi sáng dài và vệt sao thời gian thực dành cho Android, phát triển dựa trên nguyên lý kiến trúc của **NightCap Camera** và **AstroShader**.

---

## 🌟 Tính năng nổi bật

1. **Chế độ Chụp sao (Stars Mode)**:
   - Áp dụng thuật toán **Cumulative Running Mean Stacking** trên GPU OpenGL ES 3.0:
     $$S_n = \frac{S_{n-1} \cdot (n - 1) + I_n}{n}$$
   - Triệt tiêu nhiễu ngẫu nhiên (sensor read noise) theo hệ số $\sqrt{n}$, làm rõ dải Ngân Hà (Milky Way) và các cụm sao mờ mà không làm cháy các vùng sáng khác.

2. **Chế độ Vệt sao (Star Trails Mode)**:
   - Thuật toán **Maximum / Lighten Blending**:
     $$S_n = \max(S_{n-1}, I_n)$$
   - Tích lũy các điểm sáng nhất qua từng khung hình để ghi lại chuyển động tự quay của Trái Đất tạo thành các vệt sao tròn quanh cực trời mà không làm cháy sáng nền trời đêm.

3. **Chế độ Vệt sáng (Light Trails Mode)**:
   - Phơi sáng vô hạn cho dòng xe cộ, pháo hoa, thác nước mềm mịn và vẽ tranh bằng ánh sáng (Light Painting) với hệ số decay tùy chỉnh.

4. **Chế độ Sao băng & Trạm vũ trụ ISS (Meteors Mode)**:
   - Thuật toán phân tích vi sai giữa các khung hình liên tiếp ($\Delta = |I_n - I_{n-1}|$) để bắt trọn những vệt sáng lướt qua với vận tốc cao.

5. **Chống rung cầm tay (Handheld Gyroscope Stabilization)**:
   - Đọc dữ liệu cảm biến con quay hồi chuyển (`Sensor.TYPE_ROTATION_VECTOR` và `TYPE_GYROSCOPE`) ở tần số cao.
   - Tính toán ma trận biến đổi Affine/Homography bù trừ góc nghiêng Yaw/Pitch/Roll, căn chỉnh khung hình trước khi đưa vào shader hòa trộn. Cho phép phơi sáng cầm tay vài giây mà không bị nhòe.

6. **ISO Boost kỹ thuật số**:
   - Khuếch đại gain tín hiệu số (1x, 2x, 4x, 8x) vượt qua ngưỡng ISO phần cứng tối đa của cảm biến điện thoại, kết hợp thuật toán chặn sàn nhiễu (Noise floor gating).

7. **Điều khiển cử chỉ trực quan (Gesture HUD)**:
   - **Vuốt dọc**: Tăng/giảm ISO và kích hoạt ISO Boost.
   - **Vuốt ngang**: Thay đổi tốc độ màn trập (thời gian phơi sáng).
   - **Khóa nét vô cực (∞ Focus)**: Khóa tiêu cự ở khoảng cách vô tận (`LENS_FOCUS_DISTANCE = 0.0f`).

8. **Chế độ Đèn đỏ Thiên văn (Astronomy Red Vision Mode)**:
   - Chuyển toàn bộ giao diện và khung ngắm sang phổ màu đỏ đậm để bảo vệ tế bào que trong mắt, giữ nguyên khả năng thích nghi bóng tối của người ngắm sao ban đêm.

---

## 🏗 Kiến trúc mã nguồn

```
nightcapcamera/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/shaders/
│   │   │   ├── passthrough.vert          # Vertex shader với ma trận biến đổi
│   │   │   ├── camera_input.frag         # OES texture to 2D + ISO Boost
│   │   │   ├── stack_max.frag            # Star Trails & Light Trails (Max blending)
│   │   │   ├── stack_average.frag        # Stars Mode (Cumulative Running Mean)
│   │   │   ├── stack_meteor.frag         # Meteor & ISS detection
│   │   │   └── display.frag              # Tone mapping & Red Vision mode
│   │   ├── java/com/nightcap/camera/
│   │   │   ├── MainActivity.kt           # Fullscreen Immersive & Permission
│   │   │   ├── engine/
│   │   │   │   ├── Camera2Manager.kt     # Manual sensor control (ISO, Shutter, Focus)
│   │   │   │   ├── GyroStabilizer.kt     # Chống rung IMU Handheld Stabilization
│   │   │   │   └── StackingMode.kt       # Enum các chế độ chụp
│   │   │   ├── gl/
│   │   │   │   ├── GLShaderUtils.kt      # Compile/Link shader và quản lý Buffer
│   │   │   │   ├── TextureAccumulator.kt # Ping-Pong FBO FP16 double buffering
│   │   │   │   └── GLTextureRenderer.kt  # GPU Rendering pipeline & Stacking
│   │   │   ├── ui/
│   │   │   │   ├── CameraScreen.kt       # Giao diện chính Jetpack Compose
│   │   │   │   ├── NightModeTheme.kt     # Bảng màu OLED & Red Vision
│   │   │   │   └── components/
│   │   │   │       ├── GestureOverlay.kt # Điều khiển vuốt ISO / Shutter
│   │   │   │       ├── StackingHUD.kt    # Đếm giờ, số frame, thông số
│   │   │   │       └── ManualControlBar.kt # Nút Shutter, chọn Mode, Boost
│   │   │   └── util/
│   │   │       └── ImageSaver.kt         # Lưu ảnh chất lượng cao vào MediaStore
│   │   └── res/                          # Vector icons, colors, themes
├── gradle/
│   └── libs.versions.toml                # Quản lý phiên bản dependencies
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew / gradlew.bat
```

---

## 🚀 Hướng dẫn mở dự án & Đẩy lên GitLab

### 1. Mở dự án trong Android Studio
- Khởi động **Android Studio (Hedgehog / Iguana / Jellyfish hoặc mới hơn)**.
- Chọn **File -> Open...** và trỏ đến thư mục `c:\work\nightcapcamera`.
- Android Studio sẽ tự động đồng bộ Gradle và tải các thư viện cần thiết.

### 2. Kết nối và Đẩy code lên GitLab
Chạy các lệnh sau trong terminal để đẩy toàn bộ mã nguồn lên repository GitLab của bạn:

```bash
git init
git add .
git commit -m "feat: Initial commit for NightCap Camera Android app with GPU Frame Stacking & Gyro Stabilization"
git branch -M main
git remote add origin https://gitlab.com/sondeptrai/nightcap_android.git
git push -u origin main
```
*(Nếu GitLab yêu cầu xác thực, hãy nhập tài khoản và Personal Access Token với quyền `write_repository`)*.
