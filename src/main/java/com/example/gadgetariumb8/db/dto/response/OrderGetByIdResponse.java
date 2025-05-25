package com.example.gadgetariumb8.db.dto.response;

import com.example.gadgetariumb8.db.model.enums.Status;
import lombok.*;

import java.math.BigDecimal;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OrderGetByIdResponse{
   private String fullName;
   private String orderNumber;
   private String orderNameInfo;
   private int quantity;
   private BigDecimal price;
   private int discount;
   private int totalDiscount;
   private int totalFinal;
   private String orderStatus;
   private String phoneNumber;
   private String fullAddress;
}

