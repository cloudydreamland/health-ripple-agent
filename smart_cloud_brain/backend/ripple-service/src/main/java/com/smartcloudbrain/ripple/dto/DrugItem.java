package com.smartcloudbrain.ripple.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DrugItem(String drugName) {
}
