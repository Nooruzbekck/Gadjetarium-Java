package com.example.gadgetariumb8.db.dto.response;

import lombok.Builder;

@Builder
public record ReviewsResponse(
        Long reviewsId,
        String image,
        String fullName,
        String createdAt,
        int grade,
        String commentary,
        String answer
) {
}
