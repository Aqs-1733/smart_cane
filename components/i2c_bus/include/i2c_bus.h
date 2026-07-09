#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

esp_err_t i2c_bus_init(void);
bool i2c_bus_is_ready(void);
bool i2c_bus_probe(uint8_t address);
esp_err_t i2c_bus_write(uint8_t address, const uint8_t *data, size_t len, uint32_t timeout_ms);
esp_err_t i2c_bus_read(uint8_t address, uint8_t *data, size_t len, uint32_t timeout_ms);
esp_err_t i2c_bus_write_read(uint8_t address,
                             const uint8_t *write_data,
                             size_t write_len,
                             uint8_t *read_data,
                             size_t read_len,
                             uint32_t timeout_ms);
esp_err_t i2c_bus_select_tca_channel(uint8_t channel);
void i2c_bus_log_device_status(void);

