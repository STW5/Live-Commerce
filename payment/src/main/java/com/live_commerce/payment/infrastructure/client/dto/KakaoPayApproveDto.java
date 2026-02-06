package com.live_commerce.payment.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoPayApproveDto(
	@JsonProperty("aid") String aid,
	@JsonProperty("tid") String tid,
	@JsonProperty("cid") String cid,
	@JsonProperty("partner_order_id") String partnerOrderId,
	@JsonProperty("partner_user_id") String partnerUserId,
	@JsonProperty("payment_method_type") String paymentMethodType,
	@JsonProperty("item_name") String itemName,
	@JsonProperty("quantity") int quantity,
	@JsonProperty("amount") Amount amount,
	@JsonProperty("created_at") String createdAt,
	@JsonProperty("approved_at") String approvedAt
) {

	public record Amount(
		@JsonProperty("total") int total,
		@JsonProperty("tax_free") int taxFree,
		@JsonProperty("vat") int vat,
		@JsonProperty("point") int point,
		@JsonProperty("discount") int discount
	) {}
}

