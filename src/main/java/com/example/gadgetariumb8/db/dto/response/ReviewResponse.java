package com.example.gadgetariumb8.db.dto.response;

import lombok.*;

import java.util.Date;
import java.util.List;

@NoArgsConstructor
@Builder
@Getter
@Setter
public class ReviewResponse {
    private Long id;
    private String productImg;
    private int productItemNumber;
    private String commentary;
    private int grade;
    private String answer;
    private List<String> images;
    private String userName;
    private String userEmail;
    private String userImg;
    private String date;
    private String productName;

    public ReviewResponse(Long id, String productImg, int productItemNumber, String commentary, int grade, String answer, List<String> images, String userName, String userEmail, String userImg, String date, String productName) {
        this.id = id;
        this.productImg = productImg;
        this.productItemNumber = productItemNumber;
        this.commentary = commentary;
        this.grade = grade;
        this.answer = answer;
        this.images = images;
        this.userName = userName;
        this.userEmail = userEmail;
        this.userImg = userImg;
        this.date = date;
        this.productName = productName;
    }
}
