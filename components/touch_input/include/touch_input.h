#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "app_types.h"
#include "esp_err.h"

esp_err_t touch_input_init(void);
void touch_input_update(touch_event_callback_t callback, void *ctx);
bool touch_input_mpr121_active(void);
const char *touch_input_event_name(touch_event_type_t type);

