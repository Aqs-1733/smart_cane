#include "buttons.h"

#include <stdbool.h>

#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "smartcane_config.h"

static const char *TAG = "BUTTON";

static bool s_last_raw_pressed = false;
static bool s_stable_pressed = false;
static bool s_sos_sent = false;
static int64_t s_last_change_ms = 0;
static int64_t s_press_start_ms = 0;

static int64_t now_ms(void)
{
    return esp_timer_get_time() / 1000;
}

static bool read_pressed_raw(void)
{
    int level = gpio_get_level((gpio_num_t)SMARTCANE_SOS_BUTTON_GPIO);
    return SMARTCANE_SOS_ACTIVE_LOW ? (level == 0) : (level != 0);
}

esp_err_t buttons_init(void)
{
    gpio_config_t cfg = {
        .pin_bit_mask = (1ULL << SMARTCANE_SOS_BUTTON_GPIO),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = SMARTCANE_SOS_ACTIVE_LOW ? GPIO_PULLUP_ENABLE : GPIO_PULLUP_DISABLE,
        .pull_down_en = SMARTCANE_SOS_ACTIVE_LOW ? GPIO_PULLDOWN_DISABLE : GPIO_PULLDOWN_ENABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    esp_err_t ret = gpio_config(&cfg);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "gpio_config failed: %s", esp_err_to_name(ret));
        return ret;
    }

    s_last_raw_pressed = read_pressed_raw();
    s_stable_pressed = s_last_raw_pressed;
    s_last_change_ms = now_ms();
    s_press_start_ms = s_stable_pressed ? s_last_change_ms : 0;
    s_sos_sent = false;
    ESP_LOGI(TAG, "SOS button ready gpio=%d hold=%dms",
             SMARTCANE_SOS_BUTTON_GPIO,
             SMARTCANE_SOS_HOLD_MS);
    return ESP_OK;
}

void buttons_update(sos_callback_t callback, void *ctx)
{
    int64_t now = now_ms();
    bool raw_pressed = read_pressed_raw();

    if (raw_pressed != s_last_raw_pressed) {
        s_last_raw_pressed = raw_pressed;
        s_last_change_ms = now;
    }

    if (now - s_last_change_ms >= SMARTCANE_BUTTON_DEBOUNCE_MS &&
        raw_pressed != s_stable_pressed) {
        s_stable_pressed = raw_pressed;
        if (s_stable_pressed) {
            s_press_start_ms = now;
            s_sos_sent = false;
            ESP_LOGI(TAG, "SOS button pressed");
        } else {
            s_press_start_ms = 0;
            s_sos_sent = false;
            ESP_LOGI(TAG, "SOS button released");
        }
    }

    if (s_stable_pressed && !s_sos_sent && s_press_start_ms > 0 &&
        now - s_press_start_ms >= SMARTCANE_SOS_HOLD_MS) {
        s_sos_sent = true;
        if (callback != NULL) {
            callback(ctx);
        }
    }
}

