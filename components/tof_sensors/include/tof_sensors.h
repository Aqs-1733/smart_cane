#pragma once

#include <stdbool.h>

#include "app_types.h"
#include "esp_err.h"

esp_err_t tof_sensors_init(void);
esp_err_t tof_sensors_read(distance_readings_t *out);
bool tof_sensors_mock_active(void);

