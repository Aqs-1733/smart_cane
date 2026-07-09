#pragma once

#include "app_types.h"
#include "esp_err.h"

esp_err_t buttons_init(void);
void buttons_update(sos_callback_t callback, void *ctx);

