#pragma once

#include <stdbool.h>

#include "app_types.h"

bool risk_logic_is_ground_drop(int down_cm);
void risk_logic_calculate(const distance_readings_t *distances,
                          const nearby_risk_summary_t *history,
                          risk_state_t *out);
void risk_logic_log_state(const distance_readings_t *distances,
                          const risk_state_t *risk,
                          const nearby_risk_summary_t *history);

