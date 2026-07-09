#include "buzzer.h"

#include <stdbool.h>

#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "smartcane_config.h"

static const char *TAG = "BUZZER";

typedef enum {
    BUZZER_IDLE = 0,
    BUZZER_SINGLE,
    BUZZER_DANGER,
    BUZZER_SOS,
} buzzer_mode_t;

static buzzer_mode_t s_mode = BUZZER_IDLE;
static int64_t s_next_step_ms = 0;
static uint8_t s_pattern_index = 0;

static const uint16_t s_danger_durations[] = {120, 90, 120};
static const bool s_danger_levels[] = {true, false, true};
static const uint16_t s_sos_durations[] = {180, 100, 180, 100, 180, 180, 260};
static const bool s_sos_levels[] = {true, false, true, false, true, false, true};

static int64_t now_ms(void)
{
    return esp_timer_get_time() / 1000;
}

static void write_buzzer(bool on)
{
    int level = SMARTCANE_BUZZER_ACTIVE_HIGH ? (on ? 1 : 0) : (on ? 0 : 1);
    gpio_set_level((gpio_num_t)SMARTCANE_BUZZER_GPIO, level);
}

static void start_pattern(buzzer_mode_t mode)
{
    s_mode = mode;
    s_pattern_index = 0;
    s_next_step_ms = 0;
}

esp_err_t buzzer_init(void)
{
    gpio_config_t cfg = {
        .pin_bit_mask = (1ULL << SMARTCANE_BUZZER_GPIO),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    esp_err_t ret = gpio_config(&cfg);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "gpio_config failed: %s", esp_err_to_name(ret));
        return ret;
    }
    write_buzzer(false);
    ESP_LOGI(TAG, "buzzer ready gpio=%d", SMARTCANE_BUZZER_GPIO);
    return ESP_OK;
}

void buzzer_update(void)
{
    if (s_mode == BUZZER_IDLE) {
        return;
    }

    int64_t now = now_ms();
    if (s_next_step_ms != 0 && now < s_next_step_ms) {
        return;
    }

    if (s_mode == BUZZER_SINGLE) {
        write_buzzer(false);
        s_mode = BUZZER_IDLE;
        return;
    }

    const uint16_t *durations = NULL;
    const bool *levels = NULL;
    uint8_t len = 0;
    if (s_mode == BUZZER_DANGER) {
        durations = s_danger_durations;
        levels = s_danger_levels;
        len = sizeof(s_danger_durations) / sizeof(s_danger_durations[0]);
    } else if (s_mode == BUZZER_SOS) {
        durations = s_sos_durations;
        levels = s_sos_levels;
        len = sizeof(s_sos_durations) / sizeof(s_sos_durations[0]);
    }

    if (s_pattern_index >= len) {
        write_buzzer(false);
        s_mode = BUZZER_IDLE;
        return;
    }

    write_buzzer(levels[s_pattern_index]);
    s_next_step_ms = now + durations[s_pattern_index];
    s_pattern_index++;
}

void buzzer_beep(uint16_t ms)
{
    if (ms > 300) {
        ms = 300;
    }
    s_mode = BUZZER_SINGLE;
    s_next_step_ms = now_ms() + ms;
    s_pattern_index = 0;
    write_buzzer(true);
}

void buzzer_pattern_danger(void)
{
    start_pattern(BUZZER_DANGER);
}

void buzzer_pattern_sos(void)
{
    start_pattern(BUZZER_SOS);
}

