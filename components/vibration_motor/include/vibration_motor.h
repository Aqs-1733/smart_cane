#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "esp_err.h"

esp_err_t vibration_motor_init(void);
void vibration_motor_update(void);
bool vibration_motor_ready(void);
void vibration_motor_left(uint8_t level, uint16_t duration_ms);
void vibration_motor_right(uint8_t level, uint16_t duration_ms);
void vibration_motor_center(uint8_t level, uint16_t duration_ms);
void vibration_motor_all(uint8_t level, uint16_t duration_ms);
void vibration_motor_pattern_obstacle(void);
void vibration_motor_pattern_ground_drop(void);
void vibration_motor_pattern_turn_left(void);
void vibration_motor_pattern_turn_right(void);
void vibration_motor_pattern_stop(void);
void vibration_motor_pattern_sos(void);

