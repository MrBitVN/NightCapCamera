package com.nightcap.camera.engine

enum class StackingMode(val displayName: String, val description: String) {
    PHOTO(
        displayName = "Thủ công (Photo)",
        description = "Chụp ảnh thủ công tiêu chuẩn với ISO Boost và khóa nét vô cực"
    ),
    STARS(
        displayName = "Chụp sao (Stars)",
        description = "Khử nhiễu sâu và xếp chồng trung bình để làm rõ dải Ngân Hà và các vì sao mờ"
    ),
    STAR_TRAILS(
        displayName = "Vệt sao (Star Trails)",
        description = "Tự động hòa trộn điểm sáng nhất để tạo vệt sao xoay quanh cực trời mà không cháy phông nền"
    ),
    LIGHT_TRAILS(
        displayName = "Vệt sáng (Light Trails)",
        description = "Phơi sáng vô tận cho dòng xe chạy, pháo hoa, vẽ tranh ánh sáng hoặc thác nước mịn"
    ),
    METEORS(
        displayName = "Sao băng & ISS (Meteors)",
        description = "Tự động phát hiện và chộp lấy vệt sáng của sao băng vụt qua hoặc trạm vũ trụ ISS"
    )
}
