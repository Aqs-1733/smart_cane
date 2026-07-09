#include "i2c_bus.h"

#include "driver/i2c_master.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "smartcane_config.h"

static const char *TAG = "I2C";
static i2c_master_bus_handle_t s_bus = NULL;

static esp_err_t add_temp_device(uint8_t address, i2c_master_dev_handle_t *out_dev)
{
    if (s_bus == NULL || out_dev == NULL) {
        return ESP_ERR_INVALID_STATE;
    }

    i2c_device_config_t dev_cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = address,
        .scl_speed_hz = SMARTCANE_I2C_CLOCK_HZ,
    };
    return i2c_master_bus_add_device(s_bus, &dev_cfg, out_dev);
}

esp_err_t i2c_bus_init(void)
{
    if (s_bus != NULL) {
        return ESP_OK;
    }

    i2c_master_bus_config_t bus_cfg = {
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .i2c_port = SMARTCANE_I2C_PORT,
        .scl_io_num = SMARTCANE_I2C_SCL_GPIO,
        .sda_io_num = SMARTCANE_I2C_SDA_GPIO,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };

    esp_err_t ret = i2c_new_master_bus(&bus_cfg, &s_bus);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "i2c_new_master_bus failed: %s", esp_err_to_name(ret));
        return ret;
    }

    ESP_LOGI(TAG, "I2C bus ready sda=%d scl=%d speed=%d",
             SMARTCANE_I2C_SDA_GPIO,
             SMARTCANE_I2C_SCL_GPIO,
             SMARTCANE_I2C_CLOCK_HZ);
    return ESP_OK;
}

bool i2c_bus_is_ready(void)
{
    return s_bus != NULL;
}

bool i2c_bus_probe(uint8_t address)
{
    if (s_bus == NULL) {
        return false;
    }
    return i2c_master_probe(s_bus, address, pdMS_TO_TICKS(50)) == ESP_OK;
}

esp_err_t i2c_bus_write(uint8_t address, const uint8_t *data, size_t len, uint32_t timeout_ms)
{
    i2c_master_dev_handle_t dev = NULL;
    esp_err_t ret = add_temp_device(address, &dev);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = i2c_master_transmit(dev, data, len, pdMS_TO_TICKS(timeout_ms));
    esp_err_t rm_ret = i2c_master_bus_rm_device(dev);
    return ret == ESP_OK ? rm_ret : ret;
}

esp_err_t i2c_bus_read(uint8_t address, uint8_t *data, size_t len, uint32_t timeout_ms)
{
    i2c_master_dev_handle_t dev = NULL;
    esp_err_t ret = add_temp_device(address, &dev);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = i2c_master_receive(dev, data, len, pdMS_TO_TICKS(timeout_ms));
    esp_err_t rm_ret = i2c_master_bus_rm_device(dev);
    return ret == ESP_OK ? rm_ret : ret;
}

esp_err_t i2c_bus_write_read(uint8_t address,
                             const uint8_t *write_data,
                             size_t write_len,
                             uint8_t *read_data,
                             size_t read_len,
                             uint32_t timeout_ms)
{
    i2c_master_dev_handle_t dev = NULL;
    esp_err_t ret = add_temp_device(address, &dev);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = i2c_master_transmit_receive(dev,
                                      write_data,
                                      write_len,
                                      read_data,
                                      read_len,
                                      pdMS_TO_TICKS(timeout_ms));
    esp_err_t rm_ret = i2c_master_bus_rm_device(dev);
    return ret == ESP_OK ? rm_ret : ret;
}

esp_err_t i2c_bus_select_tca_channel(uint8_t channel)
{
    if (channel > 7) {
        return ESP_ERR_INVALID_ARG;
    }

    uint8_t data = (uint8_t)(1U << channel);
    esp_err_t ret = i2c_bus_write(SMARTCANE_TCA9548A_ADDR, &data, 1, 50);
    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "TCA9548A channel %u select failed: %s", channel, esp_err_to_name(ret));
    }
    return ret;
}

void i2c_bus_log_device_status(void)
{
    ESP_LOGI(TAG, "TCA9548A addr=0x%02x %s",
             SMARTCANE_TCA9548A_ADDR,
             i2c_bus_probe(SMARTCANE_TCA9548A_ADDR) ? "found" : "missing");
    ESP_LOGI(TAG, "MPR121 addr=0x%02x %s",
             SMARTCANE_MPR121_ADDR,
             i2c_bus_probe(SMARTCANE_MPR121_ADDR) ? "found" : "missing");
    ESP_LOGI(TAG, "PCA9685 addr=0x%02x %s",
             SMARTCANE_PCA9685_ADDR,
             i2c_bus_probe(SMARTCANE_PCA9685_ADDR) ? "found" : "missing");
}

