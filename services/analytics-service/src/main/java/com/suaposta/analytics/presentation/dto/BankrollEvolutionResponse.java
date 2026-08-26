package com.suaposta.analytics.presentation.dto;

import com.suaposta.analytics.application.model.BankrollEvolutionPoint;
import java.util.List;

public record BankrollEvolutionResponse(List<BankrollEvolutionPoint> points) {
}
