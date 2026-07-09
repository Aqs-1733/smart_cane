#pragma once

#include <stdint.h>

#include "esp_err.h"

esp_err_t buzzer_init(void);
void buzzer_update(void);
void buzzer_beep(uint16_t ms);
void buzzer_pattern_danger(void);
void buzzer_pattern_sos(void);

