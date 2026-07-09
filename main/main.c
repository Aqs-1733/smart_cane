#include "app_tasks.h"
#include "board_config.h"
#include "esp_log.h"

static const char *TAG = "APP";

void app_main(void)
{
    ESP_LOGI(TAG, "ESP32-C5 blind assistance system starting...");
    ESP_LOGI(TAG, "Target chip: esp32c5");
    ESP_LOGI(TAG, "Firmware framework: native ESP-IDF");
    app_tasks_start();
}

